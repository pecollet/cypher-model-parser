package org.neo4j.cs;

import lombok.Getter;
import org.neo4j.cs.model.*;
import org.neo4j.cs.model.NodeLabel;
import org.neo4j.cypherdsl.core.*;
import org.neo4j.cypherdsl.core.StatementCatalog.PropertyFilter;
import org.neo4j.cypherdsl.core.StatementCatalog.Property;
import org.neo4j.cypherdsl.parser.CyperDslParseException;
import org.neo4j.cypherdsl.parser.CypherParser;
import org.neo4j.cypherdsl.parser.UnsupportedCypherException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.*;
import java.util.Set;
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
        float errorRate = 100 * this.errors / queries.size();
        System.out.println("Parsing complete. Errors: "+this.errors+" ("+errorRate+"%)");

        //cleanup undirectedNodeLabels sets if labels are found in source or target sets
        for (Map.Entry<String, RelationshipType> rtEntry: fullModel.getRelationshipTypes().entrySet()) {
            RelationshipType rt = rtEntry.getValue();
            if (rt.getUndirectedNodeLabels().size() >0) {
                rt.getUndirectedNodeLabels().removeAll(rt.getSourceNodeLabels());
                rt.getUndirectedNodeLabels().removeAll(rt.getTargetNodeLabels());
            }
        }
        //        System.out.println(fullModel);
        return fullModel;
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

            //node labels : populate a map of nodeLabels (w/o properties)
            for (StatementCatalog.Token label : catalog.getNodeLabels()) {
                nodeLabels.put(label.value(), new NodeLabel(label.value()));
//                System.out.println(label.value()+" => "+catalog.getUndirectedRelations(label));
                for  (StatementCatalog.Token undirRelType : catalog.getUndirectedRelations(label)) {

                    relationshipTypes.get(undirRelType.value()).addUndirectedNodelabel(label.value());
                }
            }

            //fill in the properties
            for (Property prop : catalog.getProperties()) {
                String key = prop.name();
                String propertyType = getPropertyType(prop, catalog);

                for (String label : prop.owningToken().stream()
                        .filter( t -> t.type().equals(NODE_LABEL))
                        .map(t -> t.value())
                        .collect(Collectors.toList())) {
                    if (propertyType != "?") {
                        nodeLabels.get(label).addProperty(key, propertyType);
                    } else {
                        nodeLabels.get(label).addProperty(key);
                    }
                }
                for (String type : prop.owningToken().stream()
                        .filter( t -> t.type().equals(RELATIONSHIP_TYPE))
                        .map(t -> t.value())
                        .collect(Collectors.toList())) {
                    if (propertyType != "?") {
                        relationshipTypes.get(type).addProperty(key, propertyType);
                    } else {
                        relationshipTypes.get(type).addProperty(key);
                    }
                }
            }

            for (Index index : parseIndex(query)) {
                EntityType entity;
//                if (index.getNodeLabel() != null) {
//                    //TODO : find label in nodeLabels or create it
//                    for (NodeLabel nodeLabel : nodeLabels) {
//                        if (nodeLabel.getLabel() == index.getNodeLabel())
//                    }
//                } else {
//                    //TODO : find type in relationshipTypes or create it
//                }
//                for (String propertyName : index.getProperties()) {
//                    boolean found = false;
//                    //find property within entity or create them
//                    for (org.neo4j.cs.model.Property entityProperty : entity.getProperties()) {
//                        if (entityProperty.getKey() == propertyName){
//                            found = true;
//                            entityProperty.setIndexed(true);
//                        }
//                    }
//                    //if not found, add it
//                    if (!found) {
//                        org.neo4j.cs.model.Property newProperty = new org.neo4j.cs.model.Property(propertyName);
//                        newProperty.setIndexed(true);
//                        entity.addProperty(newProperty);
//                    }
//                }
            }

            queryModel.setNodeLabels(nodeLabels);
            queryModel.setRelationshipTypes(relationshipTypes);
        } catch (CyperDslParseException e) {
            String parseExceptionText ="org.neo4j.cypherdsl.parser.internal.parser.javacc.ParseException:";
            String cause = e.getCause().toString();
            System.out.println("### [CyperDslParseException] " +
                    cause.replace(parseExceptionText, "")
                            .replaceAll("\n", " ")
                            .substring(0, Math.min(100, cause.length() - parseExceptionText.length()))
                    + " : " +shortenQueryForLogging(query)
            );
            this.errors+=1;
        } catch (UnsupportedCypherException e) {
            String cause = e.getCause().toString();
            System.out.println("### [UnsupportedCypherException] " +
                    cause.replaceAll("\n", " ")
                    .substring(0, Math.min(100, cause.length()))
                    + " : " +shortenQueryForLogging(query)
            );
            this.errors+=1;
        } catch (NullPointerException npe) {
            System.out.println(npe);
        } catch (Exception e) {
            System.out.println("### [Exception] " + e + " : " +shortenQueryForLogging(query) );
            this.errors+=1;
        }
//        System.out.println(queryModel);
        return queryModel;
    }

    private Set<Index> parseIndex(String query) {
        Set<Index> indices = new HashSet<>();
        //TODO : work on regex
        Pattern pattern = Pattern.compile("CREATE INDEX FOR \\((\\w)?:(\\w)\\) ON \\((\\w)?.(\\w)\\)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(query);
        boolean matchFound = matcher.find();
        if(matchFound) {
            String entity = matcher.group(1);
            String property = matcher.group(3);
            //TODO : figure out node or rel
            Index i = new Index(entity, property, Index.EntityType.NODE);
            indices.add(i);
        }
        return indices;
    }
    private String shortenQueryForLogging(String query) {
        return query.replaceAll("\n", " ").substring(0, Math.min(100, query.length()));
    }

    private String getPropertyType(Property prop, StatementCatalog catalog) {
//        System.out.println(prop.name());
        String propertyType="?";
        Collection<PropertyFilter> filters = catalog.getFilters(prop);
        if (filters != null) {
//            System.out.println(filters);
            //use filter expressions to try to find out the property types
            for (PropertyFilter filter: filters) {
                if (Literal.class.isInstance(filter.right())) {
                    if (StringLiteral.class.isInstance(filter.right())) {
                        propertyType = "String";
//                        System.out.println(" "+filter.right()+" String");
                    } else if (NumberLiteral.class.isInstance(filter.right())) {
                        propertyType = "Number";
//                        System.out.println(" "+filter.right()+" Number");
                    } else if (BooleanLiteral.class.isInstance(filter.right())) {
                        propertyType = "Boolean";
//                        System.out.println(" "+filter.right()+" Boolean");
                    } else {
                        System.out.println(" "+filter.right()+" Unknown literal type");
                    }
                }
                if (Literal.class.isInstance(filter.left())) {
                    if (StringLiteral.class.isInstance(filter.left())) {
                        propertyType = "String";
//                        System.out.println(" "+filter.left()+" String L");
                    } else if (NumberLiteral.class.isInstance(filter.left())) {
                        propertyType = "Number";
//                        System.out.println(" "+filter.left()+" Number L");
                    } else if (BooleanLiteral.class.isInstance(filter.left())) {
                        propertyType = "Boolean";
//                        System.out.println(" "+filter.left()+" Boolean L");
                    } else {
                        System.out.println(" "+filter.left()+" Unknown literal type L");
                    }
                }
            }
        }
        return propertyType;
    }
}
