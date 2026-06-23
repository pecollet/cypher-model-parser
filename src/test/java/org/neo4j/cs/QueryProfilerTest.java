package org.neo4j.cs;

import org.junit.jupiter.api.Test;
import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder.DatabaseFormat;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

public class QueryProfilerTest {
    private static DatabaseFormat getDatabaseFormat(String formatName) {
        if ("block".equalsIgnoreCase(formatName)) {
            try {
                return (DatabaseFormat) Class.forName("org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder$DatabaseFormat$Block$")
                    .getField("MODULE$").get(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if ("aligned".equalsIgnoreCase(formatName)) {
            try {
                return (DatabaseFormat) Class.forName("org.neo4j.cypher.internal.compiler.planner.StatisticsBackedLogicalPlanningConfigurationBuilder$DatabaseFormat$Aligned$")
                    .getField("MODULE$").get(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalArgumentException("Unknown store format: " + formatName);
        }
    }

    @Test
    void testDatabaseFormatValues() throws Exception {
        DatabaseFormat block = getDatabaseFormat("block");
        DatabaseFormat aligned = getDatabaseFormat("aligned");
        assertNotNull(block);
        assertNotNull(aligned);
        assertEquals("Block", block.toString());
        assertEquals("Aligned", aligned.toString());
    }

    @Test
    void testCommandLineParsingWithDefaults() {
        QueryProfiler profiler = new QueryProfiler();
        new CommandLine(profiler).parseArgs("-c", "counts.json", "-q", "MATCH (n) RETURN n");
        
        assertEquals("counts.json", profiler.countsFile.getName());
        assertEquals(CypherVersion.Cypher5, profiler.cypherVersion);
        assertEquals(getDatabaseFormat("block"), profiler.storeFormat);
    }

    @Test
    void testCommandLineParsingWithCustomValues() {
        QueryProfiler profiler = new QueryProfiler();
        new CommandLine(profiler).parseArgs("-c", "counts.json", "-q", "MATCH (n) RETURN n", "-d", "25", "-s", "aligned");
        
        assertEquals("counts.json", profiler.countsFile.getName());
        assertEquals(CypherVersion.Cypher25, profiler.cypherVersion);
        assertEquals(getDatabaseFormat("aligned"), profiler.storeFormat);
    }

    @Test
    void testCommandLineParsingWithOutputFile() {
        QueryProfiler profiler = new QueryProfiler();
        new CommandLine(profiler).parseArgs("-c", "counts.json", "-q", "MATCH (n) RETURN n", "-o", "output.txt");

        assertEquals("counts.json", profiler.countsFile.getName());
        assertEquals(java.nio.file.Path.of("output.txt"), profiler.outputFile);
    }

    @Test
    void testCommandLineParsingWithInvalidValues() {
        QueryProfiler profiler = new QueryProfiler();
        
        // Invalid cypher version
        assertThrows(CommandLine.ParameterException.class, () -> {
            new CommandLine(profiler).parseArgs("-c", "counts.json", "-q", "MATCH (n) RETURN n", "-d", "99");
        });

        // Invalid store format
        assertThrows(CommandLine.ParameterException.class, () -> {
            new CommandLine(profiler).parseArgs("-c", "counts.json", "-q", "MATCH (n) RETURN n", "-s", "invalid_format");
        });
    }

    @Test
    void testActualOutput() throws Exception {
        String countsFilePath = "src/test/resources/direct_graphcounts.json";
        String query = "MATCH (p:Part)-[:BELONGS_TO]->(pr:Product) RETURN p, pr";

        String outText = com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized(() -> {
            int exitCode = new CommandLine(new QueryProfiler()).execute(
                "-c", countsFilePath,
                "-q", query,
                "-d", "5",
                "-s", "block"
            );
            assertEquals(0, exitCode);
        });

        System.out.println("--- CAPTURED PLAN OUTPUT (Cypher 5, Block) ---");
        System.out.println(outText);
        System.out.println("----------------------------------------------");

        // Basic assertions on the table structure and content
        assertTrue(outText.contains("Operator"));
        assertTrue(outText.contains("Details"));
        assertTrue(outText.contains("ProduceResults"));
        assertTrue(outText.contains("Filter"));
        assertTrue(outText.contains("ExpandAll"));
        assertTrue(outText.contains("NodeByLabelScan"));
    }

    @Test
    void testActualOutputWithOutputFile() throws Exception {
        String[] queries = {
                "MATCH (p:Part)-[:BELONGS_TO]->(pr:Product) RETURN p, pr", 
                "MATCH (o:Organisation) RETURN o"
        };
        String[] countsFilePaths = {
                "src/test/resources/direct_graphcounts.json", 
                "src/test/resources/adminreport_graphcounts.json"};
        for (int i = 0; i < countsFilePaths.length; i++) {
            var countsFilePath = countsFilePaths[i];
            var query = queries[i];
            java.nio.file.Path tempOutputFile = java.nio.file.Files.createTempFile("query-profiler-test", ".txt");
            try {
                String outText = com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized(() -> {
                    int exitCode = new CommandLine(new QueryProfiler()).execute(
                            "-c", countsFilePath,
                            "-q", query,
                            "-d", "5",
                            "-s", "block",
                            "-o", tempOutputFile.toAbsolutePath().toString()
                    );
                    assertEquals(0, exitCode);
                });

                // Assert output file was written and contains the same contents as stdout
                assertTrue(java.nio.file.Files.exists(tempOutputFile));
                String fileContent = java.nio.file.Files.readString(tempOutputFile);
                assertEquals(outText, fileContent);
            } finally {
                java.nio.file.Files.deleteIfExists(tempOutputFile);
            }
        }
    }

}
