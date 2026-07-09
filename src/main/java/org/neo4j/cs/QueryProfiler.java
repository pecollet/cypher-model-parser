package org.neo4j.cs;


import org.neo4j.cypher.graphcounts.GraphCountData;
import org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder;
import org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder.DatabaseFormat;
import org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder.DatabaseFormat.*;
import org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder$;
import org.neo4j.cypher.graphcounts.GraphCountsJson;
import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.cypher.internal.logical.plans.LogicalPlan;
import org.neo4j.internal.schema.ConstraintType;
import org.neo4j.exceptions.SyntaxException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import picocli.CommandLine;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;


/**
 * Command line client to profile Cypher queries against specific graph counts.
 * curl -H "Authorization:Basic bmVvNGo6Y2hhbmdlbWU="  -H accept:application/json -H content-type:application/json -d '{"statements":[{"statement":"CALL db.stats.retrieve(\"GRAPH COUNTS\")"}]}' http://localhost:7474/db/neo4j/tx/commit > graphCounts.json
 *
 */
@CommandLine.Command(
        name = "QueryProfiler",
        mixinStandardHelpOptions = true,
        description = "Plan and profile a Cypher query using graph count data."
)
public class QueryProfiler implements Callable<Integer> {

    @CommandLine.Option(
            names = {"-c", "--counts"},
            required = true,
            paramLabel = "JSON_FILE",
            description = "Path to the JSON file containing graph counts."
    )
    File countsFile;

    @CommandLine.Option(
            names = {"-d", "--dialect"},
            defaultValue = "5",
            converter = CypherVersionConverter.class,
            description = "Cypher version to use for planning. Default is 5."
    )
    CypherVersion cypherVersion;

