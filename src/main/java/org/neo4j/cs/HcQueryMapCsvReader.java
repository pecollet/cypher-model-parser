package org.neo4j.cs;

import lombok.Getter;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class HcQueryMapCsvReader implements QueryFileReader {

    private QueryFilter filter;

    public HcQueryMapCsvReader(QueryFilter filter) {
        this.filter = filter;
    }

    public List<String> read(String filePath) {
        ArrayList<String> queries = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath)) {
            Properties properties = new Properties();
            properties.load(fis);

            // Extract values and add them to the ArrayList
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (value != null && value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1); // Remove opening and closing double quotes
                }
                //HTML spacing introduced by the HC
                queries.add( value .replaceAll("<br>", " ") );
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return this.filter.filter(queries);
    }
}
