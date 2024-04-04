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

    @CommandLine.Option(names = { "-p", "--pretty" }, description = "Always pretty print the resulting cypher, even if no obfuscation took place. Obfuscated cypher will be pretty printed in any case.")
    private boolean pretty;

    private Options options;
    private Configuration rendererConfig;

    private boolean hasBeenMasked=false;

    Function<Expression, Expression> maskLiteral = e -> {
        if (e instanceof Literal) {
            if (e instanceof StringLiteral) {
                Statement subStatement;
                try { //if string parses as a valid cypher statement, recurse
                    subStatement = CypherParser.parse(((StringLiteral) e).getContent().toString(), this.options);
                    var renderer = Renderer.getRenderer(this.rendererConfig);
                    return Cypher.literalOf(renderer.render(subStatement));
                } catch (Exception ex) {
                    //otherwise, mask the string (general case)
                    this.hasBeenMasked=true;
                    return Cypher.literalOf(OBFUSCATED_STRING);
                }
            } else if (e instanceof NumberLiteral) {
                //replace by nines (so it's still a valid number), keeping the same number of digits
                String masked = "9".repeat(((NumberLiteral) e).asString().length());
                long allNines = Long.parseLong(masked);
                this.hasBeenMasked=true;
                return Cypher.literalOf(allNines);
            }
            //booleans are voluntarily left out as they're unlikely to hold sensitive information
        }
        return e;
    };

    @Override
    public Integer call() throws Exception {
        String result=query;
        this.rendererConfig = Configuration.newConfig()
                .alwaysEscapeNames(false)
                .withPrettyPrint(true)
                .withDialect(dialect).build();
        this.options = Options.newOptions()
                .withCallback(ExpressionCreatedEventType.ON_NEW_LITERAL,
                        Expression.class,
                        maskLiteral )
                .build();

        try {
            var statement = CypherParser.parse(query.replaceAll("<br>", "\n"), this.options);

            var renderer = Renderer.getRenderer(this.rendererConfig);

            //all numbers are made of 9s. Replace by * in the final string, except if they're part of a word.
            result = renderer.render(statement).replaceAll("(?<![A-Za-z0-8_]9*)9", "*");

        } catch (CyperDslParseException e) {
            System.err.println("### [CyperDslParseException] " + e + " : " +query );
        } catch (UnsupportedCypherException e) {
            System.err.println("### [UnsupportedCypherException] " + e + " : " +query );
        } catch (Exception e) {
            System.err.println("### [Exception] " + e + " : " +query );
        }

        //if no masking took place, we way want to just return the original query as-is, to avoid unnecessary formatting changes
        if (hasBeenMasked || pretty) {
            System.out.println(result);
            return 0;
        } else {
            System.out.println(query);
            return 1;
        }

    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new Obfuscator()).execute(args);
        System.exit(exitCode);
    }

}
