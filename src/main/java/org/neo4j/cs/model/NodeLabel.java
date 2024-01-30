package org.neo4j.cs.model;

import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@ToString(callSuper=true)
@EqualsAndHashCode
public class NodeLabel extends EntityType {
    @NonNull
    @Getter
    String label;

    public NodeLabel(String label, Set<Property> properties) {
        this(label);
        this.setProperties(properties);
    }

    public String asPlantUml() {
        String prefix = "class "+'"'+this.label+'"'+" << (N,lightblue) >> {\n";
        String properties = this.getProperties().stream().sorted()
                .map(p -> "    " + p.asPlantUml())
                .collect(Collectors.joining("\n"));
        String suffix = "\n}";
        return prefix + properties + suffix;
    }
}
