package org.neo4j.cs.model;

import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    @Getter
    @Setter
    Set<String> constrainedSourceNodeLabels = new HashSet<>();

    @Getter
    @Setter
    Set<String> constrainedTargetNodeLabels = new HashSet<>();

    @Getter
    @Setter
    private Long count;

    @Getter
    @Setter
    private List<LabelCount> sourceLabelCounts = new ArrayList<>();

    @Getter
    @Setter
    private List<LabelCount> targetLabelCounts = new ArrayList<>();

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
        return asPlantUml(false);
    }

    private Long getSourceLabelCount(String label) {
        if (sourceLabelCounts == null) return null;
        for (LabelCount lc : sourceLabelCounts) {
            if (lc.getLabel().equals(label)) {
                return lc.getCount();
            }
        }
        return null;
    }

    private Long getTargetLabelCount(String label) {
        if (targetLabelCounts == null) return null;
        for (LabelCount lc : targetLabelCounts) {
            if (lc.getLabel().equals(label)) {
                return lc.getCount();
            }
        }
        return null;
    }

    public String asPlantUml(boolean includeCounts) {
        String plantUml = "";
        String classDef;
        if (includeCounts && this.count != null) {
            classDef = "class " + '"' + this.type + '"' + "<" + this.count + "> R";
        } else {
            classDef = "class " + '"' + this.type + '"' + " R";
        }
        if (this.getProperties().isEmpty()) {
            plantUml = classDef;
        } else {
            String properties = this.getProperties().stream().sorted()
                    .map(p -> "    " + p.asPlantUml())
                    .collect(Collectors.joining("\n"));
            plantUml = classDef + " {\n" + properties + "\n}";
        }

        String starts = this.sourceNodeLabels.stream()
                .map ( lbl -> {
                    String suffix = "";
                    boolean constrained = this.constrainedSourceNodeLabels.contains(lbl);
                    Long c = includeCounts ? getSourceLabelCount(lbl) : null;
                    if (constrained && c != null) {
                        suffix = " : <&lock-locked> " + c;
                    } else if (constrained) {
                        suffix = " : <&lock-locked>";
                    } else if (c != null) {
                        suffix = " : " + c;
                    }
                    return '"' + lbl + '"' + " -- " + '"' + this.type + '"' + suffix;
                })
                .collect(Collectors.joining("\n"));
        String ends = this.targetNodeLabels.stream()
                .map ( lbl -> {
                    String suffix = "";
                    boolean constrained = this.constrainedTargetNodeLabels.contains(lbl);
                    Long c = includeCounts ? getTargetLabelCount(lbl) : null;
                    if (constrained && c != null) {
                        suffix = " : <&lock-locked> " + c;
                    } else if (constrained) {
                        suffix = " : <&lock-locked>";
                    } else if (c != null) {
                        suffix = " : " + c;
                    }
                    return '"' + this.type + '"' + " --> " + '"' + lbl + '"' + suffix;
                })
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
