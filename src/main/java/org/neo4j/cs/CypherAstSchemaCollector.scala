package org.neo4j.cs

import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.expressions.{BooleanLiteral, DecimalDoubleLiteral, DoubleLiteral, Expression, FunctionInvocation, IntegerLiteral, LabelName, ListLiteral, LogicalVariable, MapExpression, NodePattern, Property, RelTypeName, RelationshipChain, RelationshipPattern, SemanticDirection, SignedDecimalIntegerLiteral, StringLiteral, Variable}
import org.neo4j.cypher.internal.util.Foldable
import org.neo4j.cypher.internal.label_expressions.LabelExpression
import org.neo4j.cypher.internal.label_expressions.LabelExpression._
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypherdsl.core.ListExpression

import java.util.{List => JList, Set => JSet}
import scala.jdk.CollectionConverters._
import org.neo4j.cypher.internal.expressions._



object CypherAstSchemaCollector {

  sealed trait PropertyOwnerKind
  case object NodeOwner extends PropertyOwnerKind
  case object RelOwner  extends PropertyOwnerKind

  sealed trait PropertyType
  case object StringType   extends PropertyType
  case object IntegerType  extends PropertyType
  case object FloatType    extends PropertyType
  case object BooleanType  extends PropertyType
  case object DateType     extends PropertyType
  case object LocalTimeType extends PropertyType
  case object ZonedTimeType extends PropertyType
  case object LocalDateTimeType extends PropertyType
  case object ZonedDateTimeType extends PropertyType
  case object DurationType extends PropertyType
  case object PointType    extends PropertyType
  case object ListType     extends PropertyType
  case object UnknownType extends PropertyType

  /** One “edge” in the pattern */
  final case class RelationshipDescriptor(
                                           relType: String,
                                           sourceLabels: Set[String],
                                           targetLabels: Set[String],
                                           undirectedLabels: Set[String]
                                         )
  /** Java-friendly DTO */
  final case class RelationshipDescriptorDTO(
                                              relType: String,
                                              sourceLabels: java.util.Set[String],
                                              targetLabels: java.util.Set[String],
                                              undirectedLabels: java.util.Set[String]
                                            )
  final case class PropertyDescriptor(
                                       ownerKind: PropertyOwnerKind,   // NodeOwner or RelOwner
                                       ownerName: String,              // label or relType
                                       propertyKey: String,
                                       propertyType: Option[PropertyType]
                                     )
  // Java-friendly DTO
  final case class PropertyDescriptorDTO(
                                          ownerIsNode: Boolean,
                                          ownerName: String,
                                          propertyKey: String,
                                          propertyType: String // e.g. "STRING", "NUMBER", ...
                                        )

  final case class Result(labels: Set[String], relTypes: Set[String], properties: Set[String])
  final case class PropRef(varName: String, key: String)
  final case class Env(
                        // from your pattern pass
                        nodeVars: Map[String, Set[String]],
                        relVars:  Map[String, Set[String]],

                        // inference results (keep a *set* to tolerate ambiguity)
                        propTypes: Map[PropRef, Set[PropertyType]] = Map.empty
                      ) {
    def addType(p: PropRef, t: PropertyType): Env =
      copy(propTypes = propTypes.updated(p, propTypes.getOrElse(p, Set.empty) + t))
  }
  final case class Acc(
                        nodeVars: Map[String, Set[String]],        // var name -> node labels
                        relVars:  Map[String, Set[String]],        // var name -> rel types
                        properties: Vector[PropertyDescriptor]
                      )

  private val emptyAcc = Acc(Map.empty, Map.empty, Vector.empty)

  def collect(root: ASTNode): Result = {
    root.folder.treeFold(Result(Set.empty, Set.empty, Set.empty)) {

      case LabelName(name) =>
        acc => Foldable.TraverseChildren(acc.copy(labels = acc.labels + name))

      case RelTypeName(name) =>
        acc => Foldable.TraverseChildren(acc.copy(relTypes = acc.relTypes + name))

      case Property(expr, key) =>
        //need to also get the property owner entity
        acc => Foldable.TraverseChildren(acc.copy(properties = acc.properties + key.name))
    }
  }
  // ----- Public API you call from Java -----
  def collectLabels(root: ASTNode): JSet[String] =
    collect(root).labels.asJava

