package org.neo4j.cs.ast

import org.neo4j.cypher.internal.ast.{IsNormalized, SetPropertyItem, Unwind}
import org.neo4j.cypher.internal.expressions._
import org.neo4j.cypher.internal.label_expressions.LabelExpression._
import org.neo4j.cypher.internal.label_expressions.{LabelExpression, LabelExpressionPredicate}
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.{ASTNode, Foldable}

import java.util.{Collections, IdentityHashMap, List => JList, Set => JSet}
import scala.jdk.CollectionConverters._
import org.neo4j.cypher.internal.util.symbols._


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

  // Cache the function registry to avoid expensive repeated calls (116 native function included)
  private lazy val cachedFunctionRegistry = CypherFunctionRegistry.allFunctions

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

  def collectLabels(root: ASTNode): JSet[String] = {
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
    out.labels.asJava
  }

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
          val types = relationshipTypesEligibleForMetadata(relPat)

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

      val filteredTypes = types - UnknownType
      val chosenType: Option[PropertyType] =
        if (filteredTypes.isEmpty) {
          // If the set is empty or only contained UnknownType, return UnknownType
          if (types.nonEmpty) Some(UnknownType) else None //
        }
        else if (filteredTypes.size == 1) {
          // If we have exactly one concrete type left, use it
          Some(filteredTypes.head) //
        }
        else {
          // If we still have multiple concrete types, it's truly ambiguous
          Some(UnknownType) //
        }

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
    val labelPredicatesInOr =
      Collections.newSetFromMap(new IdentityHashMap[LabelExpressionPredicate, java.lang.Boolean]())

    // 1. First Pass: Build a comprehensive map of variables to labels
    // This includes labels from NodePatterns and LabelExpressionPredicates (WHERE clause)
    val varLabelMap = root.folder.treeFold(Map.empty[String, Set[String]]) {
      case Or(lhs: LabelExpressionPredicate, rhs: LabelExpressionPredicate) =>
        acc => {
          labelPredicatesInOr.add(lhs)
          labelPredicatesInOr.add(rhs)
          TraverseChildren(acc)
        }

      case Or(lhs: LabelExpressionPredicate, _) =>
        acc => {
          labelPredicatesInOr.add(lhs)
          TraverseChildren(acc)
        }

      case Or(_, rhs: LabelExpressionPredicate) =>
        acc => {
          labelPredicatesInOr.add(rhs)
          TraverseChildren(acc)
        }

      case node: NodePattern =>
        acc => {
          val labels = nodeLabels(node, includeAll = false)
          val newAcc = node.variable match {
            case Some(v: LogicalVariable) =>
              val existing = acc.getOrElse(v.name, Set.empty)
              acc.updated(v.name, existing ++ labels)
            case _ => acc
          }
          TraverseChildren(newAcc)
        }

      case labelPredicate @ LabelExpressionPredicate(Variable(v), le) =>
        acc => {
          if (labelPredicatesInOr.contains(labelPredicate)) {
            TraverseChildren(acc)
          } else {
            val labels = extractNamesFromLabelExpressionSelectively(le)
            val existing = acc.getOrElse(v, Set.empty)
            TraverseChildren(acc.updated(v, existing ++ labels))
          }
        }
    }

    def leftmostNode(pe: PatternElement): NodePattern = pe match {
      case np: NodePattern       => np
      case rc: RelationshipChain => leftmostNode(rc.element)
      case ParenthesizedPath(part, _) => leftmostNode(part.element)
      case other =>
        throw new IllegalArgumentException(s"Unexpected pattern element: ${other.getClass.getName}")
    }

    def rightmostNode(pe: PatternElement): NodePattern = pe match {
      case np: NodePattern       => np
      case rc: RelationshipChain => rightmostNode(rc.rightNode)
      case ParenthesizedPath(part, _) => rightmostNode(part.element)
      case other =>
        throw new IllegalArgumentException(s"Unexpected pattern element: ${other.getClass.getName}")
    }

    def leftmostRelationshipChain(pe: PatternElement): Option[RelationshipChain] = pe match {
      case rc: RelationshipChain =>
        rc.element match {
          case nested: RelationshipChain => leftmostRelationshipChain(nested)
          case _                         => Some(rc)
        }
      case PathConcatenation(factors) =>
        factors.view.flatMap(leftmostRelationshipChain).headOption
      case qp: QuantifiedPath =>
        leftmostRelationshipChain(qp.part.element)
      case ParenthesizedPath(part, _) =>
        leftmostRelationshipChain(part.element)
      case _ =>
        None
    }

    def rightmostRelationshipChain(pe: PatternElement): Option[RelationshipChain] = pe match {
      case rc: RelationshipChain =>
        Some(rc)
      case PathConcatenation(factors) =>
        factors.view.reverse.flatMap(rightmostRelationshipChain).headOption
      case qp: QuantifiedPath =>
        rightmostRelationshipChain(qp.part.element)
      case ParenthesizedPath(part, _) =>
        rightmostRelationshipChain(part.element)
      case _ =>
        None
    }

    // Helper to resolve labels by checking both inline labels and the variable map
    def resolveLabels(node: NodePattern): Set[String] = {
      val inlineLabels = nodeLabels(node, includeAll=false)
      val mappedLabels = node.variable.collect {
        case v: LogicalVariable => varLabelMap.getOrElse(v.name, Set.empty)
      }.getOrElse(Set.empty)
      inlineLabels ++ mappedLabels
    }

    def relationshipDescriptors(
      chain: RelationshipChain,
      extraLeftLabels: Set[String] = Set.empty,
      extraRightLabels: Set[String] = Set.empty
    ): Seq[RelationshipDescriptor] = {
      val leftNode: NodePattern = chain.element match {
        case np: NodePattern       => np
        case rc: RelationshipChain => rightmostNode(rc)
        case other =>
          throw new IllegalArgumentException(s"Unexpected chain. left: ${other.getClass.getName}")
      }
      val rightNode: NodePattern  = chain.rightNode
      val relPat: RelationshipPattern = chain.relationship
      val dir: SemanticDirection = relPat.direction

      val leftLabels = resolveLabels(leftNode) ++ extraLeftLabels
      val rightLabels = resolveLabels(rightNode) ++ extraRightLabels

      val (srcLabels, tgtLabels, undirLabels) =
        dir match {
          case SemanticDirection.OUTGOING =>
            (leftLabels, rightLabels, Set.empty[String])
          case SemanticDirection.INCOMING =>
            (rightLabels, leftLabels, Set.empty[String])
          case SemanticDirection.BOTH =>
            (Set.empty[String], Set.empty[String], leftLabels ++ rightLabels)
        }

      val allTypes = relationshipTypes(relPat)
      val metadataTypes = relationshipTypesEligibleForMetadata(relPat)

      allTypes.toSeq.map { t =>
        val labels =
          if (metadataTypes.contains(t)) {
            (srcLabels, tgtLabels, undirLabels)
          } else {
            (Set.empty[String], Set.empty[String], Set.empty[String])
          }
        RelationshipDescriptor(t, labels._1, labels._2, labels._3)
      }
    }

    def qppBoundaryDescriptors(
      qp: QuantifiedPath,
      leftLabels: Set[String],
      rightLabels: Set[String]
    ): Seq[RelationshipDescriptor] = {
      val element = qp.part.element
      val leftDescriptors =
        leftmostRelationshipChain(element).toSeq.flatMap(relationshipDescriptors(_, extraLeftLabels = leftLabels))
      val rightDescriptors =
        rightmostRelationshipChain(element).toSeq.flatMap(relationshipDescriptors(_, extraRightLabels = rightLabels))

      leftDescriptors ++ rightDescriptors
    }

    // 2. Second Pass: Traverse RelationshipChains and resolve variables
    root.folder.treeFold(Seq.empty[RelationshipDescriptor]) {
      case PathConcatenation(factors) =>
        acc => {
          val descriptors = factors.zipWithIndex.collect {
            case (qp: QuantifiedPath, index) =>
              val leftLabels = factors.lift(index - 1).map(f => resolveLabels(rightmostNode(f))).getOrElse(Set.empty)
              val rightLabels = factors.lift(index + 1).map(f => resolveLabels(leftmostNode(f))).getOrElse(Set.empty)
              qppBoundaryDescriptors(qp, leftLabels, rightLabels)
          }.flatten

          TraverseChildren(acc ++ descriptors)
        }

      case chain: RelationshipChain =>
        acc => {
          TraverseChildren(acc ++ relationshipDescriptors(chain))
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

  /** Extract labels from a NodePattern’s labelExpression
   * includeAll : if true all mentioned labels are collected. otherwise only those not negated and not part of a conjunction */
  private def nodeLabels(node: NodePattern, includeAll: Boolean = true): Set[String] = {
    if (includeAll) {
      node.labelExpression.map(extractNamesFromLabelExpression).getOrElse(Set.empty)
    } else {
      node.labelExpression.map(extractNamesFromLabelExpressionSelectively).getOrElse(Set.empty)
    }
  }

  /** Extract relationship types from a RelationshipPattern’s labelExpression */
  private def relationshipTypes(rel: RelationshipPattern, includeAll: Boolean = true): Set[String] =
    if (includeAll) {
      rel.labelExpression.map(extractNamesFromLabelExpression).getOrElse(Set.empty)
    } else {
      rel.labelExpression.map(extractNamesFromLabelExpressionSelectively).getOrElse(Set.empty)
    }

  private def relationshipTypesEligibleForMetadata(rel: RelationshipPattern): Set[String] = {
    val allTypes = relationshipTypes(rel)
    val selectiveTypes = relationshipTypes(rel, includeAll = false)

    // Multiple relationship types in one pattern are alternatives, not a single
    // schema assertion. Keep them in the inventory but do not attach properties
    // or endpoint labels to each possible type.
    if (allTypes.size == 1 && selectiveTypes == allTypes) {
      allTypes
    } else {
      Set.empty
    }
  }

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
      // IN : implies ListType for rhs, and potentially element type for lhs
      case In(lhs, rhs) =>
        env =>
          // property on rhs => List
          val envWithList = propRef(rhs).map(p => env.addType(p, ListType)).getOrElse(env)
          // property on lhs => infer its type from the rhs list elements
          val envWithLhs = propRef(lhs) match {
            case Some(pRef) =>
              rhs match {
                case ListLiteral(expressions) if expressions.nonEmpty =>
                  // Extract types for all elements in the list literal
                  val elementTypes = expressions.flatMap(inferType).toSet - UnknownType
                  // Only infer if the list is homogenous (exactly one concrete type found)
                  if (elementTypes.size == 1) {
                    envWithList.addType(pRef, elementTypes.head)
                  } else {
                    envWithList
                  }
                case _ => envWithList
              }
            case None => envWithList
          }

          Foldable.TraverseChildren(envWithLhs)
      // some unary operators imply a type
      case Not(inner) => handleUnaryTypedExpression(inner, BooleanType)
      case UnarySubtract(inner) => handleUnaryTypedExpression(inner, IntegerType)
      case IsNormalized(inner, _) => handleUnaryTypedExpression(inner, StringType)

      //unwind
      case Unwind(expression, _) =>
        env => Foldable.TraverseChildren(propRef(expression).map(p => env.addType(p, ListType)).getOrElse(env))

      //SET property
      case SetPropertyItem(prop, expr) => handleVarVsLiteralExpression(prop, expr)

      // function calls: constrain argument types for known functions
      case f: FunctionInvocation =>
        env => Foldable.TraverseChildren(applyFunctionConstraints(env, f))

      // default: traverse children
    }
  }

  // Helper: apply typing constraints for first parameter of known functions
  private def applyFunctionConstraints(env: Env, f: FunctionInvocation): Env = {
    val name = extractFunctionName(f)
    val param1Type = cachedFunctionRegistry.get(name)
      .flatMap(_.signatures.headOption)      // Safely get first signature
      .flatMap(_.argumentTypes.headOption)   // Safely get first argument
      .map(mapCypherTypeToPropertyType)      // Convert if it exists
      .getOrElse(                // Final unwrapped fallback
        name match {
          case "apoc.text.replace"| "apoc.text.distance"
               | "apoc.text.urlencode" | "apoc.text.urldecode" | "apoc.json.path"
               | "apoc.xml.parse" => StringType
          case "format"|"duration.between"|"duration.inDays"|"duration.inMonths"|"duration.inSeconds" => DateType
          case  "apoc.coll.elements" |"apoc.coll.split"
                | "apoc.coll.zipToRows" | "apoc.coll.avg" | "apoc.coll.combinations"
                | "apoc.coll.contains" | "apoc.coll.containsAll" | "apoc.coll.containsAllSorted"
                | "apoc.coll.containsDuplicates" | "apoc.coll.containsSorted" | "apoc.coll.different"
                | "apoc.coll.disjunction" | "apoc.coll.dropDuplicateNeighbors" | "apoc.coll.duplicates"
                | "apoc.coll.duplicatesWithCount" | "apoc.coll.flatten" | "apoc.coll.frequencies"
                | "apoc.coll.frequenciesAsMap" | "apoc.coll.indexOf" | "apoc.coll.insert"
                | "apoc.coll.insertAll" | "apoc.coll.intersection" | "apoc.coll.isEqualCollection"
                | "apoc.coll.max" | "apoc.coll.min" | "apoc.coll.occurrences" | "apoc.coll.pairs"
                | "apoc.coll.randomItems" | "apoc.coll.set" | "apoc.coll.sort" | "apoc.coll.sum"
                | "apoc.coll.toSet" | "apoc.coll.union" | "apoc.coll.unionAll" | "apoc.coll.zip" => ListType
          case _ => UnknownType
        }
      )
    f.arguments.lift(0).flatMap(propRef) match {
      case Some(p) => env.addType(p, param1Type)
      case None => env
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
    case _: SignedDecimalIntegerLiteral => Some(FloatType)
    case _: DoubleLiteral      => Some(FloatType)
    case _: DecimalDoubleLiteral        => Some(FloatType)
    case _: BooleanLiteral => Some(BooleanType)
    case _: ListLiteral => Some(ListType)
    case _: ListComprehension => Some(ListType)
    case Add(lhs, rhs) =>
      val left = inferType(lhs)
      val right = inferType(rhs)
      if (left.contains(StringType) || right.contains(StringType)) Some(StringType)
      else if (left.contains(IntegerType) && right.contains(IntegerType)) Some(IntegerType)
      else if (left.contains(FloatType) || right.contains(FloatType)) Some(FloatType)
      else if (left.contains(ListType) || right.contains(ListType)) Some(ListType)
      else Some(UnknownType)
    // function calls: date("..."), toInteger(...), etc
    case f: FunctionInvocation =>
      inferReturnedTypeFromFunction(f).orElse(Some(UnknownType))
    // parameters / variables / functions → unknown
    case _                 => Some(UnknownType)
  }

  private def mapCypherTypeToPropertyType(ct: CypherType): PropertyType = {
    if (ct.toClassString == "String") StringType
    else if (ct.toClassString == "Integer") IntegerType
    else if (ct.toClassString == "Float") FloatType
    else if (ct.toClassString == "Boolean") BooleanType
    else if (ct.toClassString == "Point") PointType
    else if (ct.toClassString == "List<Any>") ListType
    else if (ct.toClassString == "List<String>") ListType
    else if (ct.toClassString == "List<Integer>") ListType
    else if (ct.toClassString == "List<Float>") ListType
    else if (ct.toClassString == "List<Boolean>") ListType
    else if (ct.toClassString == "List<Point>") ListType
    else if (ct.toClassString == "Vector") VectorType
    //date function are not in the registry, so the below is not necessary
    //    else if (ct.toClassString == "xxxx") DateType
    //    else if (ct.toClassString == "xxxx") LocalTimeType
    //    else if (ct.toClassString == "xxxx") ZonedTimeType
    //    else if (ct.toClassString == "xxxx") LocalDateTimeType
    //    else if (ct.toClassString == "xxxx") ZonedDateTimeType
    //    else if (ct.toClassString == "xxxx") DurationType
    else UnknownType // non-useful types (Node, Relationship, Graph...) => UnknownType
  }

  private def inferReturnedTypeFromFunction(f: FunctionInvocation): Option[PropertyType] = {
    val name = extractFunctionName(f)
    val cypherFunc = cachedFunctionRegistry.get(name)
    cypherFunc
      .map(func => mapCypherTypeToPropertyType(func.signatures.head.outputType)) // several signatures? => pick first arbitrarily
      .orElse(
        //non-native functions : date-related, APOC, etc
        name match {
          // temporal “instant type” constructors
          case "date"|"date.realtime"|"date.statement"|"date.transaction"|"date.truncate" => Some(DateType)
          case "datetime"|"datetime.fromEpoch"|"datetime.fromEpochMillis"
               |"datetime.realtime"|"datetime.statement"|"datetime.transaction"|"datetime.truncate"=> Some(ZonedDateTimeType)
          case "localdatetime"|"localdatetime.realtime"|"localdatetime.statement"|"localdatetime.transaction"
               |"localdatetime.truncate" => Some(LocalDateTimeType)
          case "time"|"time.realtime"|"time.statement"|"time.transaction"|"time.truncate" => Some(ZonedTimeType)
          case "localtime"|"localtime.realtime"|"localtime.statement"|"localtime.transaction"|"localtime.truncate" => Some(LocalTimeType)
          case "duration"|"duration.between"|"duration.inDays"|"duration.inMonths"|"duration.inSeconds" => Some(DurationType)
          //int returned
          case "apoc.coll.indexOf"|"apoc.text.distance" => Some(IntegerType)
          //string returned
          case "db.nameFromElementId"
               |"apoc.util.sha1" | "apoc.util.sha256"| "apoc.util.sha384"| "apoc.util.sha512"
               |"apoc.util.md5" | "apoc.text.camelCase" | "apoc.text.base64Decode" | "apoc.text.base64Encode"
               |"apoc.text.urlencode" | "apoc.text.urldecode"  => Some(StringType)
          //list returned
          case "graph.names"|"apoc.coll.fill"|"apoc.coll.flatten"|"apoc.coll.frequencies"
               |"apoc.coll.insert" | "apoc.coll.randomItems" | "apoc.coll.set" | "apoc.coll.sort" | "apoc.coll.toSet"
               |"apoc.coll.union" | "apoc.coll.unionAll" | "apoc.coll.zip" => Some(ListType)
          case _ =>
            None
        }
      )
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

  private def extractNamesFromLabelExpression(expr: LabelExpression): Set[String] = expr match {
    //just collect all names used in the expression to inventory them
    case Leaf(LabelName(name), bool)    => Set(name)
    case Leaf(RelTypeName(name), bool)  => Set(name)
    case Leaf(LabelOrRelTypeName(name), bool)  => Set(name)

    case Conjunctions(children, bool)   => children.flatMap(extractNamesFromLabelExpression).toSet
    case Disjunctions(children, bool)   => children.flatMap(extractNamesFromLabelExpression).toSet
    case ColonConjunction(l, r, bool)   => extractNamesFromLabelExpression(l) ++ extractNamesFromLabelExpression(r)
    case ColonDisjunction(l, r, bool)   => extractNamesFromLabelExpression(l) ++ extractNamesFromLabelExpression(r)
    case Negation(inner, bool)          => extractNamesFromLabelExpression(inner)

    case Wildcard(_)              => Set.empty
    case DynamicLeaf(_, _)           => Set.empty
    case _ => Set.empty
  }
  private def extractNamesFromLabelExpressionSelectively(expr: LabelExpression): Set[String] = expr match {
    //more restrictive interpretation : any disjonction causes the labels to be ignored
    case Leaf(LabelName(name), bool)    => Set(name)
    case Leaf(RelTypeName(name), bool)  => Set(name)
    case Leaf(LabelOrRelTypeName(name), bool)  => Set(name)

    case Conjunctions(children, bool)   => children.flatMap(extractNamesFromLabelExpressionSelectively).toSet
    case Disjunctions(children, bool)   => Set.empty //OR disjunctions : which pattern actually exists is not guaranteed, so ignore
    case ColonConjunction(l, r, bool)   => extractNamesFromLabelExpressionSelectively(l) ++ extractNamesFromLabelExpressionSelectively(r)
    case ColonDisjunction(l, r, bool)   => Set.empty //OR disjunctions : which pattern actually exists is not guaranteed, so ignore
    case Negation(inner, bool)          => Set.empty //negated labels should be excluded

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