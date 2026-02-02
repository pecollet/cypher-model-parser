package org.neo4j.cs.ast;

import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.cypher.internal.parser.AstParserFactory$;
import org.neo4j.cypher.internal.parser.ast.AstParser;
import org.neo4j.cypher.internal.util.ASTNode;
import org.neo4j.cypher.internal.util.CypherExceptionFactory;
//import org.neo4j.cypherdsl.core.Statement;
import scala.Function1;
import scala.Option;
import scala.collection.immutable.Seq;
import scala.runtime.AbstractFunction1;

public class AstUtils {
    public static AstParser getCypherParser(String cypher, CypherVersion version, CypherExceptionFactory exceptionFactory) {
        var factory = AstParserFactory$.MODULE$.apply(version);
        Option notificationLogger = Option.empty();
        Seq semanticFeatures = scala.collection.immutable.Seq$.MODULE$.empty();

        return factory.apply(cypher, exceptionFactory, notificationLogger, semanticFeatures);
    }

    public static Function1<String, Option<ASTNode>> getNestedParser(CypherVersion version) {
        return new AbstractFunction1<String, Option<ASTNode>>() {
            @Override
            public Option<ASTNode> apply(String text) {
                try {
                    // Attempt to parse the string literal as a Cypher query
                    AstParser innerParser = AstUtils.getCypherParser(text, version, new SimpleCypherExceptionFactory());
                    org.neo4j.cypher.internal.ast.Statement innerStatement = innerParser.singleStatement();

                    // Wrap the result in a Scala Option (Some)
                    return Option.apply(innerStatement);
                } catch (Exception e) {
                    // If parsing fails (it's just a normal string), return Scala Option (None)
                    return Option.empty();
                }
            }
        };
    }
}
