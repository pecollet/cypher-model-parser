package org.neo4j.cs;

import java.util.List;

public interface QueryFileReader {

    public List<String> read(String filePath);
}
