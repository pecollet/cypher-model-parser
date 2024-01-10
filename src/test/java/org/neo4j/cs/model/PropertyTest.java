package org.neo4j.cs.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertyTest {
    @Test
    void shouldExportCorrectPlantUml() {
        Property p = new Property("name");
        assertEquals(p.asPlantUml(), "name");
    }

    @Test
    void shouldExportCorrectPlantUmlWithType() {
        Property p = new Property("name", "string");
        assertEquals(p.asPlantUml(), "string name");
    }
}
