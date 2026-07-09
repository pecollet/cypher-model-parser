package org.neo4j.cs.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NoArgsConstructor
@ToString
public class Model {
    @Getter
    @Setter
    Map<String, NodeLabel> nodeLabels = new HashMap();
    @Getter
    @Setter
    Map<String, RelationshipType> relationshipTypes = new HashMap<>();

    /**
     * Helper to merge property sets based on key uniqueness and type priority.
     */
    private Set<Property> mergeProperties(Set<Property> s1, Set<Property> s2) {
        return Stream.concat(s1.stream(), s2.stream())
                .collect(Collectors.groupingBy(Property::getKey)) // Group by name/key
                .values().stream()
                .map(props -> {
                    // 1. Filter out UNKNOWNs if there are known types available
                    List<Property> knownTypes = props.stream()
                            .filter(p -> !"UNKNOWN".equalsIgnoreCase(p.getType()))
                            .collect(Collectors.toList());

                    List<String> indexTypes = props.stream()
                            .flatMap(p -> p.getIndexTypes().stream())
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList());
                    List<String> constraintTypes = props.stream()
                            .flatMap(p -> p.getConstraintTypes().stream())
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList());
                    String key = props.get(0).getKey();

                    Property mergedProp;
                    if (knownTypes.isEmpty()) {
                        // Everything was UNKNOWN, just return one
                        mergedProp = new Property(key, props.get(0).getType());
                    } else if (knownTypes.size() == 1) {
                        // Only one typed property exists, it wins
                        mergedProp = new Property(key, knownTypes.get(0).getType());
                    } else {
                        // 2. Multiple different known types exist
                        // Check if they are actually all the same type
                        long distinctTypeCount = knownTypes.stream()
                                .map(Property::getType)
                                .distinct()
                                .count();

                        if (distinctTypeCount == 1) {
                            mergedProp = new Property(key, knownTypes.get(0).getType());
                        } else {
                            // Resolve ambiguity by forcing UNKNOWN
                            mergedProp = new Property(key, "UNKNOWN");
                        }
                    }
                    mergedProp.setIndexTypes(indexTypes);
                    mergedProp.setConstraintTypes(constraintTypes);
                    return mergedProp;
                })
                .collect(Collectors.toSet());
    }

    public Model add(Model m) {
        this.nodeLabels = Stream
                .concat(
                    this.nodeLabels.entrySet().stream(),
                    m.getNodeLabels().entrySet().stream()
                )
                .collect(
                    Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (value1, value2) -> {
                            Set<Property> mergedProps = mergeProperties(value1.getProperties(), value2.getProperties());
                            NodeLabel mergedNode = new NodeLabel(value1.getLabel(), mergedProps);
                            String mergedProv = value1.getProvenance().equals(value2.getProvenance()) ? value1.getProvenance() : "both";
                            mergedNode.setProvenance(mergedProv);
                            return mergedNode;
                        }
                    )
                );
        this.relationshipTypes = Stream
                .concat(
                        this.relationshipTypes.entrySet().stream(),
                        m.getRelationshipTypes().entrySet().stream()
                )
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (value1, value2) -> {
                                    RelationshipType mergedRelationshipType = new RelationshipType(value1.getType());

                                    mergedRelationshipType.setProperties(mergeProperties(value1.getProperties(), value2.getProperties()));

                                    //Merge source/target nodes lists
                                    Set<String> allSrcNodeLabels = new HashSet<>();
                                    allSrcNodeLabels.addAll(value1.getSourceNodeLabels());
                                    allSrcNodeLabels.addAll(value2.getSourceNodeLabels());
                                    mergedRelationshipType.setSourceNodeLabels(allSrcNodeLabels);

                                    Set<String> allTgtNodeLabels = new HashSet<>();
                                    allTgtNodeLabels.addAll(value1.getTargetNodeLabels());
                                    allTgtNodeLabels.addAll(value2.getTargetNodeLabels());
                                    mergedRelationshipType.setTargetNodeLabels(allTgtNodeLabels);

                                    Set<String> allUndirNodeLabels = new HashSet<>();
                                    allUndirNodeLabels.addAll(value1.getUndirectedNodeLabels());
                                    allUndirNodeLabels.addAll(value2.getUndirectedNodeLabels());
                                    mergedRelationshipType.setUndirectedNodeLabels(allUndirNodeLabels);

                                    Set<String> allConstrainedSrcNodeLabels = new HashSet<>();
                                    allConstrainedSrcNodeLabels.addAll(value1.getConstrainedSourceNodeLabels());
                                    allConstrainedSrcNodeLabels.addAll(value2.getConstrainedSourceNodeLabels());
                                    mergedRelationshipType.setConstrainedSourceNodeLabels(allConstrainedSrcNodeLabels);

                                    Set<String> allConstrainedTgtNodeLabels = new HashSet<>();
                                    allConstrainedTgtNodeLabels.addAll(value1.getConstrainedTargetNodeLabels());
                                    allConstrainedTgtNodeLabels.addAll(value2.getConstrainedTargetNodeLabels());
                                    mergedRelationshipType.setConstrainedTargetNodeLabels(allConstrainedTgtNodeLabels);

                                    String mergedProv = value1.getProvenance().equals(value2.getProvenance()) ? value1.getProvenance() : "both";
                                    mergedRelationshipType.setProvenance(mergedProv);
                                    return mergedRelationshipType;
                                }
                        )
                );
        return this;
    }

    public void filterIsolatedRelationships() {
        List<String> keys_to_filter = new ArrayList<>();
        for (Map.Entry<String, RelationshipType> rtEntry: this.getRelationshipTypes().entrySet()) {
            RelationshipType rt = rtEntry.getValue();
            if (rt.getUndirectedNodeLabels().size() + rt.getSourceNodeLabels().size() + rt.getTargetNodeLabels().size() == 0) {
                keys_to_filter.add(rt.type);

            }
        }
        for (String key: keys_to_filter) {
            this.relationshipTypes.remove(key);
        }
    }

    public String asPlantUml() {

        String nodeStatements = this.getNodeLabels().entrySet().stream()
                .map(e -> e.getValue().asPlantUml())
                .collect(Collectors.joining("\n"));
        String relStatements = this.getRelationshipTypes().entrySet().stream()
                .map(e -> e.getValue().asPlantUml())
                .collect(Collectors.joining("\n"));

        return nodeStatements + "\n" + relStatements;
    }

}
