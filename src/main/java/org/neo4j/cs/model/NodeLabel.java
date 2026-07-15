package org.neo4j.cs.model;

import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@ToString(callSuper=true)
@EqualsAndHashCode(callSuper = false)
public class NodeLabel extends EntityType {
    @NonNull
    @Getter
    String label;

    @Getter
    @Setter
    private List<String> impliedLabels = new ArrayList<>();

    @Getter
    @Setter
    private Long count;

    public NodeLabel(String label, Set<Property> properties) {
        this(label);
        this.setProperties(properties);
    }

    public String asPlantUml() {
        return asPlantUml(false);
    }

    public String asPlantUml(boolean includeCounts) {
        String classDef;
        if (includeCounts && this.count != null) {
            classDef = "class \"" + this.label + "\"<" + this.count + "> N";
        } else {
            classDef = "class \"" + this.label + "\" N";
        }
        String plantUml;
        if (this.getProperties().isEmpty()) {
            plantUml = classDef;
        } else {
            String properties = this.getProperties().stream().sorted()
                    .map(p -> "    " + p.asPlantUml())
                    .collect(Collectors.joining("\n"));
            plantUml = classDef + " {\n" + properties + "\n}";
        }

        if (this.impliedLabels != null && !this.impliedLabels.isEmpty()) {
            String inheritance = this.impliedLabels.stream().sorted()
                    .map(implied -> "\"" + implied + "\" \"implied\" <|-[dotted]- \"" + this.label + "\"")
                    .collect(Collectors.joining("\n"));
            plantUml += "\n" + inheritance;
        }

        return plantUml;
    }
}
