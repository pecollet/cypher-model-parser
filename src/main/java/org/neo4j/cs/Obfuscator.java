package org.neo4j.cs;

import org.neo4j.cs.ast.AstUtils;
import org.neo4j.cs.ast.CypherAstMasker;
import org.neo4j.cs.ast.SimpleCypherExceptionFactory;
import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.cypher.internal.ast.Statement;
import org.neo4j.cypher.internal.parser.ast.AstParser;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.neo4j.cs.QueryFilter.*;


@CommandLine.Command(name = "Obfuscator", mixinStandardHelpOptions = true, versionProvider = ManifestVersionProvider.class,
        description = "Obfuscate literal values in cypher query.")
public class Obfuscator implements Callable<Integer>  {

    static class Dialects extends ArrayList<String> {
        Dialects() {
            super( Arrays.stream(CypherVersion.values())
                    .map(Object::toString)
                    .collect(Collectors.toList())
            );
        }
    }
    // for backwards compatibility with version <2 of this tool
    static final Set<String> CYPHER_DSL_ALIASES = Set.of(
            "NEO4J_5_23", "NEO4J_4", "NEO4J_5", "NEO4J_5_26"
    );
    static class CypherDialectConverter
            implements CommandLine.ITypeConverter<CypherVersion> {
        private static final Map<String, CypherVersion> LOOKUP =
                Arrays.stream(CypherVersion.values())
                        .collect(Collectors.toMap(
                                v -> v.toString(),  // "5", "25", ...
                                v -> v
                        ));
        @Override
        public CypherVersion convert(String value) {
            String normalized = value.trim();
            // try proper CypherVersion values ("5", "25") first
            CypherVersion v = LOOKUP.get(normalized);
            if (v != null) return v;

            // allow cypher-dsl dialects (for backwards compatibility), but always map to 5
            if (CYPHER_DSL_ALIASES.contains(normalized)) {
                return CypherVersion.Cypher5;
            }
            // Reject everything else
            throw new CommandLine.TypeConversionException(
                    "Invalid dialect: " + value +
                            ". Expected one of: " + LOOKUP.keySet() + " or, for backwards compatibility, one of : " + CYPHER_DSL_ALIASES + " (which all map to '5')"
            );
        }
    }

//    private static final String OBFUSCATED_STRING = "****"; // Replace with the desired obfuscated text

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    InputCypherQuery input; // require exactly one: CYPHER or -f FILE

    static class InputCypherQuery {
        @CommandLine.Parameters(description = "The query to obfuscate", paramLabel = "CYPHER")
        String query;

        @CommandLine.Option(names = {"-f", "--file"}, paramLabel = "CYPHER_FILE",
                description = "Read CYPHER query from this file")
        Path file;
    }

    @CommandLine.Option(names = { "-o", "--output" }, description = "Output file generated, containing the obfuscated query.")
    private Path outputFile;

    @CommandLine.Option(names = { "-d", "--dialect" }, paramLabel = "dialect",  completionCandidates = Dialects.class,
            defaultValue = "25", converter = CypherDialectConverter.class, description = "The cypher dialect, one of : [${COMPLETION-CANDIDATES}]. Defaults to 25.")
    private CypherVersion dialect;

    @CommandLine.Option(names = { "-p", "--pretty" }, description = "Always pretty print the resulting cypher, even if no obfuscation took place. Obfuscated cypher will be pretty printed in any case.")
    private boolean pretty;

    private String prefix="";


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

        String q = (input.query != null)
                ? input.query
                : Files.readString(input.file).trim();

        String result=q; //default to original query if parsing fails

        try {
            var cleanQuery = cleanupQuery(q);
            AstParser parser = AstUtils.getCypherParser(cleanQuery, dialect, new SimpleCypherExceptionFactory());
            Statement statement = parser.singleStatement();
            result = CypherAstMasker.maskLiterals(cleanQuery, statement, AstUtils.getNestedParser(dialect));

        } catch (RuntimeException e){
            System.err.println("### [RuntimeException] " + e + " : " +e.getCause());
        } catch (Exception e) {
            System.err.println("### [Exception] " + e + " : " +q );
        }

        String outputString = prefix+result;
        System.out.println(outputString);
        if (outputFile != null) {
            Files.writeString(outputFile, outputString+'\n');
        }
        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new Obfuscator()).execute(args);
        System.exit(exitCode);
    }
}