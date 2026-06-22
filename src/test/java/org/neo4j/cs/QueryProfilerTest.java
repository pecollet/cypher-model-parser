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
        new CommandLine(profiler).parseArgs("-c", "counts.json", "-q", "MATCH (n) RETURN n", "-v", "25", "-s", "aligned");
        
        assertEquals("counts.json", profiler.countsFile.getName());
        assertEquals(CypherVersion.Cypher25, profiler.cypherVersion);
        assertEquals(getDatabaseFormat("aligned"), profiler.storeFormat);
    }

    @Test
    void testCommandLineParsingWithInvalidValues() {
        QueryProfiler profiler = new QueryProfiler();
        
        // Invalid cypher version
        assertThrows(CommandLine.ParameterException.class, () -> {
            new CommandLine(profiler).parseArgs("-c", "counts.json", "-q", "MATCH (n) RETURN n", "-v", "99");
        });

        // Invalid store format
        assertThrows(CommandLine.ParameterException.class, () -> {
            new CommandLine(profiler).parseArgs("-c", "counts.json", "-q", "MATCH (n) RETURN n", "-s", "invalid_format");
        });
    }
}
