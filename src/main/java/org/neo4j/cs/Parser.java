package org.neo4j.cs;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import org.neo4j.cs.model.Model;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.io.*;
import java.util.concurrent.Callable;


@Command(name = "Parser", mixinStandardHelpOptions = true, versionProvider = ManifestVersionProvider.class,
        description = "Parses cypher queries in <queriesFile> and generates a PlantUML class diagram.")
public class Parser implements Callable<Integer> {

    private enum LayoutEngine { DOT, SMETANA }

    @Parameters(index = "0", description = "The file containing the queries")
    private java.io.File queriesFile;

    @Option(names = { "-o", "--output-dir" }, paramLabel = "OUTPUT-DIR", description = "The directory where the output files are written")
    private Path outputDir;

    @Option(names = { "-l", "--layout-engine" }, paramLabel = "layout-engine", description = "The layout engine to use when exporting a diagram picture : [SMETANA|DOT]. If not specified, an attempt is made to use DOT if present. DOT requires the presence of the graphviz module on the system.")
    private LayoutEngine layoutEngine;

    @Option(names = { "-j", "--json" }, description = "Export JSON model.")
    private boolean exportJson;


    @Override
    public Integer call() throws Exception {
        System.out.println("#### CYPHER-MODEL-PARSER ####");
        if (!queriesFile.isFile() || !queriesFile.canRead()) {
            System.out.println("File not found, or file not readable : "+queriesFile);
            return 1;
        }
        if (outputDir == null) {
            outputDir=Path.of(System.getProperty("user.dir"));
        } else {
            if (!Files.exists(outputDir) || !Files.isDirectory(outputDir)) {
                System.out.println("Directory does not exist or not a directory : "+outputDir);
                return 2;
            }
        }
        if (!Files.isWritable(outputDir)) {
            System.out.println("Directory is not writable : "+outputDir);
            return 3;
        }

       System.out.println("Output directory: "+outputDir.toAbsolutePath());

        //TODO : read directly from query.log (PLAIN and JSON formats)
        QueryFileReader queryReader = new HcDatabaseQueriesCsvReader(new QueryFilter());
        List<String> queries = queryReader.read(queriesFile);
        System.out.println("Number of queries to parse: " + queries.size());


        if (layoutEngine == null) {
            // -l option not specified, check for the presence of "dot" command
            if (isDotAvailable()) {
                layoutEngine = LayoutEngine.DOT;
            } else {
                // If "dot" command is not present, use default layout engine
                layoutEngine = LayoutEngine.SMETANA;
            }
        }

        System.out.println("Layout engine: " + layoutEngine.name());

        QueryParser parser = new QueryParser();
        Model fullModel = parser.parseQueries(queries);
        //fullModel.filterIsolatedRelationships();

        if (exportJson) {
            saveJson(fullModel, outputDir + "/model.json");
        }
        int retCode = savePlantUml(fullModel, outputDir+ "/model.puml", FileFormat.SVG, layoutEngine);
        return retCode;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new Parser()).execute(args);
        System.exit(exitCode);
    }


    private static void saveJson(Object o, String filePath) {
        System.out.println("Saving to JSON...");
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.writeValue(new File(filePath), o);
        } catch (Exception e) {
            System.out.println("Failed to write JSON : " +e.toString());
        }
        System.out.println("Successfully written model to "+ filePath);
    }

    public int savePlantUml(Model m, String filePath, FileFormat imageFormat, LayoutEngine engine) {
        String prefix = "@startuml\n";
        String layoutCommand = "";
        if (engine == LayoutEngine.SMETANA) layoutCommand = "!pragma layout smetana\n";
        String stylingCommands =  "scale max 900 width\n" +
                "set namespaceSeparator none\n" +
                "hide empty members\n";
        String suffix = "\n@enduml";
        String plantUmlStr = prefix + layoutCommand + stylingCommands + m.asPlantUml() + suffix;

        SourceStringReader reader = new SourceStringReader(plantUmlStr);
        try {
            System.out.println("Saving to plantUML text format...");
            BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
            writer.write(plantUmlStr);
            writer.close();
            System.out.println("Successfully written class diagram to "+ filePath);

            if (imageFormat != null ) {
                //generate Image
                String imageFileSuffix = "." +imageFormat.name().toLowerCase(Locale.ROOT);
                System.out.println("Saving to "+imageFormat.name()+"...");
                OutputStream os = new FileOutputStream(new File(filePath + imageFileSuffix));
                FileFormatOption option = new FileFormatOption(imageFormat);
                try {
                    String desc = reader.outputImage(os, option).getDescription();
                    System.out.println("Successfully written "+imageFormat.name()+" to "+ filePath + imageFileSuffix + " "+ desc);
                } catch (Exception e) {
                    System.out.println("XXX");
                }

                os.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 2;
        }
        return 0;
    }

    private boolean isDotAvailable() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("dot", "-V");
            Process process = processBuilder.inheritIO().start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("DOT found.");
                return true;
            }
        } catch (IOException | InterruptedException e) {
            // Exception occurred, command is not present or couldn't be executed
            System.out.println("Could not find DOT: " + e.getMessage());
        }
        return false;
    }

}
