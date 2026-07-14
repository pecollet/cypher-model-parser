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

    @Test
    void shouldExportCorrectPlantUmlWithImpliedLabels() {
        NodeLabel nl = new NodeLabel("Pet");
        nl.addProperty("name", "String");
        nl.getImpliedLabels().add("Resident");
        nl.getImpliedLabels().add("Animal");
        assertEquals("class \"Pet\" N {\n" +
                "    <&double-quote-serif-left> name\n" +
                "}\n" +
                "\"Animal\" \"implied\" <|-- \"Pet\"\n" +
                "\"Resident\" \"implied\" <|-- \"Pet\"", nl.asPlantUml());
    }
}
