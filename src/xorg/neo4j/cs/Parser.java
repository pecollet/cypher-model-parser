package xorg.neo4j.cs;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.sourceforge.plantuml.SourceStringReader;
import org.neo4j.cypherdsl.core.StatementCatalog.Property;

import org.neo4j.cypherdsl.core.StatementCatalog.Token;
import org.neo4j.cypherdsl.parser.CyperDslParseException;
import org.neo4j.cypherdsl.parser.CypherParser;
import org.neo4j.cypherdsl.parser.UnsupportedCypherException;
import xorg.neo4j.cs.model.Model;
import xorg.neo4j.cs.model.NodeLabel;
import xorg.neo4j.cs.model.RelationshipType;

import java.util.*;
import java.util.stream.Collectors;
import java.io.*;


import static org.neo4j.cypherdsl.core.StatementCatalog.Token.Type.NODE_LABEL;
import static org.neo4j.cypherdsl.core.StatementCatalog.Token.Type.RELATIONSHIP_TYPE;

public class Parser {

    public static void main(String... a) {
        String filePath = a[0];
        List<String> queries = readQueriesFromFile(filePath);
        System.out.println("Number of queries to parse: " + queries.size());
        Model fullModel = new Model();

        //iterate over queries
        System.out.println("Parsing queries...");
        for(String q : queries) {
            fullModel.add(parseQuery(q));
        }
        System.out.println("Parsing complete.");

        //TODO : cleanup undirectedNodeLabels sets if labels are found in source or target sets
//        for (Map.Entry<String, RelationshipType> rtEntry: fullModel.getRelationshipTypes().entrySet()) {
//            if (rtEntry.getKey().equals("HAS_SWAD")) {
//                RelationshipType rt = rtEntry.getValue();
//                System.out.println(rt);
//                Set<String> relTypesWithKnowSource = rt.getSourceNodeLabels();
//                Set<String> relTypesWithKnowTarget = rt.getTargetNodeLabels();
////                rt.getUndirectedNodeLabels().removeAll(relTypesWithKnowSource);
//                Set<String> tmp = rt.getUndirectedNodeLabels();
//                System.out.println("tmp="+tmp);
//                System.out.println("relTypesWithKnowTarget="+relTypesWithKnowTarget);
//                System.out.println("rt="+rt);
//                tmp.removeAll(relTypesWithKnowTarget);
//                System.out.println("tmp="+tmp);
//                System.out.println("rt="+rt);
//                System.out.println("relTypesWithKnowTarget="+relTypesWithKnowTarget);
//                rt.setTargetNodeLabels(relTypesWithKnowTarget);
//                rt.setUndirectedNodeLabels(tmp);
//                System.out.println("rt="+rt);
//            }
//        }
        System.out.println(fullModel);
        //saveJson(fullModel, "target/model.json");
        savePlantUml(fullModel, "target/model.puml");
    }

    private static ArrayList<String> readQueriesFromFile(String filePath) {
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

                //ignore explain queries (sometimes used for autocomplete)
                //ignore admin queries like SHOW DATABASES
                if (!value.startsWith("EXPLAIN") && !value.startsWith("SHOW ")) {

                    // cleanup known unsupported cypher syntax
                    queries.add(
                            value
                                .replaceAll("<br>", " ")   //HTML spacing introduced by the HC
                                .replaceAll("^(CYPHER|cypher)\s+(RUNTIME|runtime)=(INTERPRETED|interpreted|SLOTTED|slotted|PARALLEL|parallel)\s*(expressionEngine=(INTERPRETED|interpreted))?", "")
                    );
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return queries;
    }

    private static Model parseQuery(String query) {
        Model queryModel = new Model();
//        System.out.println("QUERY = " +query);
        Map<String, NodeLabel> nodeLabels = new HashMap<>();
        Map<String, RelationshipType> relationshipTypes = new HashMap<>();

        //parse
        try {
            var statement = CypherParser.parse(query);
            var catalog = statement.getCatalog();

            //node labels : populate a map of nodeLabels (w/o properties)
            for (Token label : catalog.getNodeLabels()) {
                nodeLabels.put(label.value(), new NodeLabel(label.value()));
            }

            //relationshipTypes : populate a map (w/o properties)
            for (Token type : catalog.getRelationshipTypes()) {
                List<String> sourceNodeLabels = catalog.getSourceNodes(type).stream()
                        .map(t -> t.value()).collect(Collectors.toList());
                List<String> targetNodeLabels = catalog.getTargetNodes(type).stream()
                        .map(t -> t.value()).collect(Collectors.toList());
                RelationshipType rt = new RelationshipType(type.value());
                rt.setSourceNodeLabels( new HashSet<>(sourceNodeLabels) );
                rt.setTargetNodeLabels( new HashSet<>(targetNodeLabels) );
                relationshipTypes.put(type.value(), rt);
            }

            //fill in the properties
            for (Property prop : catalog.getProperties()) {
                String key = prop.name();
                for (String label : prop.owningToken().stream()
                                                    .filter( t -> t.type().equals(NODE_LABEL))
                                                    .map(t -> t.value())
                                                    .collect(Collectors.toList())) {
                    nodeLabels.get(label).addProperty(key);
                }
                for (String type : prop.owningToken().stream()
                        .filter( t -> t.type().equals(RELATIONSHIP_TYPE))
                        .map(t -> t.value())
                        .collect(Collectors.toList())) {
                    relationshipTypes.get(type).addProperty(key);
                }
            }

            queryModel.setNodeLabels(nodeLabels);
            queryModel.setRelationshipTypes(relationshipTypes);
        } catch (CyperDslParseException e) {
            String cause = e.getCause().toString();
            System.out.println("CyperDslParseException: " +cause.substring(1, Math.min(110, cause.length())));
            System.out.println(query);
        } catch (UnsupportedCypherException e) {
            String cause = e.getCause().toString();
            System.out.println("UnsupportedCypherException: " +cause.substring(1, Math.min(110, cause.length())));
            System.out.println(query);
        } catch (Exception e) {
            System.out.println(e);
            System.out.println(query);
        }
        return queryModel;
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

    private static void savePlantUml(Model m, String filePath) {
        String plantUmlStr = m.asPlantUml();
        System.out.println(plantUmlStr);
        SourceStringReader reader = new SourceStringReader(plantUmlStr);

        try {
            //generate PNG directly? requires DOT
//            OutputStream png = new FileOutputStream(new File(filePath));
//            String desc = reader.outputImage(png).getDescription();
//            System.out.println("savePlantUml: " + desc);

            //export the plantUML syntax
            // that can be turned into a PNG via a plantUML server (public server plantuml.com by default)
            //cf. https://github.com/SamuelMarks/python-plantuml
            //pip install plantuml
            //python3 path/to/plantuml.py model.puml
            BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
            writer.write(plantUmlStr);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