    @CommandLine.Option(
            names = {"-s", "--store-format"},
            defaultValue = "block",
            converter = StoreFormatConverter.class,
            description = "Store format to use. Default is block."
    )
    DatabaseFormat storeFormat;

    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "Output file generated, containing the formatted plan. If absent the output is sent to stdout."
    )
    Path outputFile;

    @CommandLine.Option(
            names = {"-p", "--plugins-dir"},
            description = "Directory containing external plugin JARs (like APOC or custom procedures) to scan and register."
    )
    File pluginsDir;

    @CommandLine.Option(
            names = {"--obfuscate"},
            description = "Obfuscate literal values in the query plan details."
    )
    boolean obfuscate = false;

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "1")
    InputQuery input;

    static class InputQuery {
        @CommandLine.Option(
                names = {"-q", "--query"},
                paramLabel = "CYPHER",
                description = "The Cypher query string to plan."
        )
        String queryText;

        @CommandLine.Option(
                names = {"-f", "--file"},
                paramLabel = "FILE",
                description = "File containing the Cypher query."
        )
        Path queryFile;
    }

    static class CypherVersionConverter implements CommandLine.ITypeConverter<CypherVersion> {
        private static final java.util.Map<String, CypherVersion> LOOKUP =
                java.util.Arrays.stream(CypherVersion.values())
                        .collect(java.util.stream.Collectors.toMap(
                                v -> v.toString(),
                                v -> v
                        ));
        @Override
        public CypherVersion convert(String value) {
            String normalized = value.trim();
            CypherVersion v = LOOKUP.get(normalized);
            if (v != null) return v;
            throw new CommandLine.TypeConversionException(
                    "Invalid Cypher version: " + value + ". Expected one of: " + LOOKUP.keySet()
            );
        }
    }

    static class StoreFormatConverter implements CommandLine.ITypeConverter<DatabaseFormat> {
        @Override
        public DatabaseFormat convert(String value) {
            String normalized = value.trim().toLowerCase();
            if ("block".equals(normalized)) {
                try {
                    return (DatabaseFormat) Class.forName("org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder$DatabaseFormat$Block$")
                            .getField("MODULE$").get(null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if ("aligned".equals(normalized)) {
                try {
                    return (DatabaseFormat) Class.forName("org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder$DatabaseFormat$Aligned$")
                            .getField("MODULE$").get(null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new CommandLine.TypeConversionException(
                        "Invalid store format: " + value + ". Expected one of: [block, aligned]"
                );
            }
        }
    }

    @Override
    public Integer call() throws Exception {
        // 1. Resolve the query string
        String cypher = (input.queryText != null)
                ? input.queryText
                : Files.readString(input.queryFile).trim();

        try {
            // 2. Parse JSON and instantiate Planner (based on your Scala snippet)
            // Note: Accessing .results().get(0)... is the Java equivalent of .head
            GraphCountData rowData;
            try { //try reading the HTTP response format
                var graphCountData = GraphCountsJson.parseAsGraphCountsJson(countsFile);
                rowData = graphCountData.results().head().data().head().row().data();
            } catch (Exception e) {
                //try reading the graph count map 
                try {
                    rowData = GraphCountsJson.parseAsGraphCountDataFromCypherMap(countsFile);
                } catch (Exception e2) {
                    //Rows format (as found in admin report file) 
                    rowData = GraphCountsJson.parseAsGraphCountRowsFromFile(countsFile);
                }
            }

            var builder = StatisticsBackedLogicalPlanningConfigurationBuilder$.MODULE$.newBuilder();

            var scanner = new ProcedureAndFunctionScanner();
            scanner.scanClassPath();
            if (pluginsDir != null) {
                scanner.scanPluginsDirectory(pluginsDir);
            }
            for (var proc : scanner.getProcedures()) {
                builder = builder.addProcedure(proc);
            }
            for (var func : scanner.getFunctions()) {
                builder = builder.addFunction(func);
            }

            rowData = filterUnsupportedConstraints(rowData);
            rowData = filterUnsupportedIndexes(rowData);

            builder = builder
                    .processGraphCounts(rowData)
                    .setDatabaseFormat(storeFormat);

            var planner = builder.build();

            // 3. Generate the plan
            LogicalPlan plan = null;
            int maxRetries = 20;
            int retries = 0;
            while (retries < maxRetries) {
                try {
                    plan = planner.plan(cypherVersion, cypher);
                    break;
                } catch (IllegalStateException e) {
                    String message = e.getMessage();
                    if (message != null && message.contains("No cardinality set for label")) {
                        Pattern labelPattern = Pattern.compile("No cardinality set for label ([^.]+)\\. Please specify using");
                        Matcher matcher = labelPattern.matcher(message);
                        if (matcher.find()) {
                            String label = matcher.group(1);
                            builder = builder.setLabelCardinality(label, 0);
                            planner = builder.build();
                            retries++;
                            continue;
                        }
                    }
                    throw e;
                } catch (SyntaxException e) {
                    String message = e.getMessage();
                    if (message != null && message.contains("Invalid input 'CYPHER'")) {
                        Pattern cypherPrefixPattern = Pattern.compile("^(?i)CYPHER\\s+(5|25)\\s*");
                        Matcher matcher = cypherPrefixPattern.matcher(cypher);
                        if (matcher.find()) {
                            String versionStr = matcher.group(1);
                            if ("5".equals(versionStr)) {
                                cypherVersion = CypherVersion.Cypher5;
                            } else if ("25".equals(versionStr)) {
                                cypherVersion = CypherVersion.Cypher25;
                            }
                            cypher = cypher.substring(matcher.end());
                            retries++;
                            continue;
                        }
                    }
                    throw e;
                }
            }
            if (plan == null) {
                throw new RuntimeException("Failed to generate plan after " + retries + " retries.");
            }

            if (obfuscate) {
                plan = org.neo4j.cs.ast.CypherPlanMasker.maskPlan(plan);
            }

            // 4. Output formatted table plan to stdout
            var formatter = new TablePlanFormatter();
            String outputString = formatter.formatPlan(plan);
            System.out.println(outputString);
            if (outputFile != null) {
                Files.writeString(outputFile, outputString + '\n');
            }

            return 0;
//        } catch (Exception e) { //No cardinality set for label Resolved_Entity
//            planner.addLabel()       
//        } catch (Exception e) { //Invalid input 'CYPHER'
//            addLabel()
        } catch (Exception e) {
            System.err.println("### [Error] Failed to generate plan: " + e.getMessage() + e);
//            e.printStackTrace();
            return 1;
        }
    }

    static GraphCountData filterUnsupportedConstraints(GraphCountData data) {
        java.util.List<org.neo4j.cypher.graphcounts.Constraint> constraints =
                scala.jdk.CollectionConverters.SeqHasAsJava(data.constraints()).asJava();

        java.util.List<org.neo4j.cypher.graphcounts.Constraint> filtered = constraints.stream()
                .filter(c -> {
                    ConstraintType t = c.type();
                    return t == ConstraintType.UNIQUE ||
                           t == ConstraintType.EXISTS ||
                           t == ConstraintType.UNIQUE_EXISTS ||
                           t == ConstraintType.NODE_LABEL_EXISTENCE ||
                           t == ConstraintType.RELATIONSHIP_ENDPOINT_LABEL;
                })
                .collect(Collectors.toList());

        scala.collection.immutable.Seq<org.neo4j.cypher.graphcounts.Constraint> scalaConstraints =
                scala.collection.immutable.Seq$.MODULE$.from(
                        scala.jdk.CollectionConverters.IterableHasAsScala(filtered).asScala()
                );

        return data.copy(
                scalaConstraints,
                data.indexes(),
                data.nodes(),
                data.relationships()
        );
    }

    static GraphCountData filterUnsupportedIndexes(GraphCountData data) {
        java.util.List<org.neo4j.cypher.graphcounts.Index> indexes =
                scala.jdk.CollectionConverters.SeqHasAsJava(data.indexes()).asJava();

        java.util.List<org.neo4j.cypher.graphcounts.Index> filtered = indexes.stream()
                .filter(idx -> {
                    org.neo4j.internal.schema.IndexType t = idx.indexType();
                    return t != org.neo4j.internal.schema.IndexType.VECTOR;
                })
                .collect(Collectors.toList());

        scala.collection.immutable.Seq<org.neo4j.cypher.graphcounts.Index> scalaIndexes =
                scala.collection.immutable.Seq$.MODULE$.from(
                        scala.jdk.CollectionConverters.IterableHasAsScala(filtered).asScala()
                );

        return data.copy(
                data.constraints(),
                scalaIndexes,
                data.nodes(),
                data.relationships()
        );
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new QueryProfiler()).execute(args);
        System.exit(exitCode);
    }
}
