Tool that parses cypher queries in order to :
- generate a class diagram of the graph model.
- obfuscate literal values
- generate explain plans from Cypher queries using graph count data

# Class diagram

**Usage:**


```
java -jar cypher-model-parser-x.y.z.jar queries.csv
```

Full options:

```
Usage: Parser [-hjV] [-d=dialect] [-l=layout-engine] [-o=OUTPUT-DIR]
              <queriesFile>
Parses cypher queries in <queriesFile> and generates a PlantUML class diagram.
      <queriesFile>       The file containing the queries
  -d, --dialect=dialect   The cypher dialect, one of : [5, 25]. Defaults to 25.
  -h, --help              Show this help message and exit.
  -j, --json              Export JSON model.
  -l, --layout-engine=layout-engine
                          The layout engine to use when exporting a diagram
                            picture : [SMETANA|DOT]. If not specified, an
                            attempt is made to use DOT if present. DOT requires
                            the presence of the graphviz module on the system.
  -o, --output-dir=OUTPUT-DIR
                          The directory where the output files are written
  -V, --version           Print version information and exit.
```

This will produce :
- `model.puml.svg` : image of the class diagram
- `model.puml` : text file with the plantUML description of the class diagram, shall you want to  edit it.
- optionally `model.json` : the json representation of the model, for any programmatic post-processing

Notes: 
- The class diagram will contain node labels (N), relationship types (R) and their properties. 
- Property types, where possible, are inferred from expressions where properties are evaluated against typed literals or used in functions with known signatures (native functions & some APOC)
- Relationships for which the direction is unknown (extracted from undirected patterns) will be shown with dotted lines
- Cypher patterns with unlabelled nodes may result in missing source or target association for Relationship classes
- multi-labeled nodes : each label is considered as a class (as opposed to each labels combinations mapping to a class)
- in label expressions (ex: `(:A|(B&!C))`) the presence of a label is sufficient for it to be included (even if negated)
- properties are matched to their parent enty via entity variables. In some cases, when the same variable is reused is various scopes, that may result is incorrect property attribution.

**Pre-requisites:**
- JRE 21
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

# Obfuscation

**Usage:**


```
java -cp cypher-model-parser-x.y.z.jar org.neo4j.cs.Obfuscator <query_string>
```

Full options:

```
Usage: Obfuscator [-hpV] [-d=dialect] [-o=<outputFile>] (CYPHER |
                  -f=CYPHER_FILE)
Obfuscate literal values in cypher query.
      CYPHER               The query to obfuscate
  -d, --dialect=dialect    The cypher dialect, one of : [5, 25]. Defaults to 25.
  -f, --file=CYPHER_FILE   Read CYPHER query from this file
  -h, --help               Show this help message and exit.
  -o, --output=<outputFile>
                           Output file generated, containing the obfuscated
                             query. If absent the output is sent to stdout.
  -p, --pretty             Pretty print the resulting cypher. This option has
                             no effect any more (no pretty printing).
  -V, --version            Print version information and exit.
```

This will return the obfuscated query on stdout.

Obfuscation rule: 
- all literals are replaced by ****
- no change is made to the general formatting of the query

Example: 
```
> java -cp cypher-model-parser-x.y.z.jar org.neo4j.cs.Obfuscator "MATCH (movie:Movie)-[r:HAS]->(x:Thing) WHERE movie.title CONTAINS 'sdfdg' or r.x = 12 or r.y IN [1290,'gh'] RETURN movie.title, r.role"

MATCH (movie:Movie)-[r:HAS]->(x:Thing) WHERE movie.title CONTAINS **** or r.x = **** or r.y IN [****,****] RETURN movie.title, r.role
```

# Query Profiler

**Usage:**

```bash
java -XX:+EnableDynamicAgentLoading -Xshare:off -cp cypher-model-parser-x.y.z.jar org.neo4j.cs.QueryProfiler -c counts.json -q "MATCH (n) RETURN n"
```

Full options:

```
Usage: QueryProfiler [-hV] -c=JSON_FILE [-d=<cypherVersion>] [-o=<outputFile>]
                     [-s=<storeFormat>] (-q=CYPHER | -f=FILE)
Plan and profile a Cypher query using graph count data.
  -c, --counts=JSON_FILE   Path to the JSON file containing graph counts.
  -d, --dialect=<cypherVersion>
                           Cypher version to use for planning. Default is 5.
  -f, --file=FILE          File containing the Cypher query.
  -h, --help               Show this help message and exit.
  -o, --output=<outputFile>
                           Output file generated, containing the formatted
                             plan. If absent the output is sent to stdout.
  -q, --query=CYPHER       The Cypher query string to plan.
  -s, --store-format=<storeFormat>
                           Store format to use. Default is block.
  -V, --version            Print version information and exit.
```

This will print the generated formatted query execution plan as a table to stdout (and optionally to an output file).

Example output:
```
+------------------+---------------------------------+
| Operator         | Details                         |
+------------------+---------------------------------+
| +ProduceResults  | "p", "pr"                       |
| |                +---------------------------------+
| +Filter          | "p:Part"                        |
| |                +---------------------------------+
| +ExpandAll       | "(pr)<-[:BELONGS_TO]-(p)"       |
| |                +---------------------------------+
| +NodeByLabelScan | "pr", "Product", IndexOrderNone |
+------------------+---------------------------------+
```