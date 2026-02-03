package org.neo4j.cs;

import lombok.Getter;
import org.neo4j.cs.ast.AstUtils;
import org.neo4j.cs.ast.SimpleCypherExceptionFactory;
import org.neo4j.cs.model.*;
import org.neo4j.cs.model.NodeLabel;

import org.neo4j.cypher.internal.CypherVersion;
import org.neo4j.cypher.internal.ast.Statement;
import org.neo4j.cypher.internal.parser.ast.AstParser;
import org.neo4j.cs.ast.CypherAstSchemaCollector;
import org.neo4j.cs.ast.CypherAstSchemaCollector.RelationshipDescriptorDTO;

import java.util.*;

public class QueryParser {

    @Getter
    private int errors = 0;

    public QueryParser() {
    }
    public Model parseQueries(List<String> queries) {
        return this.parseQueries(queries, CypherVersion.Cypher25);
    }
    public Model parseQueries(List<String> queries, CypherVersion version) {
        this.errors = 0;
        Model fullModel = new Model();
        //iterate over queries
        System.out.print("Parsing queries... ");
        for(String q : queries) {
            fullModel.add(parseQuery(q, version));
        }

        if (queries.size() > 0) {
            if (this.errors == 0) {
                System.out.println("Parsing complete. 100% success.");
            } else {
                double successRate = 100.0 - (100.0 * this.errors / queries.size());
                System.out.println("Parsing complete. "+String.format("%.2f",successRate)+"% success (skipped " +  this.errors +" out of "+queries.size()+")");
            }
        } else {
            System.out.println("No queries to parse.");
        }

        //cleanup undirectedNodeLabels sets if labels are found in source or target sets
        for (Map.Entry<String, RelationshipType> rtEntry: fullModel.getRelationshipTypes().entrySet()) {
            RelationshipType rt = rtEntry.getValue();
            if (rt.getUndirectedNodeLabels().size() >0) {
                rt.getUndirectedNodeLabels().removeAll(rt.getSourceNodeLabels());
                rt.getUndirectedNodeLabels().removeAll(rt.getTargetNodeLabels());
            }

            rt.dedupeProperties();
        }

        //dedupe properties
        for (Map.Entry<String, NodeLabel> nlEntry: fullModel.getNodeLabels().entrySet()) {
            NodeLabel nl = nlEntry.getValue();
            nl.dedupeProperties();
        }
//        System.out.println(fullModel);
        return fullModel;
    }

    public boolean isObfuscated(String query) {
        return query.contains("******");
    }

    public String preProcessObfuscatedQuery(String query) {
        return query.replaceAll("\\*\\*\\*\\*\\*\\*", "123456");
    }


    public Model parseQuery(String query) {
        return this.parseQuery(query, CypherVersion.Cypher25);
    }
    public Model parseQuery(String query, CypherVersion version) {
        Model queryModel = new Model();
        Map<String, NodeLabel> nodeLabels = new HashMap<>();
        Map<String, RelationshipType> relationshipTypes = new HashMap<>();

        if (isObfuscated(query)) {
            query = preProcessObfuscatedQuery(query);
        }
        try {
            AstParser parser = AstUtils.getCypherParser(query, version, new SimpleCypherExceptionFactory());
            Statement statement = parser.singleStatement();
//            System.out.println(statement);

            List<CypherAstSchemaCollector.RelationshipDescriptorDTO> rels =
                    CypherAstSchemaCollector.collectRelationshipsDTO(statement);
            for (RelationshipDescriptorDTO r : rels) {
                RelationshipType rt = new RelationshipType(r.relType());
                rt.setSourceNodeLabels( new HashSet<>(r.sourceLabels()) );
                rt.setTargetNodeLabels( new HashSet<>(r.targetLabels()) );
                relationshipTypes.put(r.relType(), rt);
            }
            Set<String> labels = CypherAstSchemaCollector.collectLabels(statement);
            for (String label : labels) {
                nodeLabels.put(label, new NodeLabel(label));
            }

            List<CypherAstSchemaCollector.PropertyDescriptorDTO> props =
                    CypherAstSchemaCollector.collectPropertiesDTO(statement);

            for (CypherAstSchemaCollector.PropertyDescriptorDTO prop : props) {
                if (prop.ownerIsNode()) {
                    nodeLabels.get(prop.ownerName()).addProperty(prop.propertyKey(), prop.propertyType());
                } else {
                    relationshipTypes.get(prop.ownerName()).addProperty(prop.propertyKey(), prop.propertyType());
                }
            }

            queryModel.setNodeLabels(nodeLabels);
            queryModel.setRelationshipTypes(relationshipTypes);
        } catch (Exception e) {
            System.err.println("### [Exception] " + e + " : " +shortenQueryForLogging(query) );
            this.errors+=1;
        }
        return queryModel;
    }

    private String shortenQueryForLogging(String query) {
        return query.replaceAll("\n", " ").substring(0, Math.min(100, query.length()));
    }

}
