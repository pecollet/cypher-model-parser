package org.neo4j.cs.model;

import lombok.*;

import java.util.HashSet;
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

    //labels found in a pattern without direction ()-[:TYPE]-(:Label)
    @Getter
    @Setter
    Set<String> undirectedNodeLabels = new HashSet<>();

    public RelationshipType addUndirectedNodelabel(String label) {
        this.undirectedNodeLabels.add(label);
        return this;
    }

    public RelationshipType addProperty(String key) {
        this.properties.add(new Property(key));
        return this;
    }
    public RelationshipType addProperty(Property property) {
        this.properties.add(property);
        return this;
    }
    public RelationshipType addProperty(String key, String type) {
        this.addProperty(new Property(key, type));
        return this;
    }

    public String asPlantUml() {

        String prefix = "class "+ '"' +this.type+'"'+" << (R,orange) >> {\n";
        String properties = this.getProperties().stream()
                .map(p -> "    " + p.asPlantUml())
                .collect(Collectors.joining("\n"));
        String suffix = "\n}";
        String plantUml =  prefix + properties + suffix;

        String starts = this.sourceNodeLabels.stream()
                .map ( lbl -> '"'+lbl+'"'+ " -- " +'"'+ this.type+ '"')
                .collect(Collectors.joining("\n"));
        String ends = this.targetNodeLabels.stream()
                .map ( lbl -> '"'+ this.type+ '"' + " --> " +'"'+lbl+'"' )
                .collect(Collectors.joining("\n"));
        String undirecteds = this.undirectedNodeLabels.stream()
                .map ( lbl -> '"'+ this.type+ '"' + " .. " +'"'+lbl+'"' )
                .collect(Collectors.joining("\n"));

        if (starts.length() > 0)
            plantUml += "\n" + starts;
        if (ends.length() > 0)
            plantUml += "\n" + ends;
        if (undirecteds.length() > 0)
            plantUml += "\n" + undirecteds;
        return plantUml;
    }
}
