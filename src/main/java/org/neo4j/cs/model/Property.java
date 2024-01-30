package org.neo4j.cs.model;

import lombok.*;

@AllArgsConstructor
@RequiredArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Property implements Comparable{
    @NonNull
    @EqualsAndHashCode.Include
    @Getter
    String key;

    @EqualsAndHashCode.Include
    @Getter
    @Setter
    String type;

    @EqualsAndHashCode.Include
    @Getter
    @Setter
    boolean indexed;

    public Property(String key, String type) {
        this(key);
        this.type = type;
    }

    public String asPlantUml() {
        String plantUml=this.key;
        if (this.type != null) plantUml = this.type + " " + plantUml;
        if (this.indexed) plantUml = "{static} " + plantUml;
        return plantUml;
    }

    @Override
    public int compareTo(Object o) {
        return key.compareTo(((Property)o).key);
    }
}
