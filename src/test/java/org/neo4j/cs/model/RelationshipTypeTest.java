package org.neo4j.cs.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RelationshipTypeTest {
    @Test
    void shouldExportCorrectPlantUml() {
        RelationshipType rt = new RelationshipType("HAS_CHILD");
        rt.addProperty("from");
        rt.addProperty("to", "Number");
        Set srcNodeLabels = new HashSet<String>();
        srcNodeLabels.add("Thing");
        Set targetNodeLabels = new HashSet<String>();
        targetNodeLabels.add("Stuff");
        Set undirNodeLabels = new HashSet<String>();
        undirNodeLabels.add("X");
        rt.setSourceNodeLabels(srcNodeLabels);
        rt.setTargetNodeLabels(targetNodeLabels);
        rt.setUndirectedNodeLabels(undirNodeLabels);
        assertEquals(rt.asPlantUml(), "class \"HAS_CHILD\" R {\n" +
                "    ATTR(from)\n" +
                "    <&bar-chart> to\n" +
                "}\n" +
                "\"Thing\" -- \"HAS_CHILD\"\n" +
                "\"HAS_CHILD\" --> \"Stuff\"\n" +
                "\"HAS_CHILD\" .. \"X\"");
    }

    @Test
    void shouldExportCorrectPlantUmlWithEndpointConstraints() {
        RelationshipType rt = new RelationshipType("HAS_CHILD");
        Set<String> srcNodeLabels = new HashSet<>();
        srcNodeLabels.add("Thing");
        Set<String> targetNodeLabels = new HashSet<>();
        targetNodeLabels.add("Stuff");
        rt.setSourceNodeLabels(srcNodeLabels);
        rt.setTargetNodeLabels(targetNodeLabels);

        Set<String> constrainedSrc = new HashSet<>();
        constrainedSrc.add("Thing");
        rt.setConstrainedSourceNodeLabels(constrainedSrc);

        Set<String> constrainedTgt = new HashSet<>();
        constrainedTgt.add("Stuff");
        rt.setConstrainedTargetNodeLabels(constrainedTgt);

        assertEquals("class \"HAS_CHILD\" R\n" +
                "\"Thing\" -- \"HAS_CHILD\" : <&lock-locked>\n" +
                "\"HAS_CHILD\" --> \"Stuff\" : <&lock-locked>", rt.asPlantUml());
    }

    @Test
    void shouldExportCorrectPlantUmlWithCounts() {
        RelationshipType rt = new RelationshipType("HAS_CHILD");
        rt.setCount(15000L);
        Set<String> srcNodeLabels = new HashSet<>();
        srcNodeLabels.add("Thing");
        Set<String> targetNodeLabels = new HashSet<>();
        targetNodeLabels.add("Stuff");
        rt.setSourceNodeLabels(srcNodeLabels);
        rt.setTargetNodeLabels(targetNodeLabels);

        List<LabelCount> srcCounts = new ArrayList<>();
        srcCounts.add(new LabelCount("Thing", 10000L));
        rt.setSourceLabelCounts(srcCounts);

        List<LabelCount> tgtCounts = new ArrayList<>();
        tgtCounts.add(new LabelCount("Stuff", 5000L));
        rt.setTargetLabelCounts(tgtCounts);

        assertEquals("class \"HAS_CHILD\"<15000> R\n" +
                "\"Thing\" -- \"HAS_CHILD\" : 10000\n" +
                "\"HAS_CHILD\" --> \"Stuff\" : 5000", rt.asPlantUml(true));

        Set<String> constrainedSrc = new HashSet<>();
        constrainedSrc.add("Thing");
        rt.setConstrainedSourceNodeLabels(constrainedSrc);

        assertEquals("class \"HAS_CHILD\"<15000> R\n" +
                "\"Thing\" -- \"HAS_CHILD\" : <&lock-locked> 10000\n" +
                "\"HAS_CHILD\" --> \"Stuff\" : 5000", rt.asPlantUml(true));
    }

}
