package org.neo4j.cs.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

public class EntityType {
    @Setter
    @Getter
    Set<Property> properties = new HashSet<>();

    public EntityType addProperty(String key) {
        this.addProperty(new Property(key));
        return this;
    }
    public EntityType addProperty(Property property) {
        this.properties.add(property);
        return this;
    }
    public EntityType addProperty(String key, String type) {
        this.addProperty(new Property(key, type));
        return this;
    }
}
