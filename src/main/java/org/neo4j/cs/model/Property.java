package org.neo4j.cs.model;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

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


    private static final Map<String, String> typeIconMap = new HashMap<>();

    //PlantUML OpenIconic icon names
    static {
        typeIconMap.put("String", "double-quote-serif-left");
        typeIconMap.put("Number", "bar-chart");
        typeIconMap.put("Boolean", "contrast");
        typeIconMap.put("List", "list");
        typeIconMap.put("Date", "calendar");
        typeIconMap.put("Time", "clock");
        typeIconMap.put("DateTime", "calendar><&clock");
        typeIconMap.put("Duration", "timer");
        typeIconMap.put("Point", "location");
    }

    public Property(String key, String type) {
        this(key);
        this.type = type;
    }

    public String asPlantUml() {
        String plantUml=this.key;
        String typeIconName= typeIconMap.computeIfAbsent(this.type, type -> "question-mark");
        plantUml = "<&"+typeIconName+"> " + plantUml;

        if (this.indexed) plantUml = "{static} " + plantUml;
        return plantUml;
    }

    @Override
    public int compareTo(Object o) {
        return key.compareTo(((Property)o).key);
    }
}
