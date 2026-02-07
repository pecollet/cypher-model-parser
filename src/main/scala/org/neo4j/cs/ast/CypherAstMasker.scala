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
   *
   * @param cypher The original cypher query string
   * @param root The AST root of the query
   * @param parse A callback to parse nested Cypher strings (e.g. for APOC). Returns None if the string is not valid Cypher.
   */
  def maskLiterals(cypher: String, root: ASTNode, parse: String => Option[ASTNode] = _ => None): String = {
    final case class Span(start: Int, endExclusive: Int)

    // Helper method to collect spans recursively
    def collectSpans(text: String, ast: ASTNode): Vector[Span] = {

      def scanStringLiteral(start: Int): Int = {
        if (start < 0 || start >= text.length) return start
        val quote = text.charAt(start)
        if (quote != '\'' && quote != '"') return start
        var i = start + 1
        while (i < text.length) {
          val c = text.charAt(i)
          if (c == quote) {
            // doubled quote => escaped quote, consume both
            if (i + 1 < text.length && text.charAt(i + 1) == quote) i += 2
            else return i + 1
          } else i += 1
        }
        text.length
      }

      def scanNumberLiteral(start: Int): Int = {
        if (start < 0 || start >= text.length) return start
        var i = start
        def isNumChar(ch: Char, pos: Int): Boolean = {
          if (ch >= '0' && ch <= '9') true
          else if (ch == '-' && pos == 0) true // Minus only at the absolute start
          else if (pos > 0) {
            // These characters are only valid if they are NOT the first character
            ch == '.' || ch == 'e' || ch == 'E' || ch == '_'
        }
        else false
        }

        while (i < text.length && isNumChar(text.charAt(i), i - start)) i += 1
        i
      }

      def spanForLiteral(start: Int, lit: Expression): Option[Span] = {
        if (start < 0 || start >= text.length) return None

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

      ast.folder.treeFold(Vector.empty[Span]) {
        // Special handling for Strings: Check if they contain nested Cypher (e.g. APOC)
        case lit: StringLiteral => acc =>
          val maybeInnerAst = parse(lit.value)
          maybeInnerAst match {
            case Some(innerRoot) =>
              // Recursively find spans in the inner query
              val innerSpans = collectSpans(lit.value, innerRoot)

              // Shift inner spans to match the outer string coordinates.
              // We assume standard quoting where content starts at position + 1.
              // Note: This simple shift assumes the string does not rely on complex backslash escaping for the query structure.
              val offsetShift = lit.position.offset + 1
              val shiftedSpans = innerSpans.map(s => Span(s.start + offsetShift, s.endExclusive + offsetShift))

              TraverseChildren(acc ++ shiftedSpans)

            case None =>
              // Standard literal masking
              val start = lit.position.offset
              spanForLiteral(start, lit) match {
                case Some(s) => TraverseChildren(acc :+ s)
                case None    => TraverseChildren(acc)
              }
          }

        // Generic catch-all for other Literals (Numbers, Booleans)
        case lit: Literal => acc =>
          val start = lit.position.offset
          spanForLiteral(start, lit) match {
            case Some(s) => TraverseChildren(acc :+ s)
            case None    => TraverseChildren(acc)
          }
      }
    }

    val spans = collectSpans(cypher, root)

    // Replace from right to left to keep offsets stable.
    val sorted = spans.distinct.sortBy(_.start)(Ordering.Int.reverse)

    val sb = new StringBuilder(cypher)
    sorted.foreach { s =>
      if (s.start >= 0 && s.endExclusive <= sb.length && s.endExclusive > s.start) {
//        val len = s.endExclusive - s.start
        sb.replace(s.start, s.endExclusive, "*" * 4)
      }
    }

    sb.toString
  }
}