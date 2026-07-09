package org.neo4j.cs.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertyTest {
    @Test
    void shouldExportCorrectPlantUml() {
        Property p = new Property("name");
        assertEquals(p.asPlantUml(), "ATTR(name)");
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

    @Test
    void shouldExportCorrectPlantUmlWithRangeIndex() {
        Property p = new Property("name", "String", "RANGE");
        assertEquals("<&double-quote-serif-left> name <&info>[<&resize-both>]", p.asPlantUml());
    }

    @Test
    void shouldExportCorrectPlantUmlWithTextIndex() {
        Property p = new Property("name", "String", "TEXT");
        assertEquals("<&double-quote-serif-left> name <&info>[<&text>]", p.asPlantUml());
    }

    @Test
    void shouldExportCorrectPlantUmlWithExistenceConstraint() {
        Property p = new Property("name", "String");
        p.setConstraintType("Existence");
        assertEquals("<&double-quote-serif-left> name <&lock-locked>[∃]", p.asPlantUml());
    }

    @Test
    void shouldExportCorrectPlantUmlWithUniquenessConstraint() {
        Property p = new Property("name", "String");
        p.setConstraintType("Uniqueness");
        assertEquals("<&double-quote-serif-left> name <&lock-locked>[≠]", p.asPlantUml());
    }

    @Test
    void shouldExportCorrectPlantUmlWithPropertyTypeConstraint() {
        Property p = new Property("name", "String");
        p.setConstraintType("PropertyType");
        assertEquals("<&double-quote-serif-left> name <&lock-locked>[∈]", p.asPlantUml());
    }

    @Test
    void shouldExportCorrectPlantUmlWithKeyConstraint() {
        Property p = new Property("name", "String");
        p.setConstraintType("Key");
        assertEquals("<&double-quote-serif-left> name <&key>", p.asPlantUml());
    }

    @Test
    void shouldExportCorrectPlantUmlWithIndexAndConstraint() {
        Property p = new Property("name", "String", "RANGE");
        p.setConstraintType("Existence");
        assertEquals("<&double-quote-serif-left> name <&info>[<&resize-both>] <&lock-locked>[∃]", p.asPlantUml());
    }
}
