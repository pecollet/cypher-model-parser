package org.neo4j.cs.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
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
        assertEquals(rt.asPlantUml(), "class \"HAS_CHILD\" << (R,orange) >> {\n" +
                "    <&question-mark> from\n" +
                "    <&bar-chart> to\n" +
                "}\n" +
                "\"Thing\" -- \"HAS_CHILD\"\n" +
                "\"HAS_CHILD\" --> \"Stuff\"\n" +
                "\"HAS_CHILD\" .. \"X\"");
    }

}
