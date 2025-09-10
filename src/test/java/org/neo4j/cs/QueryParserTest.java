package org.neo4j.cs;

import org.junit.jupiter.api.Test;
import org.neo4j.cs.model.Model;
import org.neo4j.cs.model.Property;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class QueryParserTest {

    @Test
    void shouldIdentifyObfuscatedQueries() {
        var p = new QueryParser();
        assertFalse(p.isObfuscated("MATCH(s:Stuff)-[:IS]->(:Class) RETURN n"));
        assertTrue(p.isObfuscated("MATCH(s:Stuff)-[:IS]->(:Class) WHERE s.id =  ****** RETURN n"));
        assertTrue(p.isObfuscated("MATCH(s:Stuff)-[:IS******..******]->(:Class) RETURN n"));
    }

    @Test
    void shouldDeobfuscate() {
        var p = new QueryParser();
        assertEquals(
                "MATCH(s:Stuff)-[:IS]->(:Class) WHERE s.id =  123456 RETURN n",
                p.preProcessObfuscatedQuery("MATCH(s:Stuff)-[:IS]->(:Class) WHERE s.id =  ****** RETURN n")
                );
        assertTrue(true);
    }

    @Test
    void shouldParseQueries() {
        List<String> queries = new ArrayList<>();
        queries.add("MATCH(t:Thing)-[:HAS]->(:Stuff) WHERE t.name= 'thingy' RETURN t");
        queries.add("MATCH(s:Stuff)-[:IS]->(:Class) RETURN n");
        Model m = new QueryParser().parseQueries(queries);

        Set expectedNodeLabels = new HashSet<String>();
        expectedNodeLabels.add("Thing");
        expectedNodeLabels.add("Stuff");
        expectedNodeLabels.add("Class");

        Set expectedRelTypes = new HashSet<String>();
        expectedRelTypes.add("HAS");
        expectedRelTypes.add("IS");

        Set expectedProperties = new HashSet<String>();
        expectedProperties.add(new Property("name", "String"));

        assertEquals(expectedNodeLabels, m.getNodeLabels().keySet());
        assertEquals(expectedRelTypes, m.getRelationshipTypes().keySet());
        assertEquals(expectedProperties, m.getNodeLabels().get("Thing").getProperties());
    }
    @Test
    void shouldParseQuery() {
        Model m = new QueryParser().parseQuery("MATCH (:Left)-[:HAS]-(:Right) RETURN *");
//        System.out.println(m);
        Set expectedNodeLabels = new HashSet<String>();
        expectedNodeLabels.add("Left");
        expectedNodeLabels.add("Right");

        Set expectedRelTypes = new HashSet<String>();
        expectedRelTypes.add("HAS");

        assertEquals(expectedNodeLabels, m.getNodeLabels().keySet());
        assertEquals(expectedRelTypes, m.getRelationshipTypes().keySet());
    }

//    @Test
//    void shouldParseQuery2() {
//        var p = new QueryParser();
//        Model m = p.parseQuery2("MATCH (l:Left)-[:HAS]-(:Right) WHERE l.name = 'sdf' RETURN toUpper(l.id) as x");
////        System.out.println(m);
//        Set expectedNodeLabels = new HashSet<String>();
//        expectedNodeLabels.add("Left");
//        expectedNodeLabels.add("Right");
//
//        Set expectedRelTypes = new HashSet<String>();
//        expectedRelTypes.add("HAS");
//
//        assertEquals(expectedNodeLabels, m.getNodeLabels().keySet());
//        assertEquals(expectedRelTypes, m.getRelationshipTypes().keySet());
//    }

//    @Test
//    void shouldInferPropertyQuery() {
//        Model m = new QueryParser().parseQuery("MATCH (l:Left)-[:HAS]-(:Right) RETURN 5 as x, toUpper(l.name) as y");
//        Set expectedProperties = new HashSet<String>();
//        expectedProperties.add(new Property("name", "String"));
//        assertEquals(expectedProperties, m.getNodeLabels().get("Left").getProperties());
//    }
}