  def collectRelTypes(root: ASTNode): JSet[String] =
    collect(root).relTypes.asJava

  /**
   * Collect properties (labels/rel-types -> property keys) and perform a best-effort type inference
   */
  def collectProperties(root: ASTNode): Seq[PropertyDescriptor] = {
    // First pass: collect node/rel variable maps and inline map-properties
    val acc: Acc = root.folder.treeFold(emptyAcc) {

      case node: NodePattern =>
        acc => {
          val labels = nodeLabels(node)

          // variable -> labels
          val newNodeVars = node.variable match {
            case Some(v: LogicalVariable) =>
              val name = v.name
              val existing = acc.nodeVars.getOrElse(name, Set.empty)
              acc.nodeVars.updated(name, existing ++ labels)
            case _ =>
              acc.nodeVars
          }

          // inline properties: (n:Label { foo: 1, bar: 'x' })
          val inlineProps: Seq[PropertyDescriptor] =
            node.properties.toSeq
              .flatMap(extractMapProperties)
              .flatMap { case (key, pType) =>
                labels.toSeq.map { label =>
                  PropertyDescriptor(NodeOwner, label, key, pType)
                }
              }

          TraverseChildren(acc.copy(
            nodeVars   = newNodeVars,
            properties = acc.properties ++ inlineProps
          ))
        }

      case relPat: RelationshipPattern =>
        acc => {
          val types = relationshipTypes(relPat)

          // variable -> rel types
          val newRelVars = relPat.variable match {
            case Some(v: LogicalVariable) =>
              val name = v.name
              val existing = acc.relVars.getOrElse(name, Set.empty)
              acc.relVars.updated(name, existing ++ types)
            case _ =>
              acc.relVars
          }

          // inline properties: -[r:TYPE { foo: true }]->
          val inlineProps: Seq[PropertyDescriptor] =
            relPat.properties.toSeq
              .flatMap(extractMapProperties)
              .flatMap { case (key, pType) =>
                types.toSeq.map { tname =>
                  PropertyDescriptor(RelOwner, tname, key, pType)
                }
              }

          TraverseChildren(acc.copy(
            relVars    = newRelVars,
            properties = acc.properties ++ inlineProps
          ))
        }

      // --- PROPERTY ACCESSES: n.foo, r.bar, used in WHERE, SET, RETURN, etc. ---

      case Property(Variable(varName), key) =>
        acc => {
          val nodeLabels = acc.nodeVars.getOrElse(varName, Set.empty)
          val relTypes   = acc.relVars.getOrElse(varName, Set.empty)

          val nodeProps =
            nodeLabels.toSeq.map { label =>
              PropertyDescriptor(NodeOwner, label, key.name, None)
            }

          val relProps =
            relTypes.toSeq.map { tname =>
              PropertyDescriptor(RelOwner, tname, key.name, None)
            }

          // If varName not known in either map, we skip it.
          val allProps = nodeProps ++ relProps

          TraverseChildren(
            acc.copy(properties = acc.properties ++ allProps)
          )
        }

    }

    // Second pass: collect type constraints based on expressions, WITH/RETURN/WHERE etc.
    val initialEnv = Env(acc.nodeVars, acc.relVars, Map.empty)
    val env = collectTypeEnv(root, initialEnv)

    // Build property descriptors from inferred prop types (env) and inline props (acc.properties)
    val derivedProps: Seq[PropertyDescriptor] = env.propTypes.toSeq.flatMap { case (PropRef(varName, key), types) =>
      val nodeLabels = acc.nodeVars.getOrElse(varName, Set.empty)
      val relTypes = acc.relVars.getOrElse(varName, Set.empty)

      val chosenType: Option[PropertyType] =
        if (types.isEmpty) None
        else if (types.size == 1) Some(types.head)
        else Some(UnknownType)

      val nodeProps = nodeLabels.toSeq.map { label =>
        PropertyDescriptor(NodeOwner, label, key, chosenType)
      }
      val relProps = relTypes.toSeq.map { rt =>
        PropertyDescriptor(RelOwner, rt, key, chosenType)
      }

      nodeProps ++ relProps
    }

    // Merge inline properties (which already may carry types) with derived ones.
    // Use a simple union: prefer inline property's explicit type when present.
    val all = (acc.properties ++ derivedProps).groupBy(p => (p.ownerKind, p.ownerName, p.propertyKey)).map {
      case (_, seq) =>
        // prefer an explicitly typed descriptor
        seq.find(_.propertyType.isDefined).getOrElse(seq.head)
    }.toSeq

    all
  }

