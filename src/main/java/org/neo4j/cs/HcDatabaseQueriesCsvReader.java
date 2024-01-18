package org.neo4j.cs;


import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

public class HcDatabaseQueriesCsvReader implements QueryFileReader {

    private QueryFilter filter;

    public HcDatabaseQueriesCsvReader(QueryFilter filter) {
        this.filter = filter;
    }

    public List<String> read(File file) {
        ArrayList<String> queries = new ArrayList<>();


        try (CSVReader csvReader = new CSVReader(new FileReader(file))) {
            String[] nextRecord;

            while ((nextRecord = csvReader.readNext()) != null) {
                if (nextRecord.length > 0) {
                    // Assuming only one column in the CSV file
                    String q = nextRecord[0];

                    // Remove leading and trailing double quotes
                    q = q.replaceAll("^\"|\"$", "");

                    //HTML spacing introduced by the HC
                    queries.add( q .replaceAll("<br>", "\n") );
                }

            }
        } catch (IOException | CsvValidationException e) {
            e.printStackTrace();
        }

        return this.filter.filter(queries);
    }
}
