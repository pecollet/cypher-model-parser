package org.neo4j.cs.model;

import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@ToString(callSuper=true)
@EqualsAndHashCode(callSuper = false)
public class NodeLabel extends EntityType {
    @NonNull
    @Getter
    String label;

    public NodeLabel(String label, Set<Property> properties) {
        this(label);
        this.setProperties(properties);
    }

    public String asPlantUml() {
        String classDef = "class \"" + this.label + "\" N";
        if (this.getProperties().isEmpty()) {
            return classDef;
        }
        String properties = this.getProperties().stream().sorted()
                .map(p -> "    " + p.asPlantUml())
                .collect(Collectors.joining("\n"));
        return classDef + " {\n" + properties + "\n}";
    }
}
