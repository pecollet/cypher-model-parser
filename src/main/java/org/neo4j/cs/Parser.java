package org.neo4j.cs;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.sourceforge.plantuml.FileFormat;
import org.neo4j.cs.model.Model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.io.*;


public class Parser {

    public static void main(String... a) {
        String queriesFilePath;
        if (a.length == 0) {
            System.out.println("Expecting at least a parameter : queriesFilePath");
            return;
        } else {
            queriesFilePath = a[0];
            System.out.println("Reading from file: "+queriesFilePath);
        }
        String outputDir;
        if (a.length == 1) {
            outputDir = ".";
        } else {
            outputDir = a[1];
        }
        Path outputDirAbsolutePath = Paths.get(outputDir).toAbsolutePath();
        System.out.println("Output written to directory: "+outputDirAbsolutePath);

        QueryFileReader queryReader = new HcQueryMapCsvReader(new QueryFilter());
        List<String> queries = queryReader.read(queriesFilePath);
        System.out.println("Number of queries to parse: " + queries.size());

        QueryParser parser = new QueryParser();
        Model fullModel = parser.parseQueries(queries);
        //fullModel.filterIsolatedRelationships();

        saveJson(fullModel, outputDir+ "/model.json");
        fullModel.savePlantUml(outputDir+ "/model.puml", FileFormat.SVG);
    }


    private static void saveJson(Object o, String filePath) {
        System.out.println("Saving to JSON...");
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.writeValue(new File(filePath), o);
        } catch (Exception e) {
            System.out.println("Failed to write JSON : " +e.toString());
        }
        System.out.println("Successfully written to "+ filePath);
    }


}
