package org.neo4j.cs.ast;

import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.cypher.internal.parser.AstParserFactory$;
import org.neo4j.cypher.internal.parser.ast.AstParser;
import org.neo4j.cypher.internal.util.CypherExceptionFactory;
import scala.Option;
import scala.collection.immutable.Seq;

public class AstUtils {
    public static AstParser getCypherParser(String cypher, CypherVersion version, CypherExceptionFactory exceptionFactory) {
        var factory = AstParserFactory$.MODULE$.apply(version);
        Option notificationLogger = Option.empty();
        Seq semanticFeatures = scala.collection.immutable.Seq$.MODULE$.empty();

        return factory.apply(cypher, exceptionFactory, notificationLogger, semanticFeatures);
    }
}
