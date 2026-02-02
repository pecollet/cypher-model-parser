package org.neo4j.cs;

import org.junit.jupiter.api.Test;
import org.neo4j.cs.model.Model;
import org.neo4j.cs.model.Property;
import scala.sys.Prop;

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
        assertFalse(p.isObfuscated("MATCH(s:Stuff)-[:IS]->(:Class) RETURN *"));
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
    void shouldParseQuery_labelsAndTypes() {
        Model m = new QueryParser().parseQuery("MATCH (:Left|Alt&!Alt2)-[:HAS]->(r:Right) WHERE r:Other RETURN *");
        System.out.println(m);
        Set expectedNodeLabels = new HashSet<String>();
        expectedNodeLabels.add("Left");
        expectedNodeLabels.add("Right");
        expectedNodeLabels.add("Other");
        expectedNodeLabels.add("Alt");
        expectedNodeLabels.add("Alt2");

        Set expectedRelTypes = new HashSet<String>();
        expectedRelTypes.add("HAS");

        assertEquals(expectedNodeLabels, m.getNodeLabels().keySet());
        assertEquals(expectedRelTypes, m.getRelationshipTypes().keySet());
        Set expectedRelSources = new HashSet<String>();
        expectedRelSources.add("Left");
        expectedRelSources.add("Alt");
        expectedRelSources.add("Alt2");
        Set expectedRelTargets = new HashSet<String>();
        expectedRelTargets.add("Right");
//        expectedRelTargets.add("Other");  // a bit too hard to link RelType from outside a pattern to source/target
        assertEquals(expectedRelSources, m.getRelationshipTypes().get("HAS").getSourceNodeLabels());
        assertEquals(expectedRelTargets, m.getRelationshipTypes().get("HAS").getTargetNodeLabels());
    }

    @Test
    void shouldParseQuery_cypher25() {
        var p = new QueryParser();
        Model m = p.parseQuery("LET x = 1 MATCH (n:Node) WHERE n.name = x RETURN n");
        System.out.println(m);
        Set expectedNodeLabels = new HashSet<String>();
        expectedNodeLabels.add("Node");
        assertEquals(expectedNodeLabels, m.getNodeLabels().keySet());
    }
    @Test
    void shouldParseQuery_properties() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (l:Left {x: date(\"2025-02-18\")})<-[:HAS {since: [123]}]-(:Right {id: 'hhh', active : true}) WHERE l.name = 'sdf' RETURN toUpper(l.id) as x");
        System.out.println(m);
        Set expectedNodeLabels = new HashSet<String>();
        expectedNodeLabels.add("Left");
        expectedNodeLabels.add("Right");

        Set expectedRelTypes = new HashSet<String>();
        expectedRelTypes.add("HAS");

        assertEquals(expectedNodeLabels, m.getNodeLabels().keySet());
        assertEquals(expectedRelTypes, m.getRelationshipTypes().keySet());

        Set expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("x", "Date"));
        expectedProperties.add(new Property("name", "String"));
        expectedProperties.add(new Property("id", "String"));
        assertEquals(expectedProperties,  m.getNodeLabels().get("Left").getProperties());

        expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("active", "Boolean"));
        expectedProperties.add(new Property("id", "String"));
        assertEquals(expectedProperties,  m.getNodeLabels().get("Right").getProperties());

        expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("since", "List"));
        assertEquals(expectedProperties,  m.getRelationshipTypes().get("HAS").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromPredicates_equals() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) WHERE x.name = 'sdf' AND x.id = 3 RETURN *");
        Set expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("name", "String"));
        expectedProperties.add(new Property("id", "Number"));
        assertEquals(expectedProperties,  m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromPredicates_notEquals() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) WHERE x.name <> 'sdf' AND x.id <> 3 RETURN *");
        Set expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("name", "String"));
        expectedProperties.add(new Property("id", "Number"));
        assertEquals(expectedProperties,  m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromPredicates_greaterThan() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) WHERE x.name >= 'sdf' AND x.id > 3 RETURN *");
        Set expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("name", "String"));
        expectedProperties.add(new Property("id", "Number"));
        assertEquals(expectedProperties,  m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromPredicates_lessThan() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) WHERE x.name <= 'sdf' AND x.id < 3 RETURN *");
        Set expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("name", "String"));
        expectedProperties.add(new Property("id", "Number"));
        assertEquals(expectedProperties,  m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromExpressions_numbers() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "RETURN x.a + 3, 1 + x.a2," +
                "x.b - 4, 5 - x.b2," +
                "x.c * 34, 2 * x.c2," +
                "x.d / 2, 3 / x.d2," +
                "x.e % 3, 10 % x.e2," +
                "x.f ^ 2, 2 ^ x.f2, x.f3 ^ x.f4," +
                "- x.g");
        Set expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("a", "Number"));
        expectedProperties.add(new Property("a2", "Number"));
        expectedProperties.add(new Property("b", "Number"));
        expectedProperties.add(new Property("b2", "Number"));
        expectedProperties.add(new Property("c", "Number"));
        expectedProperties.add(new Property("c2", "Number"));
        expectedProperties.add(new Property("d", "Number"));
        expectedProperties.add(new Property("d2", "Number"));
        expectedProperties.add(new Property("e", "Number"));
        expectedProperties.add(new Property("e2", "Number"));
        expectedProperties.add(new Property("f", "Number"));
        expectedProperties.add(new Property("f2", "Number"));
        expectedProperties.add(new Property("f3", "Number"));
        expectedProperties.add(new Property("f4", "Number"));
        expectedProperties.add(new Property("g", "Number"));
        assertEquals(expectedProperties,  m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromExpressions_boolean() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "RETURN x.a OR false, true OR x.a2, " +
                "x.b AND false, true AND x.b2," +
                "x.c XOR false, true XOR x.c2," +
                "NOT x.d");
        Set expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("a", "Boolean"));
        expectedProperties.add(new Property("a2", "Boolean"));
        expectedProperties.add(new Property("b", "Boolean"));
        expectedProperties.add(new Property("b2", "Boolean"));
        expectedProperties.add(new Property("c", "Boolean"));
        expectedProperties.add(new Property("c2", "Boolean"));
        expectedProperties.add(new Property("d", "Boolean"));
        assertEquals(expectedProperties,  m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromExpressions_strings() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE x.a CONTAINS '123' OR '123' CONTAINS x.a2 "+
                "AND x.b STARTS WITH 'A' OR 'ABC' STARTS WITH x.b2 " +
                "AND x.c ENDS WITH 'A' OR 'ABC' ENDS WITH x.c2 " +
                "AND x.d =~ 'abc.*' OR 'abc' =~ x.d2 " +
                "RETURN x.e + 'ABC', 'df' + x.e2, " +
                "x.f CONTAINS x.f2," +
                "x.g || 'ABC', 'df' || x.g2, " +
                "x.h IS NORMALIZED");
        Set expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("a", "String"));
        expectedProperties.add(new Property("a2", "String"));
        expectedProperties.add(new Property("b", "String"));
        expectedProperties.add(new Property("b2", "String"));
        expectedProperties.add(new Property("c", "String"));
        expectedProperties.add(new Property("c2", "String"));
        expectedProperties.add(new Property("d", "String"));
        expectedProperties.add(new Property("d2", "String"));
        expectedProperties.add(new Property("e", "String"));
        expectedProperties.add(new Property("e2", "String"));
        expectedProperties.add(new Property("f", "String"));
        expectedProperties.add(new Property("f2", "String"));
        expectedProperties.add(new Property("g", "String"));
        expectedProperties.add(new Property("g2", "String"));
        expectedProperties.add(new Property("h", "String"));
        assertEquals(expectedProperties,  m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromExpressions_lists() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE 'ddd' IN x.a " +
                "AND x.b IN [1,2,'3']" +
                "RETURN *");
        Set expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("a", "List"));
        expectedProperties.add(new Property("b", "UNKNOWN"));
        assertEquals(expectedProperties,  m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFunctions_numbers() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE toInteger(x.b) = x.a ");
        Set expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("a", "Number"));
        expectedProperties.add(new Property("b", "UNKNOWN"));
        assertEquals(expectedProperties,  m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFunctions_strings() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE toLower(x.a) = x.a2 " +
                "AND toUpper(x.b) = x.b2 " +
                "AND ltrim(x.c) = x.c2 " +
                "AND rtrim(x.d, ' ') = x.d2 " +
                "RETURN *");
        Set expectedProperties = new HashSet<Property>();
        expectedProperties.add(new Property("a", "String"));
        expectedProperties.add(new Property("a2", "String"));
        expectedProperties.add(new Property("b", "String"));
        expectedProperties.add(new Property("b2", "String"));
        expectedProperties.add(new Property("c", "String"));
        expectedProperties.add(new Property("c2", "String"));
        expectedProperties.add(new Property("d", "String"));
        expectedProperties.add(new Property("d2", "String"));
        assertEquals(expectedProperties,  m.getNodeLabels().get("Node").getProperties());
    }
//    @Test
//    void shouldInferPropertyQuery() {
//        Model m = new QueryParser().parseQuery("MATCH (l:Left)-[:HAS]-(:Right) RETURN 5 as x, toUpper(l.name) as y");
//        Set expectedProperties = new HashSet<String>();
//        expectedProperties.add(new Property("name", "String"));
//        assertEquals(expectedProperties, m.getNodeLabels().get("Left").getProperties());
//    }
}
