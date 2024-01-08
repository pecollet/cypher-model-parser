Tool that parses a file containing a list of cypher queries (as exported by the health check), and generates a plantUML diagram (as a text file).



Usage:

```
java -jar cypher-model-parser-1.0-SNAPSHOT.jar queries_map.csv
```

That pantUML text file can be turned into a PNG image via a plantUML server (public server plantuml.com for example).

cf. https://github.com/SamuelMarks/python-plantuml
```
pip install plantuml
```

```
python path/to/plantuml.py model.puml
```
