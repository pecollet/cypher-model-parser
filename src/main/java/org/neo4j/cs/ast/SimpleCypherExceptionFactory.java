package org.neo4j.cs.ast;

import org.neo4j.cypher.internal.util.CypherExceptionFactory;
import org.neo4j.cypher.internal.util.InputPosition;
import org.neo4j.exceptions.SyntaxException;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;

public final class SimpleCypherExceptionFactory implements CypherExceptionFactory {

    @Override
    public RuntimeException syntaxException(
            ErrorGqlStatusObject gqlStatusObject,
            String message,
            InputPosition pos
    ) {
        // Neo4j's SyntaxException is public and safe to use
        return new SyntaxException(gqlStatusObject, message);
    }
    @Override
    public RuntimeException syntaxException(
            ErrorGqlStatusObject gqlStatusObject,
            InputPosition pos,
            Throwable cause
    ) {
        return new RuntimeException("Cypher syntax error at " + pos, cause);
    }

//    @Override
//    public RuntimeException internalError(String message, InputPosition pos) {
//        return new RuntimeException(
//                "Internal Cypher parser error at " + pos + ": " + message
//        );
//    }

//    @Override
//    public RuntimeException insertExistsInOtherLanguageVersion(
//            int unsupportedVersion,
//            int supportedVersion,
//            SyntaxException ex
//    ) {
//        return new RuntimeException(
//                "Cypher " + unsupportedVersion +
//                        " syntax used, but supported version is " + supportedVersion,
//                ex
//        );
//    }
}
