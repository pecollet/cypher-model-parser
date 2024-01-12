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
                            //TODO : keep typed properties ahead of untyped ones
                            Set<Property> allProperties = new HashSet<>();
                            allProperties.addAll(value1.getProperties());
                            allProperties.addAll(value2.getProperties());
                            return new NodeLabel(value1.getLabel(), allProperties);
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

                                    Set<Property> allProperties = new HashSet<>();
                                    allProperties.addAll(value1.getProperties());
                                    allProperties.addAll(value2.getProperties());
                                    mergedRelationshipType.setProperties(allProperties);

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
