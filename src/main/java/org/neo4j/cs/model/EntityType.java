package org.neo4j.cs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ToString
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

    public void dedupeProperties() {
        Set<String> keys = this.getProperties().stream().map(p -> p.getKey()).collect(Collectors.toSet());

        Set<Property> dedupedProperties = keys.stream().map(k -> {
                    Property newProp = new Property(k);
                    for (Property p : this.getProperties()) {
                        if (k.equals(p.getKey())) {
                            if (p.getType() != null) {
                                newProp.setType(p.getType());
                            }
                            if (p.isIndexed()) {
                                newProp.setIndexed(p.isIndexed());
                            }
                        }
                    }
                    return newProp;
                } )
                .collect(Collectors.toSet());

        this.setProperties(dedupedProperties);
    }
}
