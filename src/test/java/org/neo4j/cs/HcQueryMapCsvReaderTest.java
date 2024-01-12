package org.neo4j.cs;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HcQueryMapCsvReaderTest {
    @Test
    void shouldReadNormalQueries() {
        QueryFileReader queryReader = new HcQueryMapCsvReader(new QueryFilter());
        List<String> queries = queryReader.read(new File("src/test/resources/queries_map_1.csv"));
        assertTrue(queries.size() == 9);
        assertTrue(queries.contains("MATCH (t:Thing) RETURN t"));
        assertTrue(queries.contains("MATCH (this1:A:B:C:D)-[:multi]->(:ff) \nreturn this1"));
    }
}
