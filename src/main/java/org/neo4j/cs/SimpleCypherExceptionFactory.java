package org.neo4j.cs;

import org.neo4j.cypher.internal.util.CypherExceptionFactory;
import org.neo4j.cypher.internal.util.InputPosition;
import org.neo4j.exceptions.SyntaxException;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;

public final class SimpleCypherExceptionFactory implements CypherExceptionFactory {
    @Override
    public RuntimeException syntaxException(
            ErrorGqlStatusObject gqlStatus,
            String message,
            InputPosition pos
    ) {
        // Neo4j's SyntaxException is public and safe to use
        return new SyntaxException(gqlStatus, message);
    }

    @Override
    public RuntimeException internalError(String message, InputPosition pos) {
        return new RuntimeException(
                "Internal Cypher parser error at " + pos + ": " + message
        );
    }

    @Override
    public RuntimeException insertExistsInOtherLanguageVersion(
            String unsupportedVersion,
            String supportedVersion,
            SyntaxException ex
    ) {
        return new RuntimeException(
                "Cypher " + unsupportedVersion +
                        " syntax used, but supported version is " + supportedVersion,
                ex
        );
    }
}
