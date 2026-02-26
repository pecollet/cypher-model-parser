package org.neo4j.cs;

import org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder;
import org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder$;
import org.neo4j.cypher.graphcounts.GraphCountsJson;
import picocli.CommandLine;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;


/**
 * Command line client to profile Cypher queries against specific graph counts.
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
    private File countsFile;

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

    @Override
    public Integer call() throws Exception {
        // 1. Resolve the query string
        String cypher = (input.queryText != null)
                ? input.queryText
                : Files.readString(input.queryFile).trim();

        try {
            // 2. Parse JSON and instantiate Planner (based on your Scala snippet)
            // Note: Accessing .results().get(0)... is the Java equivalent of .head
            var graphCountData = GraphCountsJson.parseAsGraphCountsJson(countsFile);
            var rowData = graphCountData.results().head().data().head().row().data();

            var builder = StatisticsBackedLogicalPlanningConfigurationBuilder$.MODULE$.newBuilder();
            var planner = builder
                    .processGraphCounts(rowData)
                    .build();

            // 3. Generate the plan
            var plan = planner.plan(cypher);

            // 4. Output to stdout
            System.out.println(plan);

            return 0;
        } catch (Exception e) {
            System.err.println("### [Error] Failed to generate plan: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new QueryProfiler()).execute(args);
        System.exit(exitCode);
    }
}
