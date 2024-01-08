package org.neo4j.cs.model;

import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@AllArgsConstructor
@ToString
public class NodeLabel {
    @NonNull
    @Getter
    String label;

    @Setter
    @Getter
    Set<Property> properties = new HashSet<>();

    public NodeLabel addProperty(String key) {
        this.properties.add(new Property(key));
        return this;
    }

    public String asPlantUml() {
        String prefix = "class "+this.label+" << (N,lightblue) >> {\n";
        String properties = this.getProperties().stream()
                .map(p -> "    " + p.asPlantUml())
                .collect(Collectors.joining("\n"));
        String suffix = "\n}";
        return prefix + properties + suffix;
    }
}
