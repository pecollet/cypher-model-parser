package org.neo4j.cs;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

public class QueryFilterTest {

    @Test
    void shouldFilterExplainQueries() {
        List<String> queries = new ArrayList<>();
        queries.add("EXPLAIN MATCH(n) <br>RETURN n");
        queries.add("  EXPLAIN   MATCH(n) RETURN n");
        List<String> filtered =  new QueryFilter().filter(queries);
        assertTrue(filtered.isEmpty());
    }

    @Test
    void shouldFilterShowQueries() {
        List<String> queries = new ArrayList<>();
        queries.add("SHOW DATABASES");
        queries.add(" show servers");
        List<String> filtered =  new QueryFilter().filter(queries);
        assertTrue(filtered.isEmpty());
    }

    @Test
    void shouldFilterIgnoreQueries() {
        List<String> queries = new ArrayList<>();
        queries.add("This query is just used to load the cypher compiler during warmup. Please ignore");
        List<String> filtered =  new QueryFilter().filter(queries);
        assertTrue(filtered.isEmpty());
    }

    @Test
    void shouldCleanupProfileQueries() {
        List<String> queries = new ArrayList<>();
        queries.add("PROFILE MATCH(n) RETURN n");
        queries.add(" profile MATCH(x) RETURN x");
        List<String> filtered =  new QueryFilter().filter(queries);
        assertTrue(filtered.contains("MATCH(n) RETURN n"));
        assertTrue(filtered.contains("MATCH(x) RETURN x"));
    }

    @Test
    void shouldCleanupPrefixedQueries() {
        List<String> queries = new ArrayList<>();
        queries.add("CYPHER RUNTIME=SLOTTED MATCH(n) RETURN n");
        queries.add("CYPHER runtime=interpreted MATCH(x) RETURN x");
        queries.add(" CYPHER   runtime=PARALLEL   MATCH(y) RETURN y");
        queries.add("CYPHER RUNTIME=SLOTTED   expressionEngine=INTERPRETED MATCH(z) RETURN z");
        queries.add("CYPHER 5  MATCH(a:Cypher5) RETURN a");
        queries.add("CYPHER 25  MATCH(a:Cypher25) RETURN a");
        List<String> filtered =  new QueryFilter().filter(queries);
        assertTrue(filtered.contains("MATCH(n) RETURN n"));
        assertTrue(filtered.contains("MATCH(x) RETURN x"));
        assertTrue(filtered.contains("MATCH(y) RETURN y"));
        assertTrue(filtered.contains("MATCH(z) RETURN z"));
        assertTrue(filtered.contains("MATCH(a:Cypher5) RETURN a"));
        assertTrue(filtered.contains("MATCH(a:Cypher25) RETURN a"));
    }

}
