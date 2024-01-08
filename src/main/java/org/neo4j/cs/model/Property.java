package org.neo4j.cs.model;

import lombok.*;

@RequiredArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Property {
    @NonNull
    @EqualsAndHashCode.Include
    @Getter
    String key;
    String type;

    public String asPlantUml() {
        if (this.type != null) return this.type + " " + this.key;
        else return this.key;
    }
}
