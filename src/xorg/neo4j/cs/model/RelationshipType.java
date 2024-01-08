package xorg.neo4j.cs.model;

import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@AllArgsConstructor
@ToString
public class RelationshipType {
    @NonNull
    @Getter
    String type;

    @Setter
    @Getter
    Set<Property> properties = new HashSet<>();
    @Getter
    @Setter
    Set<String> sourceNodeLabels = new HashSet<>();
    @Getter
    @Setter
    Set<String> targetNodeLabels = new HashSet<>();
    @Getter
    @Setter
    Set<String> undirectedNodeLabels = new HashSet<>();

    public RelationshipType addProperty(String key) {
        this.properties.add(new Property(key));
        return this;
    }

    public String asPlantUml() {
        String prefix = "class "+this.type+" << (R,orange) >> {\n";
        String properties = this.getProperties().stream()
                .map(p -> "    " + p.asPlantUml())
                .collect(Collectors.joining(" "));
        String suffix = "\n}";

        String starts = this.sourceNodeLabels.stream()
                .map ( lbl -> lbl + " -- " +'"'+ this.type+ '"')
                .collect(Collectors.joining("\n"));
        String ends = this.targetNodeLabels.stream()
                .map ( lbl -> '"'+ this.type+ '"' + " --> " + lbl )
                .collect(Collectors.joining("\n"));
        return prefix + properties + suffix + "\n" + starts + "\n" + ends;
    }
}
