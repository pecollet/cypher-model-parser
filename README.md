Tool that parses cypher queries in order to :
- generate a class diagram of the graph model.
- obfuscate literal values

#Class diagram

**Usage:**


```
java -jar cypher-model-parser-x.y.z.jar queries.csv
```

Full options:

```
Usage: Parser [-hjV] [-l=layout-engine] [-o=OUTPUT-DIR] <queriesFile>
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
- having an input CSV file, single column, with 1 query per line (quoted), no headers.


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

#Obfuscation

**Usage:**


```
java -cp cypher-model-parser-x.y.z.jar org.neo4j.cs.Obfuscator <query_string>
```

Full options:

```
Usage: Obfuscator [-hV] [-d=dialect] <query>
Obfuscate literal values in cypher query.
      <query>             The query to obfuscate
  -d, --dialect=dialect   The cypher dialect : [NEO4J_5|NEO4J_4]. Defaults to
                            NEO4J_5.
  -h, --help              Show this help message and exit.
  -V, --version           Print version information and exit.
```

This will return the obfuscated query on stdout.

Obfuscation rules: 
- all characters strings are replaced by '****'
- all digits in numbers are replaced by *

Example: 
```
> java -cp cypher-model-parser-x.y.z.jar org.neo4j.cs.Obfuscator "MATCH (movie:Movie)-[r:HAS]->(x:Thing) WHERE movie.title CONTAINS 'sdfdg' or r.x = 12 or r.y IN [1290,'gh'] RETURN movie.title, r.role"

MATCH (movie:Movie)-[r:HAS]->(x:Thing) WHERE (movie.title CONTAINS '****' OR r.x = ** OR r.y IN [****, '****']) RETURN movie.title, r.role
```