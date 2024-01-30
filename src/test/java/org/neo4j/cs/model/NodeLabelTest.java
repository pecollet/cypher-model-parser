package org.neo4j.cs.model;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;


public class NodeLabelTest {
    @Test
    void shouldExportCorrectPlantUml() {
        NodeLabel nl = new NodeLabel("Thing");
        nl.addProperty("name");
        nl.addProperty("age", "int");
        assertEquals("class \"Thing\" << (N,lightblue) >> {\n" +
                "    int age\n" +
                "    name\n" +
                "}", nl.asPlantUml());
    }

}