  def collectRelationships(root: ASTNode): Seq[RelationshipDescriptor] = {
    root.folder.treeFold(Seq.empty[RelationshipDescriptor]) {

      case chain: RelationshipChain =>
        acc => {
          val leftNode: NodePattern  = chain.leftNode
          val rightNode: NodePattern  = chain.rightNode
          val relPat: RelationshipPattern = chain.relationship
          val dir: SemanticDirection = relPat.direction

          val leftLabels = nodeLabels(leftNode)
          val rightLabels = nodeLabels(rightNode)

          val (srcLabels, tgtLabels, undirLabels) =
            dir match {
              case SemanticDirection.OUTGOING =>
                (leftLabels, rightLabels, Set.empty[String])
              case SemanticDirection.INCOMING =>
                (rightLabels, leftLabels, Set.empty[String])
              case SemanticDirection.BOTH =>
                (Set.empty[String], Set.empty[String], leftLabels ++ rightLabels)

            }

          val relTypes  = relationshipTypes(relPat)

          // For each relationship type, create one descriptor
          val descriptors =
            for (t <- relTypes.toSeq)
              yield RelationshipDescriptor(t, srcLabels, tgtLabels, undirLabels)

          TraverseChildren(acc ++ descriptors)
        }
    }
  }

  def collectRelationshipsDTO(root: ASTNode): JList[RelationshipDescriptorDTO] =
    collectRelationships(root)
      .map { d =>
        RelationshipDescriptorDTO(
          d.relType,
          d.sourceLabels.asJava,
          d.targetLabels.asJava,
          d.undirectedLabels.asJava
        )
      }
      .asJava
  // Java entry point
  def collectPropertiesDTO(root: ASTNode): JList[PropertyDescriptorDTO] =
    collectProperties(root).map(toDTO).asJava

  /** Extract labels from a NodePattern’s labelExpression */
  private def nodeLabels(node: NodePattern): Set[String] =
    node.labelExpression.map(extractNamesFromLabelExpression).getOrElse(Set.empty)

  /** Extract relationship types from a RelationshipPattern’s labelExpression */
  private def relationshipTypes(rel: RelationshipPattern): Set[String] =
    rel.labelExpression.map(extractNamesFromLabelExpression).getOrElse(Set.empty)

  /** Extract (key, propertyType) from a static map expression, if present. */
  private def extractMapProperties(expr: Expression): Seq[(String, Option[PropertyType])] = expr match {
    case MapExpression(items) =>
      items.toSeq.map { case (keyName, valueExpr) =>
        (keyName.name, inferType(valueExpr))
      }

    // If you want, add more cases for other map syntaxes; otherwise:
    case _ =>
      Seq.empty
  }


