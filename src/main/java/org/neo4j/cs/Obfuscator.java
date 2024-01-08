package org.neo4j.cs;

import org.neo4j.cypherdsl.core.Literal;
import org.neo4j.cypherdsl.parser.CypherParser;

public class Obfuscator {
    private static final String OBFUSCATED_LITERAL = "********"; // Replace with the desired obfuscated text

    private static String maskValue(Literal input) {
//        System.out.println(input.getContent());
//        System.out.println(input.asString());
        int len = input.getContent().toString().length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append('*');
        }
        return sb.toString();
    }

    public  static void main(String... a) {

        var statement = CypherParser.parse(a[0]);
        var catalog = statement.getCatalog();
        for (var l : catalog.getLiterals()) {
            String masked = maskValue(l);
            System.out.println(l.asString() + " => " + masked);

        }

    }
//    public  static void obfuscateLiterals(String... a) {
//        StringBuilder sb = new StringBuilder();
//        int i = 0;
//        List<String> adjacentCharacters = getAdjacentCharacters(a[0]);
//        //val adjacentCharacters = rawQueryText.sliding(2).toVector
//
//        for (LiteralOffset literalOffset : state.getSensitiveLiteralOffsets()) {
//            int start = literalOffset.getStart();
//
//            if (start >= rawQueryText.length() || start < i) {
//                throw new IllegalStateException("Literal offset out of bounds: " + literalOffset);
//            }
//
//            sb.append(rawQueryText, i, start);
//            sb.append(OBFUSCATED_LITERAL);
//            i = start + literalOffset.getLength().orElse(
//                    literalStringLength(adjacentCharacters, rawQueryText, start)
//            );
//        }
////        for (literalOffset <- state.sensitiveLiteralOffsets) {
////            val start = literalOffset.start
////            if (start >= rawQueryText.length || start < i)
////                throw new IllegalStateException(s"Literal offset out of bounds: $literalOffset.")
////
////            sb.append(rawQueryText.substring(i, start))
////            sb.append(CypherQueryObfuscator.OBFUSCATED_LITERAL)
////            i = start + literalOffset.length.getOrElse(literalStringLength(adjacentCharacters, rawQueryText, start))
////        }
//        if (i < rawQueryText.length)
//            sb.append(rawQueryText.substring(i))
//
//        sb.toString()
//
//    }
//    private static List<String> getAdjacentCharacters(String rawQueryText) {
//        List<String> adjacentCharacters = new ArrayList<>();
//        for (int j = 0; j < rawQueryText.length() - 1; j++) {
//            adjacentCharacters.add(rawQueryText.substring(j, j + 2));
//        }
//        return adjacentCharacters;
//    }
}
