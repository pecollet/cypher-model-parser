package org.neo4j.cs.model;

import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@AllArgsConstructor
@ToString(callSuper=true)
public class RelationshipType  extends EntityType {
    @NonNull
    @Getter
    String type;

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

    public Set<String> addUndirectedNodelabels(Set<String> labels) {
        this.undirectedNodeLabels.addAll(labels);
        return this.undirectedNodeLabels;
    }
    public Set<String> addSourceNodeLabels(Set<String> labels) {
        this.sourceNodeLabels.addAll(labels);
        return this.sourceNodeLabels;
    }
    public Set<String> addTargetNodeLabels(Set<String> labels) {
        this.targetNodeLabels.addAll(labels);
        return this.targetNodeLabels;
    }

    public String asPlantUml() {
        String plantUml = "";
        String classDef = "class "+ '"' +this.type+'"'+" R";
        if (this.getProperties().isEmpty()) {
            plantUml = classDef;
        } else {
            String properties = this.getProperties().stream().sorted()
                    .map(p -> "    " + p.asPlantUml())
                    .collect(Collectors.joining("\n"));
            plantUml = classDef + " {\n" + properties + "\n}";
        }

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