  /**
   * Walk expressions and collect type constraints for property references.
   * This is a conservative, best-effort pass focused on the common cases.
   */
  private def collectTypeEnv(root: ASTNode, initial: Env): Env = {
    root.folder.treeFold(initial) {

      // equality / comparison: propagate literal types to the opposite side
      case Equals(lhs, rhs) =>
        env => {
          val env1 = (literalType(lhs), propRef(rhs)) match {
            case (Some(t), Some(p)) => env.addType(p, t)
            case _ => env
          }
          val env2 = (literalType(rhs), propRef(lhs)) match {
            case (Some(t), Some(p)) => env1.addType(p, t)
            case _ => env1
          }
          Foldable.TraverseChildren(env2)
        }

      // string predicates
      case Contains(lhs, rhs) =>
        env =>
          val e1 = propRef(lhs).map(p => env.addType(p, StringType)).getOrElse(env)
          val e2 = propRef(rhs).map(p => e1.addType(p, StringType)).getOrElse(e1)
          Foldable.TraverseChildren(e2)

      case StartsWith(lhs, rhs) =>
        env =>
          val e1 = propRef(lhs).map(p => env.addType(p, StringType)).getOrElse(env)
          val e2 = propRef(rhs).map(p => e1.addType(p, StringType)).getOrElse(e1)
          Foldable.TraverseChildren(e2)

      case EndsWith(lhs, rhs) =>
        env =>
          val e1 = propRef(lhs).map(p => env.addType(p, StringType)).getOrElse(env)
          val e2 = propRef(rhs).map(p => e1.addType(p, StringType)).getOrElse(e1)
          Foldable.TraverseChildren(e2)

      // IN: if right is list literal, propagate element type to lhs
      case In(lhs, rhs) =>
        env =>
          val env1 = rhs match {
            case list: ListLiteral =>
              val elemTypes = list.expressions.flatMap(literalType).toSet
              propRef(lhs).map { p =>
                elemTypes.foldLeft(env)((e, t) => e.addType(p, t))
              }.getOrElse(env)
            case _ => env
          }
          Foldable.TraverseChildren(env1)

      // boolean ops: require boolean operands
      case And(lhs, rhs) =>
        env =>
          val e1 = propRef(lhs).map(p => env.addType(p, BooleanType)).getOrElse(env)
          val e2 = propRef(rhs).map(p => e1.addType(p, BooleanType)).getOrElse(e1)
          Foldable.TraverseChildren(e2)

      case Or(lhs, rhs) =>
        env =>
          val e1 = propRef(lhs).map(p => env.addType(p, BooleanType)).getOrElse(env)
          val e2 = propRef(rhs).map(p => e1.addType(p, BooleanType)).getOrElse(e1)
          Foldable.TraverseChildren(e2)

      case Not(inner) =>
        env => Foldable.TraverseChildren(propRef(inner).map(p => env.addType(p, BooleanType)).getOrElse(env))

      // function calls: constrain argument types for known functions
      case f: FunctionInvocation =>
        env => Foldable.TraverseChildren(applyFunctionConstraints(env, f))

      // default: traverse children
    }
  }

  // Helper: apply typing constraints for known functions (conservative)
  private def applyFunctionConstraints(env: Env, f: FunctionInvocation): Env = {
    val name = f.functionName.name.toLowerCase(java.util.Locale.ROOT)

    name match {
      case "toupper" | "tolower" | "touppercase" | "tolowercase" | "toUpper" | "toLower" =>
        f.arguments.lift(0).flatMap(propRef) match {
          case Some(p) => env.addType(p, StringType)
          case None => env
        }

      case "date" =>
        // date(...) returns DATE; argument might be string/params — we conservatively do not force arg type
        env

      case "tointeger" | "toInt" =>
        f.arguments.lift(0).flatMap(propRef) match {
          case Some(p) => env.addType(p, IntegerType)
          case None => env
        }

      case "tofloat" | "toDouble" =>
        f.arguments.lift(0).flatMap(propRef) match {
          case Some(p) => env.addType(p, FloatType)
          case None => env
        }

      case _ => env
    }
  }

  // Extract property reference if expression is a simple Variable.property
  private def propRef(e: Expression): Option[PropRef] = e match {
    case Property(Variable(v), key) => Some(PropRef(v, key.name))
    case _ => None
  }

  private def literalType(e: Expression): Option[PropertyType] = e match {
    case _: StringLiteral  => Some(StringType)
    case _: BooleanLiteral => Some(BooleanType)
    case _: IntegerLiteral => Some(IntegerType)
    case _: SignedDecimalIntegerLiteral => Some(FloatType)
    case _: DoubleLiteral => Some(FloatType)
    case _: DecimalDoubleLiteral => Some(FloatType)
    case _: ListLiteral => Some(ListType)
    case _ => None
  }

