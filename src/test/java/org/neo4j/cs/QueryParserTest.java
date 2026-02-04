package org.neo4j.cs;

import org.junit.jupiter.api.Test;
import org.neo4j.cs.model.Model;
import org.neo4j.cs.model.Property;
import org.neo4j.cypher.internal.CypherVersion;
import scala.sys.Prop;

import java.util.*;

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
        var queries = List.of(
                "MATCH(t:Thing)-[:HAS]->(:Stuff) WHERE t.name= 'thingy' RETURN t",
                "MATCH(s:Stuff)-[:IS]->(:Class) RETURN n"
        );
        Model m = new QueryParser().parseQueries(queries);

        assertEquals(Set.of("Thing", "Stuff", "Class"), m.getNodeLabels().keySet());
        assertEquals(Set.of("HAS", "IS"), m.getRelationshipTypes().keySet());
        assertEquals(Set.of(new Property("name", "String")), m.getNodeLabels().get("Thing").getProperties());
    }

    @Test
    void shouldParseQuery_labelsAndTypes() {
        Model m = new QueryParser().parseQuery("MATCH (:Left|Alt&!Alt2)-[:HAS]->(r:Right) WHERE r:Other RETURN *");

        assertEquals(Set.of("Left", "Right", "Other", "Alt", "Alt2"), m.getNodeLabels().keySet());
        assertEquals(Set.of("HAS"), m.getRelationshipTypes().keySet());

        assertEquals(Set.of("Left", "Alt", "Alt2"), m.getRelationshipTypes().get("HAS").getSourceNodeLabels());
        assertEquals(Set.of("Right"), m.getRelationshipTypes().get("HAS").getTargetNodeLabels());
    }

    @Test
    void shouldParseQuery_cypher25() {
        var p = new QueryParser();
        Model m = p.parseQuery("LET x = 1 MATCH (n:Node) WHERE n.name = x RETURN n");

        assertEquals(Set.of("Node"), m.getNodeLabels().keySet());
    }

    @Test
    void shouldParseQuery_properties() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (l:Left {x: date(\"2025-02-18\")})<-[:HAS {since: [123]}]-(:Right {id: 'hhh', active : true}) WHERE l.name = 'sdf' RETURN toUpper(l.id) as x");

        assertEquals(Set.of("Left", "Right"), m.getNodeLabels().keySet());
        assertEquals(Set.of("HAS"), m.getRelationshipTypes().keySet());

        Set<Property> expectedLeftProps = Set.of(
                new Property("x", "Date"),
                new Property("name", "String"),
                new Property("id", "String")
        );
        assertEquals(expectedLeftProps, m.getNodeLabels().get("Left").getProperties());

        Set<Property> expectedRightProps = Set.of(
                new Property("active", "Boolean"),
                new Property("id", "String")
        );
        assertEquals(expectedRightProps, m.getNodeLabels().get("Right").getProperties());

        assertEquals(Set.of(new Property("since", "List")), m.getRelationshipTypes().get("HAS").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromPredicates_equals() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) WHERE x.name = 'sdf' AND x.id = 3 OR [1,2] = x.groups RETURN *");
        Set<Property> expectedProperties = Set.of(
                new Property("name", "String"),
                new Property("id", "Number"),
                new Property("groups", "List")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromPredicates_notEquals() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) WHERE x.name <> 'sdf' AND 3 <> x.id RETURN *");
        Set<Property> expectedProperties = Set.of(
                new Property("name", "String"),
                new Property("id", "Number")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromPredicates_greaterThan() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) WHERE x.name >= 'sdf' AND x.id > 3 RETURN *");
        Set<Property> expectedProperties = Set.of(
                new Property("name", "String"),
                new Property("id", "Number")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromPredicates_lessThan() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) WHERE x.name <= 'sdf' AND x.id < 3 RETURN *");
        Set<Property> expectedProperties = Set.of(
                new Property("name", "String"),
                new Property("id", "Number")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
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
        Set<Property> expectedProperties = Set.of(
                new Property("a", "Number"), new Property("a2", "Number"),
                new Property("b", "Number"), new Property("b2", "Number"),
                new Property("c", "Number"), new Property("c2", "Number"),
                new Property("d", "Number"), new Property("d2", "Number"),
                new Property("e", "Number"), new Property("e2", "Number"),
                new Property("f", "Number"), new Property("f2", "Number"),
                new Property("f3", "Number"), new Property("f4", "Number"),
                new Property("g", "Number")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromExpressions_boolean() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "RETURN x.a OR false, true OR x.a2, " +
                "x.b AND false, true AND x.b2," +
                "x.c XOR false, true XOR x.c2," +
                "NOT x.d");
        Set<Property> expectedProperties = Set.of(
                new Property("a", "Boolean"), new Property("a2", "Boolean"),
                new Property("b", "Boolean"), new Property("b2", "Boolean"),
                new Property("c", "Boolean"), new Property("c2", "Boolean"),
                new Property("d", "Boolean")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
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
        Set<Property> expectedProperties = Set.of(
                new Property("a", "String"), new Property("a2", "String"),
                new Property("b", "String"), new Property("b2", "String"),
                new Property("c", "String"), new Property("c2", "String"),
                new Property("d", "String"), new Property("d2", "String"),
                new Property("e", "String"), new Property("e2", "String"),
                new Property("f", "String"), new Property("f2", "String"),
                new Property("g", "String"), new Property("g2", "String"),
                new Property("h", "String")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFromExpressions_lists() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE 'ddd' IN x.a " +
                "AND x.b IN [1,2,'3']" +
                "RETURN *");
        Set<Property> expectedProperties = Set.of(
                new Property("a", "List"),
                new Property("b", "UNKNOWN")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFunctions_numbers() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE toInteger(x.b) = x.a " +
                "AND  x.a2 = toInteger(x.b2)" +
                "AND id(x) = x.id " +
                "RETURN log10(x.v)");
        Set<Property> expectedProperties = Set.of(
                new Property("a", "Number"),
                new Property("b", "UNKNOWN"),
                new Property("a2", "Number"),
                new Property("b2", "UNKNOWN"),
                new Property("v", "Number"),
                new Property("id", "Number")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFunctions_vectors() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE vector([1,2,3]) = x.a ");
        assertEquals(Set.of(new Property("a", "Vector")), m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFunctions_strings() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE toLower(x.a) = x.a2 " +
                "AND toUpper(x.b) = x.b2 " +
                "AND ltrim(x.c) = x.c2 " +
                "AND x.d2 = rtrim(x.d, ' ')  " +
                "AND char_length(x.e) = x.e2 " +
                "RETURN *");
        Set<Property> expectedProperties = Set.of(
                new Property("a", "String"), new Property("a2", "String"),
                new Property("b", "String"), new Property("b2", "String"),
                new Property("c", "String"), new Property("c2", "String"),
                new Property("d", "String"), new Property("d2", "String"),
                new Property("e", "String"), new Property("e2", "Number")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFunctions_lists() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE collect(1) = x.b " +
                "UNWIND x.l as i " +
                "RETURN head(x.m), tail(x.n), last(x.o), coll.distinct(x.p)");
        Set<Property> expectedProperties = Set.of(
                new Property("b", "List"),
                new Property("l", "List"),
                new Property("m", "List"),
                new Property("n", "List"),
                new Property("o", "List"),
                new Property("p", "List")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFunctions_dates() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE date() = x.b " +
                "AND x.c = datetime() " +
                "AND datetime.realtime() = x.d " +
                "RETURN *");
        Set<Property> expectedProperties = Set.of(
                new Property("b", "Date"),
                new Property("c", "Time"),
                new Property("d", "Time")
        );

        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldDealWithConflictingPropertyTypes() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE date() = x.whatami " +
                "AND x.whatami = 12 " +
                "RETURN *");
        assertEquals(Set.of(new Property("whatami", "UNKNOWN")), m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldCombineMultipleRefsToRelationshipTypes() {
        var p = new QueryParser();
        Model m = p.parseQuery("match path = (n)-[:Has_Postcode]->(p:Postcode)<-[:Has_Postcode]-(a:Address) " +
                "RETURN *");

        assertEquals(Set.of("Postcode", "Address"), m.getNodeLabels().keySet());
        assertEquals(Set.of("Has_Postcode"), m.getRelationshipTypes().keySet());

        assertEquals(Set.of("Address"), m.getRelationshipTypes().get("Has_Postcode").getSourceNodeLabels());
        assertEquals(Set.of("Postcode"), m.getRelationshipTypes().get("Has_Postcode").getTargetNodeLabels());
    }

    @Test
    void shouldLinkNodesAndRelsInMultiHopPatterns() {
        var p = new QueryParser();
        Model m = p.parseQuery("match path = (n)-[:Has_Postcode]->(p:Postcode)<-[:Has_Thing]-(a:Address) " +
                "RETURN *");

        assertEquals(Set.of("Postcode", "Address"), m.getNodeLabels().keySet());
        assertEquals(Set.of("Has_Postcode", "Has_Thing"), m.getRelationshipTypes().keySet());

        assertEquals(Collections.emptySet(), m.getRelationshipTypes().get("Has_Postcode").getSourceNodeLabels());
        assertEquals(Set.of("Postcode"), m.getRelationshipTypes().get("Has_Postcode").getTargetNodeLabels());

        assertEquals(Set.of("Address"), m.getRelationshipTypes().get("Has_Thing").getSourceNodeLabels());
        assertEquals(Set.of("Postcode"), m.getRelationshipTypes().get("Has_Thing").getTargetNodeLabels());
    }

    @Test
    void shouldExtractPropertyFromIndexCreation() {
        var p = new QueryParser();
        Model m = p.parseQuery(" CREATE TEXT INDEX location_name FOR (n:Location) ON (n.name) ");
        assertEquals(Set.of("Location"), m.getNodeLabels().keySet());

        assertEquals(Set.of(new Property("name", "UNKNOWN")), m.getNodeLabels().get("Location").getProperties());
    }

    @Test
    void shouldExtractPropertyFromConstraintCreation() {
        var p = new QueryParser();
        Model m = p.parseQuery(" CREATE CONSTRAINT location_name FOR (n:Location)  REQUIRE n.property IS UNIQUE ");
        assertEquals(Set.of("Location"), m.getNodeLabels().keySet());

        assertEquals(Set.of(new Property("property", "UNKNOWN")), m.getNodeLabels().get("Location").getProperties());
    }

    @Test
    void shouldExtractRelationshipsFromNodePatternsWithOnlyVariables() {
        var p = new QueryParser();
        Model m = p.parseQuery("LOAD CSV WITH HEADERS FROM 'some/path' AS row " +
                "MATCH (p:Product), (o:Order) " +
                "WHERE p.productID = row.productID AND o.orderID = row.orderID " +
                "CREATE (o)-[details:ORDERS]->(p) " +
                "SET details = row, details.quantity = toInteger(row.quantity)");

        assertEquals(Set.of("Product", "Order"), m.getNodeLabels().keySet());
        assertEquals(Set.of("ORDERS"), m.getRelationshipTypes().keySet());

        assertEquals(Set.of("Order"), m.getRelationshipTypes().get("ORDERS").getSourceNodeLabels());
        assertEquals(Set.of("Product"), m.getRelationshipTypes().get("ORDERS").getTargetNodeLabels());

        Set<Property> expectedProperties = Set.of(new Property("quantity", "Number"));
        assertEquals(expectedProperties, m.getRelationshipTypes().get("ORDERS").getProperties());
    }
}