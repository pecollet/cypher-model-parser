package org.neo4j.cs;

import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.parser.CypherParser;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Paths;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ObfuscatorTest {

    @Test
    void shouldObfuscateStrings() throws Exception {
        String query="MATCH (n:Label) WHERE n.name = 'something' RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("MATCH (n:Label)\n" +
                    "WHERE n.name = '****'\n" +
                    "RETURN n\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateNumbers() throws Exception {
        String query="MATCH (n:Label) WHERE n.name = -123 or n.id=123 or n.gid=0 RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("MATCH (n:Label)\n" +
                    "WHERE (n.name = ****\n" +
                    "  OR n.id = ***\n" +
                    "  OR n.gid = *)\n" +
                    "RETURN n\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateDecimals() throws Exception {
        String query="MATCH (n:Label) WHERE n.name = 123.34 RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("MATCH (n:Label)\n" +
                    "WHERE n.name = ******\n" +
                    "RETURN n\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldNotObfuscateBooleans() throws Exception {
        String query="MATCH (n:Label) WHERE n.name = true RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query, "-p");
            });
            assertEquals("MATCH (n:Label)\n" +
                    "WHERE n.name = true\n" +
                    "RETURN n\n", outText);
        });
        assertEquals("", errText);
    }


    @Test
    void shouldNotObfuscateHopCounts() throws Exception {
        String query="MATCH (n:Label)-[:TYPE*2..5]->() RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("MATCH (n:Label)-[:TYPE*2..5]->() RETURN n\n" , outText);
        });
        assertEquals("", errText);
    }


    @Test
    void shouldNotObfuscateApocSubQueries() throws Exception {
        String query="CALL apoc.periodic.iterate('MATCH (p:Person) WHERE p.id = 12 RETURN p', 'SET p:Actor', {batchSize:10000, parallel:true})";
        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("CALL apoc.periodic.iterate('MATCH (p:Person)\n" +
                    "WHERE p.id = **\n" +
                    "RETURN p', 'SET p:Actor', {\n" +
                    "  batchSize: *****,\n" +
                    "  parallel: true\n" +
                    "})\n" , outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldWorkWithServerObfuscatedCypher() throws Exception {
        String query="WITH apoc.cypher.runFirstColumn(******, {auth: $auth, lid: $lid}, ******) as x\n" +
                "UNWIND x as this\n" +
                "RETURN this { .username, .codes } AS this";
        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("WITH apoc.cypher.runFirstColumn(******, {auth: $auth, lid: $lid}, ******) as x\n" +
                    "UNWIND x as this\n" +
                    "RETURN this { .username, .codes } AS this\n" , outText);
        });
        assertTrue(errText.contains("CyperDslParseException"));
    }

    @Test
    void shouldObfuscateParallel() throws Exception {
        String query="CYPHER RUNTIME=PARALLEL MATCH (n:Label) WHERE n.name = 'something' RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("CYPHER RUNTIME=PARALLEL\n" +
                    "MATCH (n:Label)\n" +
                    "WHERE n.name = '****'\n" +
                    "RETURN n\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateExplain() throws Exception {
        String query="EXPLAIN MATCH (n:Label) WHERE n.name = 'something' RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("EXPLAIN\n" +
                    "MATCH (n:Label)\n" +
                    "WHERE n.name = '****'\n" +
                    "RETURN n\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateProfile() throws Exception {
        String query="Profile MATCH (n:Label) WHERE n.name = 'something' RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("PROFILE\n" +
                    "MATCH (n:Label)\n" +
                    "WHERE n.name = '****'\n" +
                    "RETURN n\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateFromFile() throws Exception {
        String inputFile="src/test/resources/query_to_obfuscate.txt";
        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute("-f", inputFile, "-p");
            });
            assertEquals("RETURN ***\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateIntoFile() throws Exception {
        String cypher="  RETURN 123 ";
        String outputFile = "src/test/resources/shouldObfuscateIntoFile_output.txt";
        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(cypher, "-p", "-o", outputFile);
            });
            //read outputFile and assert its content
            assertEquals("RETURN ***\n", Files.readString(Paths.get(outputFile)));
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateBetweenFile() throws Exception {
        String inputFile="src/test/resources/query_to_obfuscate.txt";
        String outputFile = "src/test/resources/shouldObfuscateIntoFile_output.txt";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute("-f", inputFile, "-p", "-o", outputFile);
            });
            //read outputFile and assert its content
            assertEquals("RETURN ***\n", Files.readString(Paths.get(outputFile)));
        });
        assertEquals("", errText);
    }

//    @Test
//    void shouldObfuscateOrderByWithoutNPE() throws Exception {
//        String cypher="""
//                MATCH (c:Content)
//                WITH c WHERE c.name = 3
//                ORDER BY c.publishedDate
//                RETURN c
//                """;
//        String cypher2="""
//                MATCH (c:Content)
//                ORDER BY c.publishedDate
//                RETURN c
//                """;
//
//        String errText = tapSystemErr(() -> {
//            String outText = tapSystemOutNormalized(() -> {
//                new CommandLine(new Obfuscator()).execute(cypher, "-p");
//            });
////            assertEquals("RETURN ***\n", Files.readString(Paths.get(outputFile)));
//        });
//        assertEquals("", errText);
//        errText = tapSystemErr(() -> {
//            String outText = tapSystemOutNormalized(() -> {
//                new CommandLine(new Obfuscator()).execute(cypher2, "-p");
//            });
////            assertEquals("RETURN ***\n", Files.readString(Paths.get(outputFile)));
//        });
//        assertEquals("", errText);
//    }


}
