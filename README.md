Tool that parses a file containing a list of cypher queries (as exported by the health check), and generates a plantUML class diagram.



**Usage:**


```
java -jar cypher-model-parser-1.0-SNAPSHOT.jar queries_map.csv
```

Full options:

```
Usage: parse-model [-hjV] [-l=layout-engine] [-o=OUTPUT-DIR] <queriesFile>
Parses cypher queries in <queriesFile> and generates a PlantUML class diagram.
<queriesFile>   The file containing the queries
-h, --help          Show this help message and exit.
-j, --json          Export JSON model.
-l, --layout-engine=layout-engine
The layout engine to use when exporting a diagram picture
: [SMETANA|DOT]. Defaults to SMETANA. DOT requires the
presence of the graphviz module on the system.
-o, --output-dir=OUTPUT-DIR
The directory where the output files are written
-V, --version       Print version information and exit.
```

This will produce :
- `model.puml.svg` : image of the class diagram
- `model.puml` : text file with the plantUML description of the class diagram, shall you want to  edit it.
- optionally `model.json` : the json representation of the model, for any programmatic post-processing


**Pre-requisites:**
- JRE 17
- having the `database_queries.csv` file. Run a HC for the HC to output that file. It will contain all the distinct cypher queries present in the query.logs for the selected database.


**Notes:**
- A more accurate PlantUML class diagram can be obtained from the actual data via the following cypher query :

```
CALL apoc.meta.nodeTypeProperties() YIELD nodeLabels, propertyName, propertyTypes
WITH nodeLabels[0] as lbl, COLLECT(propertyTypes[0]+" "+propertyName) as props
WITH COLLECT(["class "+lbl+" << (N,lightblue) >> {"]+props+["}"]) as labelStatements
WITH REDUCE(x=[], st in labelStatements | x + st) as labelStatements
CALL apoc.meta.relTypeProperties() YIELD relType, propertyName, propertyTypes
WITH labelStatements, replace(replace(relType,":",""),"`",'"') as relType, COLLECT(propertyTypes[0]+" "+propertyName) as props
WITH labelStatements, COLLECT(["class "+relType+" << (R,orange) >> {"]+props+["}"]) as types
WITH labelStatements, REDUCE(x=[], st in types | x + st) as types
call db.stats.retrieve("GRAPH COUNTS") yield data
WITH labelStatements, types,
[rel in data.relationships WHERE rel.relationshipType is not null AND rel.startLabel is not null| rel.startLabel+' -- "'+rel.relationshipType+'"'] as startRels,
[rel in data.relationships WHERE rel.relationshipType is not null AND rel.endLabel is not null| '"'+rel.relationshipType+'" --> '+rel.endLabel] as endRels
WITH ["@startuml"]+labelStatements+types+startRels+endRels+["@enduml"] as plantUlmStatements
RETURN reduce(x="", st in plantUlmStatements | x+'
'+st) as res
```