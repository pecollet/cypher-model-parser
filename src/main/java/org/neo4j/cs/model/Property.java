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
    String indexType;


    private static final Map<String, String> typeIconMap = new HashMap<>();
    private static final Map<String, String> constraintsIconMap = new HashMap<>();
    private static final Map<String, String> indexesIconMap = new HashMap<>();
    //PlantUML OpenIconic icon names (https://plantuml.com/openiconic)
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
        typeIconMap.put("Vector", "grid-four-up");
        //"key" "sort-ascending" "pin" "shield"
        constraintsIconMap.put("Key", "<&key>");
        constraintsIconMap.put("Uniqueness", "<&lock-locked>[≠]"); 
        constraintsIconMap.put("Existence", "<&lock-locked>[∃]"); 
        constraintsIconMap.put("PropertyType", "<&lock-locked>[∈]"); 
        
        //bootstrap (https://icons.getbootstrap.com/) hand-index <$bi-hand-index>
        //!include <bootstrap/bootstrap>
        indexesIconMap.put("RANGE", "<&info>[<&resize-both>]"); //resize-width resize-height resize-both
        indexesIconMap.put("TEXT", "<&info>[<&text>]"); //text
        indexesIconMap.put("POINT", "<&info>[<&map-marker>]"); //map-marker map
        indexesIconMap.put("FULLTEXT", "<&info>[<&book>]"); //book
        indexesIconMap.put("VECTOR", "<&info>[<&grid-four-up>]"); //grid-four-up
    }

    public Property(String key, String type) {
        this(key);
        this.type = type;
    }

    @Deprecated
    public Property(String key, String type, boolean indexed) {
        this(key, type);
        if (indexed) {
            this.indexType = "RANGE";
        }
    }

    public boolean isIndexed() {
        return this.indexType != null;
    }

    @Deprecated
    public void setIndexed(boolean indexed) {
        if (indexed) {
            if (this.indexType == null) {
                this.indexType = "RANGE";
            }
        } else {
            this.indexType = null;
        }
    }

    public String asPlantUml() {
        String typeIconName = typeIconMap.computeIfAbsent(this.type, type -> "question-mark");
        String plantUml;
        if ("question-mark".equals(typeIconName)) {
            plantUml = "ATTR(" + this.key + ")";
        } else {
            plantUml = "<&" + typeIconName + "> " + this.key;
        }

        if (this.indexType != null) {
            String suffix = indexesIconMap.get(this.indexType);
            if (suffix != null) {
                plantUml = plantUml + " " + suffix;
            }
        }
        return plantUml;
    }

    @Override
    public int compareTo(Object o) {
        return key.compareTo(((Property)o).key);
    }
}
