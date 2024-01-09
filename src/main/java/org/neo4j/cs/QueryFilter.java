package org.neo4j.cs;

import java.util.List;
import java.util.stream.Collectors;

public class QueryFilter {
    public List<String> filter(List<String> queries) {
        return queries.stream()
                //ignore explain queries (sometimes used for autocomplete)
                // and admin queries like SHOW DATABASES
                .filter( q -> !q.startsWith("EXPLAIN") && !q.startsWith("SHOW "))
                //CYPHER modifiers are not supported by cypherDSL, so remove them
                .map(q -> q.replaceAll(
                                "^(CYPHER|cypher)\s+(RUNTIME|runtime)=(INTERPRETED|interpreted|SLOTTED|slotted|PARALLEL|parallel)\s*(expressionEngine=(INTERPRETED|interpreted))?",
                                ""
                        )
                )
                .collect(Collectors.toList());
    }
}
