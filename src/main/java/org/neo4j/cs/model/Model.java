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
        String prefix = "@startuml\n" +
                "!pragma layout smetana\n" +
                "scale max 900 width\n" +
                "set namespaceSeparator none\n" +
                "hide empty members\n";
        String nodeStatements = this.getNodeLabels().entrySet().stream()
                .map(e -> e.getValue().asPlantUml())
                .collect(Collectors.joining("\n"));
        String relStatements = this.getRelationshipTypes().entrySet().stream()
                .map(e -> e.getValue().asPlantUml())
                .collect(Collectors.joining("\n"));

        String suffix = "\n@enduml";
        return prefix + nodeStatements + "\n" + relStatements + suffix;
    }

    public void savePlantUml(String filePath, FileFormat imageFormat) {
        String plantUmlStr = this.asPlantUml();
        SourceStringReader reader = new SourceStringReader(plantUmlStr);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
            writer.write(plantUmlStr);
            writer.close();
            System.out.println("plantUML text exported.");

            if (imageFormat != null ) {
                //generate Image
                String imageFileSuffix = "." +imageFormat.name().toLowerCase(Locale.ROOT);
                OutputStream os = new FileOutputStream(new File(filePath + imageFileSuffix));
                FileFormatOption option = new FileFormatOption(imageFormat);
                String desc = reader.outputImage(os, option).getDescription();
                System.out.println("Image output: " + desc);
                os.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
