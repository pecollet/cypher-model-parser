package org.neo4j.cs;

import org.neo4j.cypherdsl.core.*;
import org.neo4j.cypherdsl.core.renderer.Configuration;
import org.neo4j.cypherdsl.core.renderer.Dialect;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.neo4j.cypherdsl.parser.*;
import picocli.CommandLine;

import java.util.concurrent.Callable;
import java.util.function.Function;


@CommandLine.Command(name = "Obfuscator", mixinStandardHelpOptions = true, versionProvider = ManifestVersionProvider.class,
        description = "Obfuscate literal values in cypher query.")
public class Obfuscator implements Callable<Integer>  {

    private static final String OBFUSCATED_STRING = "****"; // Replace with the desired obfuscated text


    @CommandLine.Parameters(index = "0", description = "The query to obfuscate")
    private String query;

    @CommandLine.Option(names = { "-d", "--dialect" }, paramLabel = "dialect", defaultValue = "NEO4J_5", description = "The cypher dialect : [NEO4J_5|NEO4J_4]. Defaults to NEO4J_5.")
    private Dialect dialect;


    Function<Expression, Expression> maskLiteral = e -> {
        if (e instanceof Literal) {
            if (e instanceof StringLiteral) {
                return Cypher.literalOf(OBFUSCATED_STRING);
            } else if (e instanceof NumberLiteral) {
                //replace by nines (so it's still a valid number), keeping the same number of digits
                long value = ((NumberLiteral)e).getContent().longValue();
                int numDigits = (int) Math.log10(value) + 1;

                // Calculate the result with all 9s
                long allNines = (long) (Math.pow(10, numDigits) - 1);
                return Cypher.literalOf(allNines);
            }
        }
        return e;
    };

    @Override
    public Integer call() throws Exception {
        String result=query;
        Options options = Options.newOptions()
                .withCallback(ExpressionCreatedEventType.ON_NEW_LITERAL,
                        Expression.class,
                        maskLiteral )
                .build();
        try {
            var statement = CypherParser.parse(query.replaceAll("<br>", "\n"), options);
            //all numbers are made of 9s. Replace by * in the final string, except if they're part of a word.

            var rendererConfig = Configuration.newConfig()
                    .alwaysEscapeNames(false)
                    .withPrettyPrint(true)
                    .withDialect(dialect).build();
            var renderer = Renderer.getRenderer(rendererConfig);
            result = renderer.render(statement).replaceAll("(?<![A-Za-z0-8_]9*)9", "*");

        } catch (CyperDslParseException e) {
//            System.err.println("### [Exception] " + e + " : " +query );
        } catch (UnsupportedCypherException e) {
//            System.err.println("### [Exception] " + e + " : " +query );
        } catch (Exception e) {
            System.err.println("### [Exception] " + e + " : " +query );
        }


        System.out.println(result);
        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new Obfuscator()).execute(args);
        System.exit(exitCode);
    }

}
