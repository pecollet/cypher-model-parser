package org.neo4j.cs;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HcQueryMapCsvReaderTest {
    @Test
    void shouldReadNormalQueries() {
        QueryFileReader queryReader = new HcDatabaseQueriesCsvReader(new QueryFilter());
        List<String> queries = queryReader.read(new File("src/test/resources/database_queries_1.csv"));

        assertEquals(queries.size(),  10);
        assertTrue(queries.contains("MATCH (t:Thing) RETURN t"));
        assertTrue(queries.contains("MATCH (this1:A:B:C:D)-[:multi]->(:ff) \nreturn this1"));
        assertTrue(queries.contains("LET x = 1 MATCH (n:Node) WHERE n.name = x RETURN n"));
    }
}
