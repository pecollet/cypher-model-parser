package org.neo4j.cs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.cs.model.Model;
import org.neo4j.cs.model.NodeLabel;
import org.neo4j.cs.model.RelationshipType;
import org.neo4j.cs.model.Property;
import org.neo4j.cs.model.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GraphCountsParser {

    public static Model parse(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        
        JsonNode dataNode = null;
        if (root.isArray()) {
            for (JsonNode element : root) {
                if (element.has("section") && "GRAPH COUNTS".equals(element.get("section").asText())) {
                    if (element.has("data")) {
                        dataNode = element.get("data");
                        break;
                    }
                }
            }
            if (dataNode == null && root.size() > 0) {
                JsonNode first = root.get(0);
                if (first.has("data")) {
                    dataNode = first.get("data");
                } else if (first.has("nodes") || first.has("relationships")) {
                    dataNode = first;
                }
            }
        } else if (root.isObject()) {
            if (root.has("data")) {
                dataNode = root.get("data");
            } else if (root.has("nodes") || root.has("relationships")) {
                dataNode = root;
            }
        }

        Model model = new Model();
        if (dataNode == null) {
            return model;
        }

        GraphCountsParser parser = new GraphCountsParser();
        parser.parseNodes(dataNode.get("nodes"), model);
        parser.parseRelationships(dataNode.get("relationships"), model);
        parser.parseIndexes(dataNode.get("indexes"), model);
        parser.parseConstraints(dataNode.get("constraints"), model);

        return model;
    }

    private void parseNodes(JsonNode nodesNode, Model model) {
        if (nodesNode == null || !nodesNode.isArray()) return;
        for (JsonNode node : nodesNode) {
            if (node.has("label") && !node.get("label").isNull()) {
                String label = node.get("label").asText();
                getOrCreateNodeLabel(model, label);
            }
        }
    }

    private void parseRelationships(JsonNode relsNode, Model model) {
        if (relsNode == null || !relsNode.isArray()) return;
        for (JsonNode rel : relsNode) {
            if (rel.has("relationshipType") && !rel.get("relationshipType").isNull()) {
                String type = rel.get("relationshipType").asText();
                RelationshipType rt = getOrCreateRelationshipType(model, type);
                if (rt != null) {
                    if (rel.has("startLabel") && !rel.get("startLabel").isNull()) {
                        String startLabel = rel.get("startLabel").asText();
                        rt.getSourceNodeLabels().add(startLabel);
                        getOrCreateNodeLabel(model, startLabel);
                    }
                    if (rel.has("endLabel") && !rel.get("endLabel").isNull()) {
                        String endLabel = rel.get("endLabel").asText();
                        rt.getTargetNodeLabels().add(endLabel);
                        getOrCreateNodeLabel(model, endLabel);
                    }
                }
            }
        }
    }

    private void parseIndexes(JsonNode indexesNode, Model model) {
        if (indexesNode == null || !indexesNode.isArray()) return;
        for (JsonNode index : indexesNode) {
            List<String> props = new ArrayList<>();
            if (index.has("properties") && index.get("properties").isArray()) {
                for (JsonNode prop : index.get("properties")) {
                    props.add(prop.asText());
                }
            }
            if (props.isEmpty()) continue;

            String indexType = index.has("indexType") && !index.get("indexType").isNull() ? index.get("indexType").asText() : "RANGE";

            if (index.has("labels") && index.get("labels").isArray() && index.get("labels").size() > 0) {
                for (JsonNode labelNode : index.get("labels")) {
                    String labelName = labelNode.asText();
                    NodeLabel nl = getOrCreateNodeLabel(model, labelName);
                    if (nl != null) {
                        for (String propName : props) {
                            Property p = getOrCreateProperty(nl, propName);
                            p.setIndexType(indexType);
                        }
                    }
                }
            } else if (index.has("relationshipTypes") && index.get("relationshipTypes").isArray() && index.get("relationshipTypes").size() > 0) {
                for (JsonNode typeNode : index.get("relationshipTypes")) {
                    String typeName = typeNode.asText();
                    RelationshipType rt = getOrCreateRelationshipType(model, typeName);
                    if (rt != null) {
                        for (String propName : props) {
                            Property p = getOrCreateProperty(rt, propName);
                            p.setIndexType(indexType);
                        }
                    }
                }
            }
        }
    }

    private void parseConstraints(JsonNode constraintsNode, Model model) {
        if (constraintsNode == null || !constraintsNode.isArray()) return;
        for (JsonNode constraint : constraintsNode) {
            if (!constraint.has("type") || constraint.get("type").isNull()) continue;
            String type = constraint.get("type").asText();

            List<String> props = new ArrayList<>();
            if (constraint.has("properties") && constraint.get("properties").isArray()) {
                for (JsonNode prop : constraint.get("properties")) {
                    props.add(prop.asText());
                }
            }

            String labelName = constraint.has("label") && !constraint.get("label").isNull() ? constraint.get("label").asText() : null;
            String relType = constraint.has("relationshipType") && !constraint.get("relationshipType").isNull() ? constraint.get("relationshipType").asText() : null;

            if ("Property type constraint".equals(type)) {
                List<String> propTypes = new ArrayList<>();
                if (constraint.has("propertyTypes") && constraint.get("propertyTypes").isArray()) {
                    for (JsonNode propType : constraint.get("propertyTypes")) {
                        propTypes.add(propType.asText());
                    }
                }
                if (labelName != null) {
                    NodeLabel nl = getOrCreateNodeLabel(model, labelName);
                    if (nl != null) {
                        for (int i = 0; i < props.size(); i++) {
                            String propName = props.get(i);
                            String rawType = i < propTypes.size() ? propTypes.get(i) : "UNKNOWN";
                            Property p = getOrCreateProperty(nl, propName);
                            p.setType(mapPropertyType(rawType));
                            p.setConstraintType("PropertyType");
                        }
                    }
                } else if (relType != null) {
                    RelationshipType rt = getOrCreateRelationshipType(model, relType);
                    if (rt != null) {
                        for (int i = 0; i < props.size(); i++) {
                            String propName = props.get(i);
                            String rawType = i < propTypes.size() ? propTypes.get(i) : "UNKNOWN";
                            Property p = getOrCreateProperty(rt, propName);
                            p.setType(mapPropertyType(rawType));
                            p.setConstraintType("PropertyType");
                        }
                    }
                }
            } else if ("Existence constraint".equals(type)) {
                if (labelName != null) {
                    NodeLabel nl = getOrCreateNodeLabel(model, labelName);
                    if (nl != null) {
                        for (String propName : props) {
                            Property p = getOrCreateProperty(nl, propName);
                            p.setConstraintType("Existence");
                        }
                    }
                } else if (relType != null) {
                    RelationshipType rt = getOrCreateRelationshipType(model, relType);
                    if (rt != null) {
                        for (String propName : props) {
                            Property p = getOrCreateProperty(rt, propName);
                            p.setConstraintType("Existence");
                        }
                    }
                }
            } else if ("Uniqueness constraint".equals(type) || "Unique constraint".equals(type)) {
                if (labelName != null) {
                    NodeLabel nl = getOrCreateNodeLabel(model, labelName);
                    if (nl != null) {
                        for (String propName : props) {
                            Property p = getOrCreateProperty(nl, propName);
                            p.setConstraintType("Uniqueness");
                        }
                    }
                } else if (relType != null) {
                    RelationshipType rt = getOrCreateRelationshipType(model, relType);
                    if (rt != null) {
                        for (String propName : props) {
                            Property p = getOrCreateProperty(rt, propName);
                            p.setConstraintType("Uniqueness");
                        }
                    }
                }
            } else if ("Key constraint".equals(type) || "Node key constraint".equals(type)) {
                if (labelName != null) {
                    NodeLabel nl = getOrCreateNodeLabel(model, labelName);
                    if (nl != null) {
                        for (String propName : props) {
                            Property p = getOrCreateProperty(nl, propName);
                            p.setConstraintType("Key");
                        }
                    }
                } else if (relType != null) {
                    RelationshipType rt = getOrCreateRelationshipType(model, relType);
                    if (rt != null) {
                        for (String propName : props) {
                            Property p = getOrCreateProperty(rt, propName);
                            p.setConstraintType("Key");
                        }
                    }
                }
            } else if ("Node label existence constraint".equals(type)) {
                getOrCreateNodeLabel(model, labelName);
                if (constraint.has("enforcedLabel") && !constraint.get("enforcedLabel").isNull()) {
                    getOrCreateNodeLabel(model, constraint.get("enforcedLabel").asText());
                }
            } else if ("Relationship endpoint label constraint".equals(type)) {
                if (relType != null && constraint.has("enforcedLabel") && !constraint.get("enforcedLabel").isNull() && constraint.has("endpointType") && !constraint.get("endpointType").isNull()) {
                    String enforcedLabel = constraint.get("enforcedLabel").asText();
                    String endpointType = constraint.get("endpointType").asText();
                    RelationshipType rt = getOrCreateRelationshipType(model, relType);
                    if (rt != null) {
                        getOrCreateNodeLabel(model, enforcedLabel);
                        if ("START".equalsIgnoreCase(endpointType)) {
                            rt.getSourceNodeLabels().add(enforcedLabel);
                        } else if ("END".equalsIgnoreCase(endpointType)) {
                            rt.getTargetNodeLabels().add(enforcedLabel);
                        }
                    }
                }
            }
        }
    }

    private NodeLabel getOrCreateNodeLabel(Model model, String labelName) {
        if (labelName == null || labelName.isEmpty()) return null;
        NodeLabel nl = model.getNodeLabels().get(labelName);
        if (nl == null) {
            nl = new NodeLabel(labelName);
            nl.setProvenance("graphcounts");
            model.getNodeLabels().put(labelName, nl);
        }
        return nl;
    }

    private RelationshipType getOrCreateRelationshipType(Model model, String typeName) {
        if (typeName == null || typeName.isEmpty()) return null;
        RelationshipType rt = model.getRelationshipTypes().get(typeName);
        if (rt == null) {
            rt = new RelationshipType(typeName);
            rt.setProvenance("graphcounts");
            model.getRelationshipTypes().put(typeName, rt);
        }
        return rt;
    }

    private Property getOrCreateProperty(EntityType entity, String key) {
        for (Property p : entity.getProperties()) {
            if (p.getKey().equals(key)) {
                return p;
            }
        }
        Property newProp = new Property(key, "UNKNOWN");
        entity.addProperty(newProp);
        return newProp;
    }

    private String mapPropertyType(String neo4jType) {
        if (neo4jType == null) return "UNKNOWN";
        switch (neo4jType.toUpperCase()) {
            case "INTEGER":
            case "FLOAT":
            case "NUMBER":
                return "Number";
            case "STRING":
                return "String";
            case "BOOLEAN":
                return "Boolean";
            case "DATE":
            case "LOCAL DATE":
                return "Date";
            case "TIME":
            case "LOCAL TIME":
            case "ZONED TIME":
                return "Time";
            case "DATETIME":
            case "LOCAL DATETIME":
            case "ZONED DATETIME":
                return "DateTime";
            case "DURATION":
                return "Duration";
            case "POINT":
                return "Point";
            case "LIST":
                return "List";
            default:
                if (neo4jType.length() > 0) {
                    return neo4jType.substring(0, 1).toUpperCase() + neo4jType.substring(1).toLowerCase();
                }
                return "UNKNOWN";
        }
    }
}
