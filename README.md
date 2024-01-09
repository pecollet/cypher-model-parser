Tool that parses a file containing a list of cypher queries (as exported by the health check), and generates a plantUML class diagram.



Usage:

```
java -jar cypher-model-parser-1.0-SNAPSHOT.jar queries_map.csv
```

This will produce :
- model.puml : text file with the plantUML description of the class diagram
- model.puml.svg : image of the diagram


Pre-requisites:
- JRE 17
- having the queries_map.csv file. Run a HC with option `-deep` for the HC to output that file. It will contain all the distinct cypher queries present in the query.logs for the selected database.