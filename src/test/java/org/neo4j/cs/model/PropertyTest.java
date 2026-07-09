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
        p.addConstraintType("Existence");
        assertEquals("<&double-quote-serif-left> name <&lock-locked>[∃]", p.asPlantUml());
    }

    @Test
    void shouldExportCorrectPlantUmlWithUniquenessConstraint() {
        Property p = new Property("name", "String");
        p.addConstraintType("Uniqueness");
        assertEquals("<&double-quote-serif-left> name <&lock-locked>[≠]", p.asPlantUml());
    }

    @Test
    void shouldExportCorrectPlantUmlWithPropertyTypeConstraint() {
        Property p = new Property("name", "String");
        p.addConstraintType("PropertyType");
        assertEquals("<&double-quote-serif-left> name <&lock-locked>[∈]", p.asPlantUml());
    }

    @Test
    void shouldExportCorrectPlantUmlWithKeyConstraint() {
        Property p = new Property("name", "String");
        p.addConstraintType("Key");
        assertEquals("<&double-quote-serif-left> name <&key>", p.asPlantUml());
    }

    @Test
    void shouldExportCorrectPlantUmlWithIndexAndConstraint() {
        Property p = new Property("name", "String", "RANGE");
        p.addConstraintType("Existence");
        assertEquals("<&double-quote-serif-left> name <&info>[<&resize-both>] <&lock-locked>[∃]", p.asPlantUml());
    }

    @Test
    void shouldExportCorrectPlantUmlWithMultipleIndexesAndConstraints() {
        Property p = new Property("name", "String");
        p.getIndexTypes().add("RANGE");
        p.getIndexTypes().add("TEXT");
        p.getConstraintTypes().add("Existence");
        p.getConstraintTypes().add("Uniqueness");
        assertEquals("<&double-quote-serif-left> name <&info>[<&resize-both>] <&info>[<&text>] <&lock-locked>[∃] <&lock-locked>[≠]", p.asPlantUml());
    }
}
