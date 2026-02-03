package org.neo4j.cs.ast

import org.neo4j.cypher.internal.ast.{IsNormalized, Unwind}
import org.neo4j.cypher.internal.expressions._
import org.neo4j.cypher.internal.label_expressions.LabelExpression._
import org.neo4j.cypher.internal.label_expressions.{LabelExpression, LabelExpressionPredicate}
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.{ASTNode, Foldable}

import java.util.{List => JList, Set => JSet}
import scala.jdk.CollectionConverters._



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
  case object VectorType     extends PropertyType
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

  final case class Result(labels: Set[String])
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
    final case class TokAcc(
                             nodeVars: Map[String, Set[String]],
                             relVars: Map[String, Set[String]],
                             labels: Set[String]
                           )

    val init = TokAcc(Map.empty, Map.empty, Set.empty)
    val out = root.folder.treeFold(init) {
      case node: NodePattern =>
        acc => {
          val labels = nodeLabels(node)
          val newNodeVars = node.variable match {
            case Some(v: LogicalVariable) =>
              val existing = acc.nodeVars.getOrElse(v.name, Set.empty)
              acc.nodeVars.updated(v.name, existing ++ labels)
            case _ => acc.nodeVars
          }
          TraverseChildren(acc.copy(nodeVars = newNodeVars, labels = acc.labels ++ labels))
        }

      case LabelName(name) =>
        acc => Foldable.TraverseChildren(acc.copy(labels = acc.labels + name))

      case LabelExpressionPredicate(Variable(v), le) =>
        acc => {
          val labelsOrTypes = extractNamesFromLabelExpression(le)
          val isNode = acc.nodeVars.contains(v)

          val (labels) =
            if (isNode) (labelsOrTypes)
            else (Set.empty[String])

          val newAcc = acc.copy(
            labels = acc.labels ++ labels,
          )

          TraverseChildren(newAcc)
        }

    }
    Result(out.labels)
  }
  // ----- Public API you call from Java -----
  def collectLabels(root: ASTNode): JSet[String] =
    collect(root).labels.asJava

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
      // --- INDEX CREATION: CREATE INDEX ... FOR (n:Label) ON (n.prop) ---
      case node: ASTNode if Set("CreateSingleLabelPropertyIndexCommand", "CreateConstraintCommand")
        .contains(node.getClass.getSimpleName) =>
        acc => {
          // Use reflection or structural extraction to avoid the import access error
          val labelField = node.getClass.getDeclaredMethods.find(_.getName == "entityName").map(_.invoke(node))
          val propsField = node.getClass.getDeclaredMethods.find(_.getName == "properties").map(_.invoke(node))
          val labelName = labelField match {
            case Some(l: LabelName) => l.name
            case _ => ""
          }

          val indexProps = try {
            propsField match {
              case Some(propsObj: Seq[_]) =>
                val props = propsObj.asInstanceOf[Seq[Any]]
                props.collect {
                  case Property(_, key) =>
                    PropertyDescriptor(NodeOwner, labelName, key.name, None)
                }
              case _ => Seq.empty
            }
          } catch { case _: Exception => Seq.empty }

          TraverseChildren(acc.copy(
            properties = acc.properties ++ indexProps
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
    def rightmostNode(pe: PatternElement): NodePattern = pe match {
      case np: NodePattern          => np
      case rc: RelationshipChain    => rightmostNode(rc.rightNode) // rightNode is a NodePattern, but keep it robust
      case other =>
        throw new IllegalArgumentException(s"Unexpected left pattern element: ${other.getClass.getName}")
    }

    root.folder.treeFold(Seq.empty[RelationshipDescriptor]) {

      case chain: RelationshipChain =>
        acc => {
          //left
          val leftNode: NodePattern = chain.element match {
            case np: NodePattern       => np
            case rc: RelationshipChain => rightmostNode(rc)
            case other =>
              throw new IllegalArgumentException(s"Unexpected chain. left: ${other.getClass.getName}")
          }
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

  def handleVarVsLiteralExpression(lhs: Expression, rhs: Expression) = {
    (env: Env) => {
      val env1 = (inferType(lhs), propRef(rhs)) match {
        case (Some(t), Some(p)) => env.addType(p, t)
        case _                  => env
      }
      val env2 = (inferType(rhs), propRef(lhs)) match {
        case (Some(t), Some(p)) => env1.addType(p, t)
        case _                  => env1
      }
      Foldable.TraverseChildren(env2)
    }
  }
  def handleTypedExpression(lhs: Expression, rhs: Expression, literalType: PropertyType) = {
    (env: Env) =>
      val e1 = propRef(lhs).map(p => env.addType(p, literalType)).getOrElse(env)
      val e2 = propRef(rhs).map(p => e1.addType(p, literalType)).getOrElse(e1)
      Foldable.TraverseChildren(e2)
  }
  def handleUnaryTypedExpression(inner: Expression, literalType: PropertyType) = {
    (env: Env) =>
      Foldable.TraverseChildren(propRef(inner).map(p => env.addType(p, literalType)).getOrElse(env))
  }


  /**
   * Walk expressions and collect type constraints for property references.
   * This is a conservative, best-effort pass focused on the common cases.
   */
  private def collectTypeEnv(root: ASTNode, initial: Env): Env = {
    root.folder.treeFold(initial) {

      // equality / comparison: propagate literal types to the opposite side
    case Equals(lhs, rhs) => handleVarVsLiteralExpression(lhs, rhs)
      case NotEquals(lhs, rhs) => handleVarVsLiteralExpression(lhs, rhs)
      case LessThan(lhs, rhs) => handleVarVsLiteralExpression(lhs, rhs)
      case LessThanOrEqual(lhs, rhs) => handleVarVsLiteralExpression(lhs, rhs)
      case GreaterThan(lhs, rhs) => handleVarVsLiteralExpression(lhs, rhs)
      case GreaterThanOrEqual(lhs, rhs) => handleVarVsLiteralExpression(lhs, rhs)
      case Add(lhs, rhs) => handleVarVsLiteralExpression(lhs, rhs)
      case Subtract(lhs, rhs) => handleVarVsLiteralExpression(lhs, rhs)
      case Multiply(lhs, rhs) => handleVarVsLiteralExpression(lhs, rhs)
      case Divide(lhs, rhs) => handleVarVsLiteralExpression(lhs, rhs)
      case Concatenate(lhs, rhs) => handleVarVsLiteralExpression(lhs, rhs)
      // some operators imply a type on both side
      case Modulo(lhs, rhs) => handleTypedExpression(lhs, rhs, IntegerType)
      case Pow(lhs, rhs) => handleTypedExpression(lhs, rhs, IntegerType)
      case Or(lhs, rhs) => handleTypedExpression(lhs, rhs, BooleanType)
      case And(lhs, rhs) => handleTypedExpression(lhs, rhs, BooleanType)
      case Xor(lhs, rhs) => handleTypedExpression(lhs, rhs, BooleanType)
      case StartsWith(lhs, rhs) => handleTypedExpression(lhs, rhs, StringType)
      case EndsWith(lhs, rhs) => handleTypedExpression(lhs, rhs, StringType)
      case Contains(lhs, rhs) => handleTypedExpression(lhs, rhs, StringType)
      case RegexMatch(lhs, rhs) => handleTypedExpression(lhs, rhs, StringType)
      //IN : implies only for rhs
      case In(lhs, rhs) =>
        env =>
          val e2 = propRef(rhs).map(p => env.addType(p, ListType)).getOrElse(env)
          Foldable.TraverseChildren(e2)
      // some unary operators imply a type
      case Not(inner) => handleUnaryTypedExpression(inner, BooleanType)
      case UnarySubtract(inner) => handleUnaryTypedExpression(inner, IntegerType)
      case IsNormalized(inner, _) => handleUnaryTypedExpression(inner, StringType)

      //unwind
      case Unwind(expression, _) =>
        env => Foldable.TraverseChildren(propRef(expression).map(p => env.addType(p, ListType)).getOrElse(env))
      // function calls: constrain argument types for known functions
      case f: FunctionInvocation =>
        env => Foldable.TraverseChildren(applyFunctionConstraints(env, f))

      // default: traverse children
    }
  }

  // Helper: apply typing constraints for first parameter of known functions
  private def applyFunctionConstraints(env: Env, f: FunctionInvocation): Env = {
    val name = extractFunctionName(f)

    name match {
      case "upper"| "lower" | "toUpper" | "toLower" | "ltrim" | "rtrim"
         | "char_length" | "character_length"|"left"|"right"|"normalize"
         | "replace" | "split" | "substring"| "apoc.text.replace" =>
        f.arguments.lift(0).flatMap(propRef) match {
          case Some(p) => env.addType(p, StringType)
          case None => env
        }

      case "format"|"duration.between"|"duration.inDays"|"duration.inMonths"|"duration.inSeconds" =>
        f.arguments.lift(0).flatMap(propRef) match {
          case Some(p) => env.addType(p, DateType)
          case None => env
        }

      case "exp" | "log"| "log10"| "sqrt"|"abs"|"ceil"|"floor"|"round"|"sign"
           |"acos"|"asin"|"atan"|"atan2"|"cos"|"cosh"|"cot"|"coth"|"degrees"
           |"haversin"|"radians"|"sin"|"sinh"|"tan"|"tanh"
           | "range" =>
        f.arguments.lift(0).flatMap(propRef) match {
          case Some(p) => env.addType(p, FloatType)
          case None => env
        }

      case "head" |"tail" | "last"| "coll.distinct"| "coll.flatten" | "coll.indexOf"
           | "coll.insert" | "coll.max"|"coll.min" | "coll.remove" | "coll.sort"
           | "toStringList" | "toBooleanList" =>
        f.arguments.lift(0).flatMap(propRef) match {
          case Some(p) => env.addType(p, ListType)
          case None => env
        }

      case "point.distance" | "point.withinBBox" =>
        f.arguments.lift(0).flatMap(propRef) match {
          case Some(p) => env.addType(p, PointType)
          case None => env
        }

      case "vector_distance"|"vector_norm"|"vector_dimension_count" =>
        f.arguments.lift(0).flatMap(propRef) match {
          case Some(p) => env.addType(p, VectorType)
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
      inferReturnedTypeFromFunction(f).orElse(Some(UnknownType))
    // parameters / variables / functions → unknown
    case _                 => Some(UnknownType)
  }

  private def inferReturnedTypeFromFunction(f: FunctionInvocation): Option[PropertyType] = {
    val name = extractFunctionName(f)

    name match {
      // temporal “instant type” constructors
      case "date"|"date.realtime"|"date.statement"|"date.transaction"|"date.truncate" => Some(DateType) // date(...) :: DATE :contentReference[oaicite:1]{index=1}
      case "datetime"|"datetime.fromEpoch"|"datetime.fromEpochMillis"
           |"datetime.realtime"|"datetime.statement"|"datetime.transaction"|"datetime.truncate"=> Some(ZonedDateTimeType) // datetime(...) :: ZONED DATETIME :contentReference[oaicite:2]{index=2}
      case "localdatetime"|"localdatetime.realtime"|"localdatetime.statement"|"localdatetime.transaction"
           |"localdatetime.truncate" => Some(LocalDateTimeType) // localdatetime(...) :: LOCAL DATETIME :contentReference[oaicite:3]{index=3}
      case "time"|"time.realtime"|"time.statement"|"time.transaction"|"time.truncate" => Some(ZonedTimeType) // time(...) :: ZONED TIME :contentReference[oaicite:4]{index=4}
      case "localtime"|"localtime.realtime"|"localtime.statement"|"localtime.transaction"|"localtime.truncate" => Some(LocalTimeType) // localtime(...) :: LOCAL TIME :contentReference[oaicite:5]{index=5}
      case "duration"|"duration.between"|"duration.inDays"|"duration.inMonths"|"duration.inSeconds" => Some(DurationType) // duration(...) :: DURATION :contentReference[oaicite:6]{index=6}
      //geo type
      case "point" => Some(PointType) // point(...) :: POINT (see functions list) :contentreference[oaicite:7]{index=7}

      // casting
      case "toString"|"toStringOrNull" => Some(StringType) // toString(...) returns STRING :contentReference[oaicite:8]{index=8}
      case "toInteger"|"toIntegerOrNull" => Some(IntegerType) // toInteger(...) returns INTEGER :contentReference[oaicite:9]{index=9}
      case "toFloat"|"toFloatOrNull" => Some(FloatType) // toFloat(...) returns FLOAT :contentReference[oaicite:10]{index=10}
      case "toBoolean"|"toBooleanOrNull" => Some(BooleanType) // toBoolean(...) returns BOOLEAN :contentReference[oaicite:11]{index=11}
      //int returned
      case "count"|"char_length"|"character_length"|"id"|"length"|"size"|"timestamp"
            |"vector_dimension_count" => Some(IntegerType)
      case "e"|"exp"|"log"|"log10"|"sqrt"|"abs"|"rand"|"ceil"|"round"|"floor"|"point.distance"
            |"percentileCont"|"percentileDisc"|"stDev"|"stDevP"|"sign"
            | "acos"|"asin"|"atan"|"atan2"|"cos"|"cosh"|"cot"|"coth"|"degrees"
            |"haversin"|"radians"|"sin"|"sinh"|"tan"|"tanh"
            |"vector.similarity.cosine"|"vector.similarity.euclidean"|"vector_distance"|"vector_norm" => Some(FloatType)
      //string returned
      case "trim"|"ltrim"|"rtrim"|"toUpper"|"toLower"|"upper"|"lower"|"left"|"right"
           |"normalize"|"substring"|"replace"|"format"|"db.nameFromElementId"
           |"elementId"|"type"|"valueType" => Some(StringType)
      //list returned
      case "collect"|"split"|"tail"|"toBooleanList"|"toFloatList"|"toIntegerList"|"toStringList"|"range"|"labels"
            |"coll.distinct"|"coll.flatten"|"coll.insert"|"coll.remove"|"coll.sort"|"keys"
            |"graph.names"|"nodes"|"relationships"  => Some(ListType)
      //boolean
      case "point.withinBBox"|"isEmpty" => Some(BooleanType)
      //vector
      case "vector" => Some(VectorType)
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
        case VectorType    => "Vector"
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
    case Leaf(LabelOrRelTypeName(name), bool)  => Set(name)

    case Conjunctions(children, bool)   => children.flatMap(extractNamesFromLabelExpression).toSet
    case Disjunctions(children, bool)   => children.flatMap(extractNamesFromLabelExpression).toSet
    case ColonConjunction(l, r, bool)   => extractNamesFromLabelExpression(l) ++ extractNamesFromLabelExpression(r)
    case ColonDisjunction(l, r, bool)   => extractNamesFromLabelExpression(l) ++ extractNamesFromLabelExpression(r)
    case Negation(inner, bool)          => extractNamesFromLabelExpression(inner)

    // Wildcards / dynamic names don’t give you a concrete schema token
    case Wildcard(_)              => Set.empty
    case DynamicLeaf(_, _)           => Set.empty
    case _ => Set.empty
  }

  private def extractFunctionName(f: FunctionInvocation): String = {
    val fn = f.functionName

    if (fn.namespace.parts.isEmpty)
      fn.name
    else
      (fn.namespace.parts :+ fn.name).mkString(".")
  }
}
