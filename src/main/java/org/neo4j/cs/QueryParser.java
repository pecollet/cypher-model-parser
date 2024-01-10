package org.neo4j.cs;

import lombok.Getter;
import org.neo4j.cs.model.Model;
import org.neo4j.cs.model.NodeLabel;
import org.neo4j.cs.model.RelationshipType;
import org.neo4j.cypherdsl.core.StatementCatalog;
import org.neo4j.cypherdsl.parser.CyperDslParseException;
import org.neo4j.cypherdsl.parser.CypherParser;
import org.neo4j.cypherdsl.parser.UnsupportedCypherException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.neo4j.cypherdsl.core.StatementCatalog.Token.Type.NODE_LABEL;
import static org.neo4j.cypherdsl.core.StatementCatalog.Token.Type.RELATIONSHIP_TYPE;

public class QueryParser {

    @Getter
    private int errors = 0;

    public Model parseQueries(List<String> queries) {
        this.errors = 0;
        Model fullModel = new Model();
        //iterate over queries
        System.out.println("Parsing queries...");
        for(String q : queries) {
            fullModel.add(parseQuery(q));
        }
        System.out.println("Parsing complete. Errors: "+this.errors);
//        System.out.println(fullModel);
        return fullModel;

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
    }

    public Model parseQuery(String query) {
        Model queryModel = new Model();
//        System.out.println("QUERY = " +query);
        Map<String, NodeLabel> nodeLabels = new HashMap<>();
        Map<String, RelationshipType> relationshipTypes = new HashMap<>();

        //parse
        try {
            var statement = CypherParser.parse(query);
            var catalog = statement.getCatalog();

            //node labels : populate a map of nodeLabels (w/o properties)
            for (StatementCatalog.Token label : catalog.getNodeLabels()) {
                nodeLabels.put(label.value(), new NodeLabel(label.value()));
            }

            //relationshipTypes : populate a map (w/o properties)
            for (StatementCatalog.Token type : catalog.getRelationshipTypes()) {
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
            for (StatementCatalog.Property prop : catalog.getProperties()) {
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
            System.out.println("#####[CyperDslParseException] " +cause.substring(0, Math.min(110, cause.length())));
            System.out.println(query);
            this.errors+=1;
        } catch (UnsupportedCypherException e) {
            String cause = e.getCause().toString();
            System.out.println("#####   [UnsupportedCypherException] " +cause.substring(0, Math.min(110, cause.length())));
            System.out.println(query);
            this.errors+=1;
        } catch (Exception e) {
            System.out.println(e);
            System.out.println(query);
            this.errors+=1;
        }
        return queryModel;
    }
}
