package org.neo4j.cs.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertyTest {
    @Test
    void shouldExportCorrectPlantUml() {
        Property p = new Property("name");
        assertEquals(p.asPlantUml(), "<&question-mark> name");
    }

    @Test
    void shouldExportCorrectPlantUmlWithStringType() {
        Property p = new Property("name", "String");
        assertEquals(p.asPlantUml(), "<&double-quote-serif-left> name");
    }

    @Test
    void shouldExportCorrectPlantUmlWithListType() {
        Property p = new Property("names", "List");
        assertEquals(p.asPlantUml(), "<&list> names");
    }
}
