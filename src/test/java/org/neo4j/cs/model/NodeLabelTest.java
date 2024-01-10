package org.neo4j.cs.model;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;


public class NodeLabelTest {
    @Test
    void shouldExportCorrectPlantUml() {
        NodeLabel nl = new NodeLabel("Thing");
        nl.addProperty("name");
        nl.addProperty("age", "int");
        assertEquals(nl.asPlantUml(), "class \"Thing\" << (N,lightblue) >> {\n" +
                "    name\n" +
                "    int age\n" +
                "}");
    }

}
