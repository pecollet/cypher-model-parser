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
        this.addProperty(new Property(key));
        return this;
    }
    public NodeLabel addProperty(Property property) {
        this.properties.add(property);
        return this;
    }
    public NodeLabel addProperty(String key, String type) {
        this.addProperty(new Property(key, type));
        return this;
    }

    public String asPlantUml() {
        String prefix = "class "+'"'+this.label+'"'+" << (N,lightblue) >> {\n";
        String properties = this.getProperties().stream()
                .map(p -> "    " + p.asPlantUml())
                .collect(Collectors.joining("\n"));
        String suffix = "\n}";
        return prefix + properties + suffix;
    }
}