  private def inferType(expr: Expression): Option[PropertyType] = expr match {
    case _: StringLiteral  => Some(StringType)
    case _: IntegerLiteral => Some(IntegerType)
    // adjust these to your branch's integer/decimal classes if different:
    case _: SignedDecimalIntegerLiteral => Some(FloatType)
    case _: DoubleLiteral      => Some(FloatType)
    case _: DecimalDoubleLiteral        => Some(FloatType)
    case _: BooleanLiteral => Some(BooleanType)
    case _: ListLiteral => Some(ListType)
    // function calls: date("..."), toInteger(...), etc
    case f: FunctionInvocation =>
      inferTypeFromFunction(f).orElse(Some(UnknownType))
    // parameters / variables / functions → unknown
    case _                 => Some(UnknownType)
  }

  private def inferTypeFromFunction(f: FunctionInvocation): Option[PropertyType] = {
    // Depending on your branch, you may need:
    // val name = f.functionName.name  OR  f.functionName.toString
    val name = f.functionName.name.toLowerCase(java.util.Locale.ROOT)

    name match {
      // temporal “instant type” constructors
      case "date" => Some(DateType) // date(...) :: DATE :contentReference[oaicite:1]{index=1}
      case "datetime" => Some(ZonedDateTimeType) // datetime(...) :: ZONED DATETIME :contentReference[oaicite:2]{index=2}
      case "localdatetime" => Some(LocalDateTimeType) // localdatetime(...) :: LOCAL DATETIME :contentReference[oaicite:3]{index=3}
      case "time" => Some(ZonedTimeType) // time(...) :: ZONED TIME :contentReference[oaicite:4]{index=4}
      case "localtime" => Some(LocalTimeType) // localtime(...) :: LOCAL TIME :contentReference[oaicite:5]{index=5}
      case "duration" => Some(DurationType) // duration(...) :: DURATION :contentReference[oaicite:6]{index=6}
      case "point" => Some(PointType) // point(...) :: POINT (see functions list) :contentreference[oaicite:7]{index=7}

      // casting
      case "tostring" => Some(StringType) // toString(...) returns STRING :contentReference[oaicite:8]{index=8}
      case "tointeger" => Some(IntegerType) // toInteger(...) returns INTEGER :contentReference[oaicite:9]{index=9}
      case "tofloat" => Some(FloatType) // toFloat(...) returns FLOAT :contentReference[oaicite:10]{index=10}
      case "toboolean" => Some(BooleanType) // toBoolean(...) returns BOOLEAN :contentReference[oaicite:11]{index=11}

      case _ =>
        None
    }
  }
  def toDTO(p: PropertyDescriptor): PropertyDescriptorDTO =
    PropertyDescriptorDTO(
      ownerIsNode   = p.ownerKind == NodeOwner,
      ownerName     = p.ownerName,
      propertyKey   = p.propertyKey,
      propertyType  = p.propertyType.map {
        case StringType  => "String"
        case IntegerType => "Number"
        case FloatType  => "Number"
        case BooleanType => "Boolean"
        case DateType => "Date"
        case LocalTimeType => "Time"
        case ZonedTimeType => "Time"
        case LocalDateTimeType => "Time"
        case ZonedDateTimeType => "Time"
        case DurationType => "Duration"
        case PointType  => "Point"
        case ListType    => "List"
        case UnknownType => "UNKNOWN"
      }.getOrElse("UNKNOWN")
    )

  /**
   * Extract all simple label/type names from a label expression.
   * Handles conjunctions / disjunctions / colon-chains; ignores wildcards & dynamic expressions.
   */
  private def extractNamesFromLabelExpression(expr: LabelExpression): Set[String] = expr match {
    case Leaf(LabelName(name), bool)    => Set(name)
    case Leaf(RelTypeName(name), bool)  => Set(name)

    case Conjunctions(children, bool)   => children.flatMap(extractNamesFromLabelExpression).toSet
    case Disjunctions(children, bool)   => children.flatMap(extractNamesFromLabelExpression).toSet
    case ColonConjunction(l, r, bool)   => extractNamesFromLabelExpression(l) ++ extractNamesFromLabelExpression(r)
    case ColonDisjunction(l, r, bool)   => extractNamesFromLabelExpression(l) ++ extractNamesFromLabelExpression(r)
    case Negation(inner, bool)          => extractNamesFromLabelExpression(inner)

    // Wildcards / dynamic names don’t give you a concrete schema token
    case Wildcard(_)              => Set.empty
    case DynamicLeaf(_, _)           => Set.empty
  }
}
