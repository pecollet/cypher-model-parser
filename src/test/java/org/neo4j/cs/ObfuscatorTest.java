package org.neo4j.cs;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
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
        String query="MATCH (n:Label {name: 'bob'}) WHERE n.name = 'something' RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("MATCH (n:Label {name: ****}) WHERE n.name = **** RETURN n\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateLists() throws Exception {
        String query="MATCH (n:Label) WHERE n.name = ['something', 123, true] RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("MATCH (n:Label) WHERE n.name = [****, ****, true] RETURN n\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateMaps() throws Exception {
        String query="MATCH (n:Label) WHERE n.name = {a: 'bc', b: [1, {x: 'hh'}]} RETURN 12 as u";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("MATCH (n:Label) WHERE n.name = {a: ****, b: [****, {x: ****}]} RETURN **** as u\n", outText);
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
            assertEquals("MATCH (n:Label) WHERE n.name = **** or n.id=**** or n.gid=**** RETURN n\n", outText);
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
            assertEquals("MATCH (n:Label) WHERE n.name = **** RETURN n\n", outText);
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
            assertEquals(query+"\n", outText);
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
    void shouldObfuscateSkipLimit() throws Exception {
        String query="MATCH (n:Label) RETURN n SKIP 10 LIMIT 100";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("MATCH (n:Label) RETURN n SKIP **** LIMIT ****\n" , outText);
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
            assertEquals("CALL apoc.periodic.iterate('MATCH (p:Person) " +
                    "WHERE p.id = **** RETURN p', 'SET p:Actor', " +
                    "{batchSize:****, parallel:true})\n" , outText);
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
        assertTrue(errText.contains("SyntaxException"));
    }

    @Test
    void shouldObfuscateParallel() throws Exception {
        String query="CYPHER RUNTIME=PARALLEL MATCH (n:Label) WHERE n.name = 'something' RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("CYPHER RUNTIME=PARALLEL\nMATCH (n:Label) WHERE n.name = **** RETURN n\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateCypher25Prefix() throws Exception {
        String query="CYPHER 25<br>MATCH (n:Label) WHERE n.name = 'something' RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("CYPHER 25\nMATCH (n:Label) WHERE n.name = **** RETURN n\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateCypher25() throws Exception {
        String query="LET x = 3 MATCH (n:Label) WHERE n.name = x RETURN n";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query, "-d", "25");
            });
            assertEquals("LET x = **** MATCH (n:Label) WHERE n.name = x RETURN n\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateRemovedV4Syntax() throws Exception {
        String query="MATCH (n:Label) WHERE exists(n.name) AND distance(n.prop, point({x:0, y:0})) > 12 RETURN 12 as x";

        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query );
            });
            assertEquals("MATCH (n:Label) WHERE exists(n.name) AND distance(n.prop, point({x:****, y:****})) > **** RETURN **** as x\n", outText);
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateExplain() throws Exception {
        String query="EXPLAIN MATCH (n:Label) WHERE n.name = 'something'<br>RETURN n";
        
        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute(query);
            });
            assertEquals("EXPLAIN\nMATCH (n:Label) WHERE n.name = ****\nRETURN n\n", outText);
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
            assertEquals("PROFILE\nMATCH (n:Label) WHERE n.name = **** RETURN n\n", outText);
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
            assertEquals("RETURN ****\n", outText);
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
            assertEquals("RETURN ****\n", Files.readString(Paths.get(outputFile)));
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
            assertEquals("RETURN ****\n", Files.readString(Paths.get(outputFile)));
        });
        assertEquals("", errText);
    }

    @Test
    void shouldObfuscateWithoutDroppingColons() throws Exception {
        String inputFile="src/test/resources/obfuscate.01Q83ESPWX.input";
        String errText = tapSystemErr(() -> {
            String outText = tapSystemOutNormalized(() -> {
                new CommandLine(new Obfuscator()).execute("-f", inputFile, "-p");
            });
            assertEquals("MATCH (s:Supplier)-[:IS_VENDOR]->(:Purchase_orders|Tollers_orders)-[:PLACED_IN]->(:Site),\n      (po:Purchase_orders)<-[:IS_PURCHASED]-(m:Material)\nRETURN *\n", outText);
        });
        assertEquals("", errText);
    }


//    @Test
//    void shouldObfuscateCypher25Syntaxes() throws Exception {
//        ArrayList<String> queries = new ArrayList<>();
//        queries.add("LET n = 1 RETURN n");
//        queries.add("RETURN 0 NEXT RETURN 1");
//
//        for (String query : queries) {
//            var statement = CypherParser.parse(query);
//            Configuration rendererConfig = Configuration.newConfig()
//                    .alwaysEscapeNames(false)
//                    .withPrettyPrint(true)
//                    .withDialect(Dialect.NEO4J_5_23  )
//                    .build();
//            var renderer = Renderer.getRenderer(rendererConfig);
//
//            String result = renderer.render(statement);
//            assertEquals(query, result);
//        }
//    }

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
