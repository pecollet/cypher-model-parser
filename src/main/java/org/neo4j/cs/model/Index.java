package org.neo4j.cs.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Index {

    public enum EntityType {
        NODE, RELATIONSHIP
    }

    @Getter
    @Setter
    String name;

    @Getter
    @Setter
    String nodeLabel;

    @Getter
    @Setter
    String relationshipType;

    @NonNull
    @Getter
    @Setter
    List<String> properties;

    public Index(String entityName, List<String> properties, EntityType et) {
        if (et == EntityType.NODE) {
            this.nodeLabel = entityName;
        } else {
            this.relationshipType = entityName;
        }
        this.properties = properties;
    }
    public Index(String indexName, String entityName, List<String> properties, EntityType et) {
        this(entityName, properties, et);
        this.name = indexName;
    }

    public Index(String entityName, String property, EntityType et) {
        this(entityName, Collections.emptyList(), et);
        this.properties.add(property);
    }

    public Index(String indexName, String entityName, String property, EntityType et) {
        this(indexName, entityName, Collections.emptyList(), et);
        this.properties.add(property);
    }

}
