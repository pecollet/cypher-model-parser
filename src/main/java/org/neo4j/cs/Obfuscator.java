package org.neo4j.cs;

import org.neo4j.cypherdsl.core.*;
import org.neo4j.cypherdsl.core.renderer.Configuration;
import org.neo4j.cypherdsl.core.renderer.Dialect;
import org.neo4j.cypherdsl.core.renderer.Renderer;
import org.neo4j.cypherdsl.parser.*;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.neo4j.cs.QueryFilter.*;


@CommandLine.Command(name = "Obfuscator", mixinStandardHelpOptions = true, versionProvider = ManifestVersionProvider.class,
        description = "Obfuscate literal values in cypher query.")
public class Obfuscator implements Callable<Integer>  {

    static class Dialects extends ArrayList<String> {
        Dialects() {
            super( Arrays.stream(Dialect.values())
                    .map(Dialect::name)
                    .collect(Collectors.toList())
            );
        }
    }
    private static final String OBFUSCATED_STRING = "****"; // Replace with the desired obfuscated text

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    InputCypherQuery input; // require exactly one: CYPHER or -f FILE

    static class InputCypherQuery {
        @CommandLine.Parameters(description = "The query to obfuscate", paramLabel = "CYPHER")
        String query;

        @CommandLine.Option(names = {"-f", "--file"}, paramLabel = "CYPHER_FILE",
                description = "Read CYPHER query from this file")
        Path file;
    }

//    @CommandLine.Parameters(description = "The query to obfuscate")
//    private String query;

//    @CommandLine.Option(names = { "-q", "--query" }, description = "The query to obfuscate.")
//    private String query;
//
//    @CommandLine.Option(names = { "-f", "--file" }, description = "File containing the query to obfuscate.")
//    private Path file;

    @CommandLine.Option(names = { "-o", "--output" }, description = "Output file generated, containing the obfuscated query.")
    private Path outputFile;

    @CommandLine.Option(names = { "-d", "--dialect" }, paramLabel = "dialect",  completionCandidates = Dialects.class, defaultValue = "NEO4J_5_23", description = "The cypher dialect, one of : [${COMPLETION-CANDIDATES}]. Defaults to NEO4J_5_23.")
    private Dialect dialect;

    @CommandLine.Option(names = { "-p", "--pretty" }, description = "Always pretty print the resulting cypher, even if no obfuscation took place. Obfuscated cypher will be pretty printed in any case.")
    private boolean pretty;

    private Options options;
    private Configuration rendererConfig;

    private boolean hasBeenMasked=false;

    private String prefix="";

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


    String cleanupQuery(String query) {
        query = query.replaceAll("<br>", "\n").trim();
        //deal with EXPLAIN/PROFILE
        Pattern pattern = Pattern.compile("(?is)"+ "^(PROFILE|EXPLAIN)\\s(.*)$");
        Matcher matcher = pattern.matcher(query);
        if (matcher.find()) {
            this.prefix+=matcher.group(1).trim().toUpperCase() +"\n";
            query=matcher.group(2);
        }
        //also with CYPHER prefix
        Pattern pattern2 = Pattern.compile("(?i)^("+ prefixRegex_CYPHER
                + prefixRegex_PLANNER
                + prefixRegex_CCPLANNER
                + prefixRegex_UPDATESTRATEGY
                + prefixRegex_RUNTIME
                + prefixRegex_EXPRESSIONENGINE
                + prefixRegex_OPERATORENGINE
                + prefixRegex_IPF
                + prefixRegex_REPLAN+")(.*)$");
        Matcher matcher2 = pattern2.matcher(query);
        if (matcher2.find()) {
            this.prefix+=matcher2.group(1).trim().toUpperCase()  +"\n";
            query=matcher2.group(2);
        }
        return query;
    }

    @Override
    public Integer call() throws Exception {
//        if (query == null && file == null) {
//            System.err.println("One of the options -q or -f must be specified.");
//            System.exit(8);
//        }
//        if (query != null && file != null) {
//            System.err.println("Only one of the options -q or -f can be specified.");
//            System.exit(9);
//        }
        String q = (input.query != null)
                ? input.query
                : Files.readString(input.file).trim();

        String result=q;
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
            var statement = CypherParser.parse(cleanupQuery(q), this.options);

            var renderer = Renderer.getRenderer(this.rendererConfig);

            //all numbers are made of 9s. Replace by * in the final string, except if they're part of a word.
            result = renderer.render(statement).replaceAll("(?<![A-Za-z0-8_]9*)9", "*");

        } catch (CyperDslParseException e) {
            System.err.println("### [CyperDslParseException] " + e + " : " +q );
        } catch (UnsupportedCypherException e) {
            System.err.println("### [UnsupportedCypherException] " + e + " : " + q);
        } catch (RuntimeException e){
            System.err.println("### [RuntimeException] " + e + " : " +e.getCause());
        } catch (Exception e) {
            System.err.println("### [Exception] " + e + " : " +q );
        }

        //if no masking took place, we way want to just return the original query as-is, to avoid unnecessary formatting changes
        String outputString;
        int returnCode;
        if (hasBeenMasked || pretty) {
            outputString = prefix+result;
            returnCode=0;
        } else {
            outputString = prefix+q;
            returnCode=1;
        }
        System.out.println(outputString);
        if (outputFile != null) {
            Files.writeString(outputFile, outputString+'\n');
        }
        return returnCode;

    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new Obfuscator()).execute(args);
        System.exit(exitCode);
    }

}
