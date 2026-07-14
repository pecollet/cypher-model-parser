package org.neo4j.cs.model;

import org.junit.jupiter.api.Test;
import org.neo4j.cs.GraphCountsParser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ModelTest {

    @Test
    void testParseGraphCounts() throws Exception {
        String json = "[\n" +
                "  {\n" +
                "    \"section\": \"GRAPH COUNTS\",\n" +
                "    \"data\": {\n" +
                "      \"relationships\": [\n" +
                "        {\n" +
                "          \"relationshipType\": \"LIVES_IN\",\n" +
                "          \"count\": 10\n" +
                "        },\n" +
                "        {\n" +
                "          \"relationshipType\": \"LOVES\",\n" +
                "          \"count\": 5,\n" +
                "          \"startLabel\": \"Person\",\n" +
                "          \"endLabel\": \"City\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"nodes\": [\n" +
                "        {\n" +
                "          \"count\": 100,\n" +
                "          \"label\": \"Person\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"count\": 50,\n" +
                "          \"label\": \"City\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"indexes\": [\n" +
                "        {\n" +
                "          \"indexType\": \"RANGE\",\n" +
                "          \"properties\": [\"id\"],\n" +
                "          \"labels\": [\"Person\"]\n" +
                "        }\n" +
                "      ],\n" +
                "      \"constraints\": [\n" +
                "        {\n" +
                "          \"type\": \"Property type constraint\",\n" +
                "          \"properties\": [\"age\"],\n" +
                "          \"propertyTypes\": [\"INTEGER\"],\n" +
                "          \"label\": \"Person\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"type\": \"Relationship endpoint label constraint\",\n" +
                "          \"relationshipType\": \"LIVES_IN\",\n" +
                "          \"enforcedLabel\": \"Resident\",\n" +
                "          \"endpointType\": \"START\"\n" +
                "        },\n" +
                "        {\n" +
                "          \"type\": \"Relationship endpoint label constraint\",\n" +
                "          \"relationshipType\": \"LIVES_IN\",\n" +
                "          \"enforcedLabel\": \"City\",\n" +
                "          \"endpointType\": \"END\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "]";

        Path tempFile = Files.createTempFile("graphcounts-test", ".json");
        Files.writeString(tempFile, json);

        try {
            Model model = GraphCountsParser.parse(tempFile.toFile());

            // Check node labels
            assertTrue(model.getNodeLabels().containsKey("Person"));
            assertTrue(model.getNodeLabels().containsKey("City"));
            assertTrue(model.getNodeLabels().containsKey("Resident"));

            NodeLabel person = model.getNodeLabels().get("Person");
            assertEquals("graphcounts", person.getProvenance());

            // Check property and index on Person
            Property idProp = person.getProperties().stream()
                    .filter(p -> p.getKey().equals("id"))
                    .findFirst()
                    .orElse(null);
            assertNotNull(idProp);
            assertTrue(idProp.isIndexed());
            assertEquals("RANGE", idProp.getIndexTypes().getFirst());
            assertEquals("UNKNOWN", idProp.getType());

            // Check constraint property type
            Property ageProp = person.getProperties().stream()
                    .filter(p -> p.getKey().equals("age"))
                    .findFirst()
                    .orElse(null);
            assertNotNull(ageProp);
            assertEquals("Number", ageProp.getType());
            assertTrue(ageProp.getConstraintTypes().contains("PropertyType"));

            // Check relationships
            assertTrue(model.getRelationshipTypes().containsKey("LIVES_IN"));
            assertTrue(model.getRelationshipTypes().containsKey("LOVES"));

            RelationshipType loves = model.getRelationshipTypes().get("LOVES");
            assertEquals("graphcounts", loves.getProvenance());
            assertEquals(Set.of("Person"), loves.getSourceNodeLabels());
            assertEquals(Set.of("City"), loves.getTargetNodeLabels());

            RelationshipType livesIn = model.getRelationshipTypes().get("LIVES_IN");
            assertEquals(Set.of("Resident"), livesIn.getSourceNodeLabels());
            assertEquals(Set.of("City"), livesIn.getTargetNodeLabels());
            assertEquals(Set.of("Resident"), livesIn.getConstrainedSourceNodeLabels());
            assertEquals(Set.of("City"), livesIn.getConstrainedTargetNodeLabels());

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testModelMergingProvenance() {
        Model m1 = new Model();
        NodeLabel nl1 = new NodeLabel("Person");
        nl1.setProvenance("query logs");
        nl1.addProperty("name", "String");
        m1.getNodeLabels().put("Person", nl1);

        Model m2 = new Model();
        NodeLabel nl2 = new NodeLabel("Person");
        nl2.setProvenance("graphcounts");
        Property p = new Property("name", "String", "RANGE");
        nl2.getProperties().add(p);
        nl2.addProperty("age", "Number");
        m2.getNodeLabels().put("Person", nl2);

        // Merge m2 into m1
        m1.add(m2);

        NodeLabel merged = m1.getNodeLabels().get("Person");
        assertNotNull(merged);
        assertEquals("both", merged.getProvenance());

        // Check merged properties
        assertEquals(2, merged.getProperties().size());
        Property mergedName = merged.getProperties().stream()
                .filter(prop -> prop.getKey().equals("name"))
                .findFirst()
                .orElse(null);
        assertNotNull(mergedName);
        assertTrue(mergedName.isIndexed());
        assertEquals("RANGE", mergedName.getIndexTypes().getFirst());
        assertEquals("String", mergedName.getType());
    }

    @Test
    void testActualGraphCountsFile() throws Exception {
        File actualFile= new File("src/test/resources/graphcounts.json");
        Model model = GraphCountsParser.parse(actualFile);
        assertNotNull(model);
        
        // Check that we got at least some nodes and relationships
        assertFalse(model.getNodeLabels().isEmpty(), "Should have parsed node labels from actual file");
        assertTrue(model.getNodeLabels().containsKey("Person"));
        assertTrue(model.getNodeLabels().containsKey("City"));
        assertTrue(model.getNodeLabels().containsKey("Organisation"));
        
        assertTrue(model.getRelationshipTypes().containsKey("LIVES_IN"));
        assertTrue(model.getRelationshipTypes().containsKey("LOVES"));

        NodeLabel org = model.getNodeLabels().get("Organisation");
        Property synthId = org.getProperties().stream().filter(p -> "synthetic_id".equals(p.getKey())).findFirst().orElse(null);
        assertNotNull(synthId);
        assertTrue(synthId.isIndexed(), "synthetic_id should be indexed on Organisation");
        assertEquals("RANGE", synthId.getIndexTypes().getFirst(), "synthetic_id should have RANGE index on Organisation");

        NodeLabel person = model.getNodeLabels().get("Person");
        Property nameProp = person.getProperties().stream().filter(p -> "name".equals(p.getKey())).findFirst().orElse(null);
        assertNotNull(nameProp);
        assertTrue(nameProp.getConstraintTypes().contains("PropertyType"));
        assertTrue(nameProp.getConstraintTypes().contains("Existence"));

        NodeLabel city = model.getNodeLabels().get("City");
        Property cityNameProp = city.getProperties().stream().filter(p -> "name".equals(p.getKey())).findFirst().orElse(null);
        assertNotNull(cityNameProp);
        assertTrue(cityNameProp.getConstraintTypes().contains("PropertyType"));
        assertTrue(cityNameProp.getConstraintTypes().contains("Existence"));
    }

    @Test
    void testModelMergingWithMultipleIndexesAndConstraints() {
        Model m1 = new Model();
        NodeLabel nl1 = new NodeLabel("User");
        Property p1 = new Property("username", "String");
        p1.getIndexTypes().add("RANGE");
        p1.getConstraintTypes().add("Existence");
        nl1.getProperties().add(p1);
        m1.getNodeLabels().put("User", nl1);

        Model m2 = new Model();
        NodeLabel nl2 = new NodeLabel("User");
        Property p2 = new Property("username", "String");
        p2.getIndexTypes().add("TEXT");
        p2.getConstraintTypes().add("Uniqueness");
        nl2.getProperties().add(p2);
        m2.getNodeLabels().put("User", nl2);

        m1.add(m2);

        NodeLabel merged = m1.getNodeLabels().get("User");
        assertNotNull(merged);
        Property mergedProp = merged.getProperties().stream()
                .filter(p -> "username".equals(p.getKey()))
                .findFirst()
                .orElse(null);
        assertNotNull(mergedProp);

        assertTrue(mergedProp.getIndexTypes().contains("RANGE"));
        assertTrue(mergedProp.getIndexTypes().contains("TEXT"));
        assertEquals(2, mergedProp.getIndexTypes().size());

        assertTrue(mergedProp.getConstraintTypes().contains("Existence"));
        assertTrue(mergedProp.getConstraintTypes().contains("Uniqueness"));
        assertEquals(2, mergedProp.getConstraintTypes().size());
    }

    @Test
    void testParseNodeLabelExistenceConstraint() throws Exception {
        String json = "[\n" +
                "  {\n" +
                "    \"section\": \"GRAPH COUNTS\",\n" +
                "    \"data\": {\n" +
                "      \"constraints\": [\n" +
                "        {\n" +
                "          \"type\": \"Node label existence constraint\",\n" +
                "          \"label\": \"Pet\",\n" +
                "          \"enforcedLabel\": \"Resident\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "]";

        Path tempFile = Files.createTempFile("graphcounts-constraint-test", ".json");
        Files.writeString(tempFile, json);

        try {
            Model model = GraphCountsParser.parse(tempFile.toFile());

            assertTrue(model.getNodeLabels().containsKey("Pet"));
            assertTrue(model.getNodeLabels().containsKey("Resident"));

            NodeLabel pet = model.getNodeLabels().get("Pet");
            assertNotNull(pet);
            assertEquals(1, pet.getImpliedLabels().size());
            assertEquals("Resident", pet.getImpliedLabels().get(0));

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testModelMergingImpliedLabels() {
        Model m1 = new Model();
        NodeLabel nl1 = new NodeLabel("Pet");
        nl1.getImpliedLabels().add("Resident");
        nl1.getImpliedLabels().add("Animal");
        m1.getNodeLabels().put("Pet", nl1);

        Model m2 = new Model();
        NodeLabel nl2 = new NodeLabel("Pet");
        nl2.getImpliedLabels().add("Animal");
        nl2.getImpliedLabels().add("Mammal");
        m2.getNodeLabels().put("Pet", nl2);

        m1.add(m2);

        NodeLabel merged = m1.getNodeLabels().get("Pet");
        assertNotNull(merged);
        List<String> implied = merged.getImpliedLabels();
        assertNotNull(implied);
        assertEquals(3, implied.size());
        assertTrue(implied.contains("Resident"));
        assertTrue(implied.contains("Animal"));
        assertTrue(implied.contains("Mammal"));
    }
}
