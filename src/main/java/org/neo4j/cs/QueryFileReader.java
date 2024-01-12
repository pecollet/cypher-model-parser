package org.neo4j.cs;

import java.io.File;
import java.util.List;

public interface QueryFileReader {

    public List<String> read(File file);
}
