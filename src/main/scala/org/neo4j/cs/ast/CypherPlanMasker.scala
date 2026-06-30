package org.neo4j.cs.ast

import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.expressions._
import org.neo4j.cypher.internal.util.InputPosition

object CypherPlanMasker {
  def maskPlan(plan: LogicalPlan): LogicalPlan = {
    rewriteAny(plan).asInstanceOf[LogicalPlan]
  }

  private def copyCaseClass(prod: Product, rewrittenElements: Array[AnyRef]): Any = {
    val clazz = prod.getClass
    val constructors = clazz.getConstructors
    
    // Sort constructors by parameter count descending to find the primary constructor
    val constructor = constructors.sortBy(_.getParameterTypes.length)(Ordering.Int.reverse).find { c =>
      c.getParameterTypes.length >= rewrittenElements.length
    }

    constructor match {
      case Some(c) =>
        val paramTypes = c.getParameterTypes
        val args = new Array[AnyRef](paramTypes.length)
        Array.copy(rewrittenElements, 0, args, 0, rewrittenElements.length)
        
        if (paramTypes.length > rewrittenElements.length) {
          // Fill extra parameters
          for (i <- rewrittenElements.length until paramTypes.length) {
            val t = paramTypes(i)
            if (classOf[org.neo4j.cypher.internal.util.attribution.IdGen].isAssignableFrom(t)) {
              args(i) = new org.neo4j.cypher.internal.util.attribution.IdGen {
                override def id(): org.neo4j.cypher.internal.util.attribution.Id = prod match {
                  case lp: LogicalPlan => lp.id
                  case _ => org.neo4j.cypher.internal.util.attribution.Id(0)
                }
              }
            } else if (classOf[InputPosition].isAssignableFrom(t)) {
              args(i) = InputPosition.NONE
            } else {
              // Look for a field on the class hierarchy that is assignable to the parameter type
              var currentClazz: Class[_] = clazz
              var foundValue: AnyRef = null
              while (currentClazz != null && foundValue == null) {
                val field = currentClazz.getDeclaredFields.find(f => t.isAssignableFrom(f.getType))
                field.foreach { f =>
                  f.setAccessible(true)
                  foundValue = f.get(prod)
                }
                currentClazz = currentClazz.getSuperclass
              }
              args(i) = foundValue
            }
          }
        }
        c.newInstance(args: _*)
      case None =>
        org.neo4j.cypher.internal.util.Rewritable.copyProduct(prod, rewrittenElements)
    }
  }

  private def rewriteAny(x: Any): Any = {
    x match {
      case _: StringLiteral =>
        StringLiteral("****")(InputPosition.NONE)
      case _: IntegerLiteral | _: SignedDecimalIntegerLiteral =>
        StringLiteral("****")(InputPosition.NONE)
      case _: DoubleLiteral | _: DecimalDoubleLiteral =>
        StringLiteral("****")(InputPosition.NONE) 

      case opt: Option[_] =>
        opt.map(rewriteAny)

      case seq: Seq[_] =>
        seq.map(rewriteAny)

      case set: Set[_] =>
        set.map(rewriteAny)

      case map: Map[_, _] =>
        map.map { case (k, v) => (rewriteAny(k), rewriteAny(v)) }

      case prod: Product =>
        val elements = prod.productIterator.toArray
        var changed = false
        val rewrittenElements = elements.map { elem =>
          val rewritten = rewriteAny(elem)
          if (rewritten != elem) {
            changed = true
          }
          rewritten.asInstanceOf[AnyRef]
        }
        if (changed) {
          try {
            copyCaseClass(prod, rewrittenElements)
          } catch {
            case e: Exception =>
              println(s"Error copying case class ${prod.getClass.getName}: $e")
              e.printStackTrace()
              // Fallback to original if copying fails
              prod
          }
        } else {
          prod
        }

      case other =>
        other
    }
  }
}
