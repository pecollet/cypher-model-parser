package org.neo4j.cs;

import org.junit.jupiter.api.Test;
import org.neo4j.cs.model.Model;
import org.neo4j.cs.model.Property;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueryParserTest {
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
        expectedProperties.add(new Property("name"));

        assertEquals(m.getNodeLabels().keySet(), expectedNodeLabels);
        assertEquals(m.getRelationshipTypes().keySet(), expectedRelTypes);
        assertEquals(m.getNodeLabels().get("Thing").getProperties(), expectedProperties);
    }
    @Test
    void shouldParseQuery() {
        Model m = new QueryParser().parseQuery("MATCH (:Left)-[:HAS]-(:Right) RETURN *");
        System.out.println(m);
    }
}
