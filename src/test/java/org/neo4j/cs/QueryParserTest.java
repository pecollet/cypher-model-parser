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
                "MATCH(s:Stuff)-[:IS]->(:Class) WHERE s.id =  $abcde RETURN n",
                p.preProcessObfuscatedQuery("MATCH(s:Stuff)-[:IS]->(:Class) WHERE s.id =  ****** RETURN n")
        );
        assertTrue(true);
    }

    @Test
    void shouldParseQueries() {
        var queries = List.of(
                "MATCH(t:Thing)-[:HAS]->(s:Stuff) WHERE s.name= 'thingy' RETURN t",
                "MATCH(s:Stuff {id : 23})-[:IS]->(:Class) RETURN n"
        );
        Model m = new QueryParser().parseQueries(queries);

        assertEquals(Set.of("Thing", "Stuff", "Class"), m.getNodeLabels().keySet());
        assertEquals(Set.of("HAS", "IS"), m.getRelationshipTypes().keySet());
        assertEquals(Set.of(new Property("name", "String"), new Property("id", "Number")),
                m.getNodeLabels().get("Stuff").getProperties());
    }

    @Test
    void shouldParseQuery_labelsAndTypes() {
        Model m = new QueryParser().parseQuery("MATCH (:Left|Alt&!Alt2)-[:HAS]->(r:Right) WHERE r:Other RETURN *");

        assertEquals(Set.of("Left", "Right", "Other", "Alt", "Alt2"), m.getNodeLabels().keySet());
        assertEquals(Set.of("HAS"), m.getRelationshipTypes().keySet());

        assertEquals(Set.of(), m.getRelationshipTypes().get("HAS").getSourceNodeLabels());
        assertEquals(Set.of("Right", "Other"), m.getRelationshipTypes().get("HAS").getTargetNodeLabels());
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
                "AND x.c IN [1,2,3]" +
                "RETURN *");
        Set<Property> expectedProperties = Set.of(
                new Property("a", "List"),
                new Property("b", "UNKNOWN"),
                new Property("c", "Number")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFunctions_numbers() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE toInteger(x.b) = x.a " +
                "AND  x.a2 = toInteger(x.b2)" +
                "AND  x.f2 = toFloat(x.g2)" +
                "AND id(x) = x.id " +
                "RETURN log10(x.v)");
        Set<Property> expectedProperties = Set.of(
                new Property("a", "Number"),
                new Property("b", "UNKNOWN"),
                new Property("a2", "Number"),
                new Property("b2", "UNKNOWN"),
                new Property("f2", "Number"),
                new Property("g2", "UNKNOWN"),
                new Property("v", "Number"),
                new Property("id", "Number")
        );
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyTypeFunctions_vectors() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (x:Node) " +
                "WHERE vector([1,2,3]) = x.a " +
                "AND  vector_dimension_count(x.a2) = 256" );
        assertEquals(
                Set.of(
                        new Property("a", "Vector"),
                        new Property("a2", "Vector")
                ),
                m.getNodeLabels().get("Node").getProperties());
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

    @Test
    void shouldCombineConflictingPropertyTypes() {
        var queries = List.of(
                "CREATE (TheMatrix:Movie {title:'The Matrix', released:1999, tagline:'blah'})",
                "CREATE CONSTRAINT FOR (n:Movie) REQUIRE (n.title) IS UNIQUE"
        );
        Model m = new QueryParser().parseQueries(queries);

        assertEquals(Set.of("Movie"), m.getNodeLabels().keySet());

        Set<Property> expectedProperties = Set.of(
                new Property("released", "Number"),
                new Property("title", "String"),
                new Property("tagline", "String"));
        assertEquals(expectedProperties, m.getNodeLabels().get("Movie").getProperties());
    }

    @Test
    void shouldInferPropertyTypeForNestedFunctionInvocations() {
        var p = new QueryParser();
        Model m = p.parseQuery("CREATE (e:Element {name:toString(i)+'-'+toString(j)})");
        assertEquals(Set.of("Element"), m.getNodeLabels().keySet());
        assertEquals(Set.of(new Property("name", "String")), m.getNodeLabels().get("Element").getProperties());
    }

    @Test
    void shouldInferPropertyTypeForList() {
        var p = new QueryParser();
        Model m = p.parseQuery("CREATE (p:Product {ids: [1] + [2], embedding: [i IN range(1, 1000) | rand()]})");
        assertEquals(Set.of("Product"), m.getNodeLabels().keySet());
        Set<Property> expectedProperties = Set.of(
                new Property("ids", "List"),
                new Property("embedding", "List"));
        assertEquals(expectedProperties, m.getNodeLabels().get("Product").getProperties());
    }

    @Test
    void shouldInferPropertyType_toString() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (n:PersonList_v3) WHERE n.x =~ $var SET n.list_id = toString(n.list_id) RETURN split(n.list_val, '-')");
        assertEquals(Set.of("PersonList_v3"), m.getNodeLabels().keySet());
        Set<Property> expectedProperties = Set.of(
                new Property("list_val", "String"),
                new Property("x", "String"),
                new Property("list_id", "String"));
        assertEquals(expectedProperties, m.getNodeLabels().get("PersonList_v3").getProperties());
    }

    @Test
    void shouldInferPropertyType_apoc() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (n:Node) WHERE n.x = apoc.coll.union(n.y, [12]) RETURN *");
        assertEquals(Set.of("Node"), m.getNodeLabels().keySet());
        Set<Property> expectedProperties = Set.of(
                new Property("x", "List"),
                new Property("y", "List"));
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldInferPropertyType_ambiguousTypes() {
        // property x is detected as both unknown and string => string
        // property z is detected as both integer and string => unknown
        String query = "MATCH (o:Organization) " +
                "WHERE ((o.x = $ccna) OR ($acna IN o.y)) AND o.z = 1 " +
                "RETURN o.y " +
                "ORDER BY CASE WHEN o.x = 'BOB' THEN 0 WHEN o.z = 'BILL' THEN 2 ELSE 1 END LIMIT 1";
//        query = "MATCH (o:Organization) WHERE o.id = 12 AND o.id = '' RETURN 0";
        var p = new QueryParser();
        Model m = p.parseQuery(query);
        assertEquals(Set.of("Organization"), m.getNodeLabels().keySet());
        Set<Property> expectedProperties = Set.of(
                new Property("y", "List"),
                new Property("z", "UNKNOWN"),
                new Property("x", "String"));
        assertEquals(expectedProperties, m.getNodeLabels().get("Organization").getProperties());
    }

    @Test
    void shouldInferPropertyType_ambiguousProperties() {
        List<String> queries = new ArrayList<>();
        queries.add(
        "    UNWIND $data AS row\n" +
                "    MATCH (d:Document {UID: row.UID})\n" +
                "    MATCH (p:Person {reference: row.person_reference})\n" +
                "    MERGE (d)-[:HAS_PERSON]->(p)");
        queries.add("UNWIND $data AS row\n" +
                "MATCH (d {UID: row.doc_uid})\n" +
                "MATCH (e {UID: row.entity_uid})\n" +
                "CREATE (d)-[:HAS_PERSON]->(e)");
        queries.add("MATCH p=(d:Document)-[:HAS_PERSON]->() where d.collectionID=32 RETURN p");
        queries.add("MATCH p=()-[:HAS_PERSON]->(d:Document) where d.collectionID=51 RETURN p LIMIT 25;");
        queries.add("MATCH p=()-[:HAS_PERSON]->() RETURN p");
        var p = new QueryParser();
        Model m = p.parseQueries(queries);
        assertEquals(Set.of("Document", "Person"), m.getNodeLabels().keySet());
        assertEquals(Set.of("HAS_PERSON"), m.getRelationshipTypes().keySet());
        assertEquals(Set.of("Document"), m.getRelationshipTypes().get("HAS_PERSON").getSourceNodeLabels());
        assertEquals(Set.of("Person", "Document"), m.getRelationshipTypes().get("HAS_PERSON").getTargetNodeLabels());
    }

    @Test
    void shouldInferPropertyType_listFunctionReturn() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (n:Node) WHERE n.x = toStringList([1,2]) RETURN *");
        assertEquals(Set.of("Node"), m.getNodeLabels().keySet());
        Set<Property> expectedProperties = Set.of(
                new Property("x", "List"));
        assertEquals(expectedProperties, m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldSensiblyParseBloomQueries() {
        var p = new QueryParser();
        Model m = p.parseQuery(
                "MATCH (n) " +
                "WHERE elementId(n) IN $nodeIds " +
                "   AND ( n:`AML` OR n:`Account` OR n:`Alert` OR n:`BIC`)    " +
                "RETURN n as n, null as r " +
                "UNION " +
                "UNWIND $relationshipIds as relId " +
                "MATCH (n)-[r:`HAS`|`IN_POSTALCODE`]-(m) " +
                "WHERE elementId(n) in $nodeIds and elementId(r) = relId and elementId(m) in $nodeIds " +
                "AND (n:`AML` OR n:`Account` OR n:`Alert` OR n:`BIC`) " +
                "AND (m:`AML` OR m:`Account` OR m:`Alert` OR m:`BIC`) " +
                "RETURN null as n, r ");
        assertEquals(Set.of("AML", "Account", "Alert", "BIC"), m.getNodeLabels().keySet());
        assertEquals(Set.of("HAS", "IN_POSTALCODE"), m.getRelationshipTypes().keySet());
        assertEquals(Set.of(), m.getRelationshipTypes().get("HAS").getSourceNodeLabels());
        assertEquals(Set.of(), m.getRelationshipTypes().get("HAS").getTargetNodeLabels());
        assertEquals(Set.of(), m.getRelationshipTypes().get("HAS").getUndirectedNodeLabels());
        assertEquals(Set.of(), m.getRelationshipTypes().get("IN_POSTALCODE").getSourceNodeLabels());
        assertEquals(Set.of(), m.getRelationshipTypes().get("IN_POSTALCODE").getTargetNodeLabels());
        assertEquals(Set.of(), m.getRelationshipTypes().get("IN_POSTALCODE").getUndirectedNodeLabels());
    }

    @Test
    void shouldExcludeNegatedAndDisjointLabelsFromLabelExpressions() {
        var p = new QueryParser();
        Model m = p.parseQuery(
                "MATCH (:A&(B|C)&!D)-[:X|Y]->()  RETURN *");
        //TODO : what is expected here?
        assertEquals(Set.of("A", "B", "C", "D"), m.getNodeLabels().keySet()); // All mentioned labels should be present
        assertEquals(Set.of("X", "Y"), m.getRelationshipTypes().keySet()); // All mentioned rels should be present
        // X & Y : should they expect source A only? nothing?
        // certainly not D
        assertEquals(Set.of("A"), m.getRelationshipTypes().get("X").getSourceNodeLabels());
        assertEquals(Set.of("A"), m.getRelationshipTypes().get("Y").getSourceNodeLabels());
    }

    @Test
    void shouldExcludeDisjointLabelsFromLabelExpressions() {
        var p = new QueryParser();
        Model m = p.parseQuery(
                "MATCH (:A|B|C|D)-[:X|Y]->()  RETURN *");
        assertEquals(Set.of("A", "B", "C", "D"), m.getNodeLabels().keySet()); // All mentioned labels should be present
        assertEquals(Set.of("X", "Y"), m.getRelationshipTypes().keySet()); // All mentioned rels should be present
        // X & Y : no source nodes
        assertEquals(Set.of(), m.getRelationshipTypes().get("X").getSourceNodeLabels());
        assertEquals(Set.of(), m.getRelationshipTypes().get("Y").getSourceNodeLabels());
    }

    @Test
    void shouldExcludeDisjointLabelsFromOrPredicates() {
        var p = new QueryParser();
        Model m = p.parseQuery(
                "MATCH (n)-[:X|Y]->() WHERE (n:A OR n:B OR n:C OR n:D) RETURN *");
        assertEquals(Set.of("A", "B", "C", "D"), m.getNodeLabels().keySet()); // All mentioned labels should be present
        assertEquals(Set.of("X", "Y"), m.getRelationshipTypes().keySet()); // All mentioned rels should be present
        // X & Y : no source nodes
        assertEquals(Set.of(), m.getRelationshipTypes().get("X").getSourceNodeLabels());
        assertEquals(Set.of(), m.getRelationshipTypes().get("Y").getSourceNodeLabels());
    }

    @Test
    void shouldIncludeConjointLabelsFromOrPredicates() {
        var p = new QueryParser();
        Model m = p.parseQuery(
                "MATCH (n:A&B)-[:X|Y]->() RETURN *");
        assertEquals(Set.of("A", "B"), m.getNodeLabels().keySet()); // All mentioned labels should be present
        assertEquals(Set.of("X", "Y"), m.getRelationshipTypes().keySet()); // All mentioned rels should be present
        // X & Y : no source nodes
        assertEquals(Set.of("A", "B"), m.getRelationshipTypes().get("X").getSourceNodeLabels());
        assertEquals(Set.of("A", "B"), m.getRelationshipTypes().get("Y").getSourceNodeLabels());
    }

    @Test
    void shouldParseAcyclicIn2026_04() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH p = ACYCLIC (:Router {name: 'A'})-[:LINK]-+(:Router {name: 'Z'}) RETURN p");
        assertEquals(Set.of("Router"), m.getNodeLabels().keySet());
        assertEquals(Set.of( new Property("name", "String")), m.getNodeLabels().get("Router").getProperties());
        assertEquals(Set.of("LINK"), m.getRelationshipTypes().keySet());
        assertEquals(Set.of("Router"), m.getRelationshipTypes().get("LINK").getUndirectedNodeLabels());
    }

    @Test
    void shouldParseQuantifiedPathPatternFromManual() {
        var p = new QueryParser();
        Model m = p.parseQuery("""
                MATCH (:Station { name: 'Denmark Hill' })<-[:CALLS_AT]-(d:Stop)
                      ((:Stop)-[:NEXT]->(:Stop)){1,3}
                      (a:Stop)-[:CALLS_AT]->(:Station { name: 'Clapham Junction' })
                RETURN d.departs AS departureTime, a.arrives AS arrivalTime
                """);

        assertEquals(Set.of("Station", "Stop"), m.getNodeLabels().keySet());
        assertEquals(Set.of("CALLS_AT", "NEXT"), m.getRelationshipTypes().keySet());

        assertEquals(Set.of("Stop"), m.getRelationshipTypes().get("CALLS_AT").getSourceNodeLabels());
        assertEquals(Set.of("Station"), m.getRelationshipTypes().get("CALLS_AT").getTargetNodeLabels());

        assertEquals(Set.of("Stop"), m.getRelationshipTypes().get("NEXT").getSourceNodeLabels());
        assertEquals(Set.of("Stop"), m.getRelationshipTypes().get("NEXT").getTargetNodeLabels());
    }

    @Test
    void shouldParseQuantifiedRelationshipFromManual() {
        var p = new QueryParser();
        Model m = p.parseQuery("""
                MATCH (d:Station { name: 'Denmark Hill' })<-[:CALLS_AT]-
                        (n:Stop)-[:NEXT]->{1,10}(m:Stop)-[:CALLS_AT]->
                        (a:Station { name: 'Clapham Junction' })
                WHERE m.arrives < time('17:18')
                RETURN n.departs AS departureTime
                """);

        assertEquals(Set.of("Station", "Stop"), m.getNodeLabels().keySet());
        assertEquals(Set.of("CALLS_AT", "NEXT"), m.getRelationshipTypes().keySet());

        assertEquals(Set.of("Stop"), m.getRelationshipTypes().get("CALLS_AT").getSourceNodeLabels());
        assertEquals(Set.of("Station"), m.getRelationshipTypes().get("CALLS_AT").getTargetNodeLabels());

        assertEquals(Set.of("Stop"), m.getRelationshipTypes().get("NEXT").getSourceNodeLabels());
        assertEquals(Set.of("Stop"), m.getRelationshipTypes().get("NEXT").getTargetNodeLabels());
    }

    @Test
    void shouldParseQuantifiedPathPatternWithGroupVariablesFromManual() {
        var p = new QueryParser();
        Model m = p.parseQuery("""
                MATCH (:Station {name: 'Denmark Hill'})<-[:CALLS_AT]-(origin)
                      ((l)-[r:NEXT]->(m)){1,3}
                      ()-[:CALLS_AT]->(:Station {name: 'Clapham Junction'})
                RETURN origin.departs + [stop in m | stop.departs] AS departureTimes,
                       reduce(acc = 0.0, next in r | round(acc + next.distance, 2)) AS totalDistance
                """);

        assertEquals(Set.of("Station"), m.getNodeLabels().keySet());
        assertEquals(Set.of("CALLS_AT", "NEXT"), m.getRelationshipTypes().keySet());

        assertEquals(Set.of("Station"), m.getRelationshipTypes().get("CALLS_AT").getTargetNodeLabels());
        assertEquals(Set.of(), m.getRelationshipTypes().get("NEXT").getSourceNodeLabels());
        assertEquals(Set.of(), m.getRelationshipTypes().get("NEXT").getTargetNodeLabels());
    }

    @Test
    void shouldParseQuantifiedPathPatternWithInlinePredicateFromManual() {
        var p = new QueryParser();
        Model m = p.parseQuery("""
                MATCH (bfr:Station {name: "London Blackfriars"}),
                      (ndl:Station {name: "North Dulwich"})
                MATCH p = (bfr)
                          ((a)-[:LINK]-(b:Station)
                            WHERE point.distance(a.location, ndl.location) >
                              point.distance(b.location, ndl.location))+ (ndl)
                RETURN reduce(acc = 0, r in relationships(p) | round(acc + r.distance, 2))
                  AS distance
                """);

        assertEquals(Set.of("Station"), m.getNodeLabels().keySet());
        assertEquals(Set.of("LINK"), m.getRelationshipTypes().keySet());
        assertEquals(Set.of("Station"), m.getRelationshipTypes().get("LINK").getUndirectedNodeLabels());
    }

    @Test
    void shouldParseForIn2026_04() {
        var p = new QueryParser();
        Model m = p.parseQuery("FOR i in [1,2,3] MATCH (n:Node) WHERE n.id = i RETURN n");
        assertEquals(Set.of("Node"), m.getNodeLabels().keySet());
//        assertEquals(Set.of( new Property("id", "Number")), m.getNodeLabels().get("Node").getProperties());
    }
    @Test
    void shouldParseUnwind() {
        var p = new QueryParser();
        Model m = p.parseQuery("UNWIND [1,2,3] as i MATCH (n:Node) WHERE n.id = i RETURN n");
        assertEquals(Set.of("Node"), m.getNodeLabels().keySet());
//        assertEquals(Set.of( new Property("id", "Number")), m.getNodeLabels().get("Node").getProperties());
    }

    @Test
    void shouldParseIsLabeledIn2026_04() {
        var p = new QueryParser();
        Model m = p.parseQuery("MATCH (n)-[:LINK]->() WHERE n IS LABELED !A&B AND n IS NOT LABELED C AND n IS NOT D RETURN n");
        assertEquals(Set.of("A", "B", "C", "D"), m.getNodeLabels().keySet());
        assertEquals(Set.of("LINK"), m.getRelationshipTypes().keySet());
        assertEquals(Set.of("B"), m.getRelationshipTypes().get("LINK").getSourceNodeLabels());
    }
}