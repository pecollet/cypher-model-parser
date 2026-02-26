package org.neo4j.cs.model;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;


public class NodeLabelTest {
    @Test
    void shouldExportCorrectPlantUml() {
        NodeLabel nl = new NodeLabel("Thing");
        nl.addProperty("name");
        nl.addProperty("age", "Number");
        assertEquals("class \"Thing\" N {\n" +
                "    <&bar-chart> age\n" +
                "    ATTR(name)\n" +
                "}", nl.asPlantUml());
    }

}
