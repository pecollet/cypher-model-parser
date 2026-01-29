package org.neo4j.cs.ast

import org.neo4j.cypher.internal.expressions._
import org.neo4j.cypher.internal.util.ASTNode
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren



object CypherAstMasker {

  /**
   * Mask all scalar literal occurrences in the original Cypher text.
   *
   * Option B implementation: we use the AST only to find literal start offsets (InputPosition.offset),
   * then compute the literal token length by scanning the original Cypher string.
   *
   * We replace each literal token with '*' repeated to the same length, preserving formatting and offsets.
   */
  def maskLiterals(cypher: String, root: ASTNode): String = {
    final case class Span(start: Int, endExclusive: Int)

    def scanStringLiteral(start: Int): Int = {
      // Cypher string literals use single quotes and escape a quote by doubling it: 'it''s'
      if (start < 0 || start >= cypher.length) return start
      val quote = cypher.charAt(start)
      if (quote != '\'' && quote != '"') return start
      var i = start + 1
      while (i < cypher.length) {
        val c = cypher.charAt(i)
        if (c == quote) {
          // doubled quote => escaped quote, consume both
          if (i + 1 < cypher.length && cypher.charAt(i + 1) == quote) i += 2
          else return i + 1
        } else i += 1
      }
      cypher.length
    }

    def scanNumberLiteral(start: Int): Int = {
      if (start < 0 || start >= cypher.length) return start
      var i = start
      def isNumChar(ch: Char): Boolean =
        (ch >= '0' && ch <= '9') || ch == '+' || ch == '-' || ch == '.' || ch == 'e' || ch == 'E' || ch == '_'
      while (i < cypher.length && isNumChar(cypher.charAt(i))) i += 1
      i
    }

    def scanWord(start: Int): Int = {
      if (start < 0 || start >= cypher.length) return start
      var i = start
      def isAlpha(ch: Char): Boolean =
        (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')
      while (i < cypher.length && isAlpha(cypher.charAt(i))) i += 1
      i
    }

    def spanForLiteral(start: Int, lit: Expression): Option[Span] = {
      if (start < 0 || start >= cypher.length) return None

      lit match {
        case _: StringLiteral =>
          val end = scanStringLiteral(start)
          if (end > start) Some(Span(start, end)) else None

        case _: IntegerLiteral | _: SignedDecimalIntegerLiteral | _: DoubleLiteral | _: DecimalDoubleLiteral =>
          val end = scanNumberLiteral(start)
          if (end > start) Some(Span(start, end)) else None

        // Do not mask list/map literals as a whole—children literals will be masked individually.
        case _ => None
      }
    }

    val spans = root.folder.treeFold(Vector.empty[Span]) {
      case lit: Literal =>
        acc => {
          val start = lit.position.offset
          spanForLiteral(start, lit) match {
            case Some(s) => TraverseChildren(acc :+ s)
            case None    => TraverseChildren(acc)
          }
        }
    }

    // Replace from right to left to keep offsets stable.
    val sorted = spans.distinct.sortBy(_.start)(Ordering.Int.reverse)

    val sb = new StringBuilder(cypher)
    sorted.foreach { s =>
      if (s.start >= 0 && s.endExclusive <= sb.length && s.endExclusive > s.start) {
        val len = s.endExclusive - s.start
        sb.replace(s.start, s.endExclusive, "*" * 4)
      }
    }

    sb.toString
  }

}
