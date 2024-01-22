package org.neo4j.cs;

import lombok.Getter;
import org.neo4j.cs.model.*;
import org.neo4j.cs.model.NodeLabel;
import org.neo4j.cypherdsl.core.*;
import org.neo4j.cypherdsl.core.StatementCatalog.PropertyFilter;
import org.neo4j.cypherdsl.core.StatementCatalog.Property;
import org.neo4j.cypherdsl.core.ast.Visitable;
import org.neo4j.cypherdsl.core.ast.Visitor;
import org.neo4j.cypherdsl.parser.CyperDslParseException;
import org.neo4j.cypherdsl.parser.CypherParser;
import org.neo4j.cypherdsl.parser.UnsupportedCypherException;


import java.util.*;
import java.util.Set;
import java.util.stream.Collectors;

import static org.neo4j.cypherdsl.core.StatementCatalog.Token.Type.NODE_LABEL;
import static org.neo4j.cypherdsl.core.StatementCatalog.Token.Type.RELATIONSHIP_TYPE;

public class QueryParser {

    private static String PROPERTY_CLASS = "org.neo4j.cypherdsl.core.InternalPropertyImpl";

    @Getter
    private int errors = 0;

    private List<Operator> sameTypeOperators = new ArrayList<>();
    private List<Operator> numberOperators = new ArrayList<>();
    private List<Operator> stringOperators = new ArrayList<>();
    private List<Operator> booleanOperators = new ArrayList<>();

    private List<String> booleanFunctions = new ArrayList<>();
    private List<String> numberFunctions = new ArrayList<>();
    private List<String> stringFunctions = new ArrayList<>();
    private List<String> listFunctions = new ArrayList<>();
    private List<String> dateFunctions = new ArrayList<>();
    private List<String> timeFunctions = new ArrayList<>();
    private List<String> datetimeFunctions = new ArrayList<>();
    private List<String> durationFunctions = new ArrayList<>();
    private List<String> pointFunctions = new ArrayList<>();

    private Map<String,List> functionTypeMapping = new HashMap<>();


    public QueryParser() {
        this.sameTypeOperators.add(Operator.EQUALITY);
        this.sameTypeOperators.add(Operator.INEQUALITY);
        this.sameTypeOperators.add(Operator.LESS_THAN);
        this.sameTypeOperators.add(Operator.GREATER_THAN);
        this.sameTypeOperators.add(Operator.LESS_THAN_OR_EQUAL_TO);
        this.sameTypeOperators.add(Operator.GREATER_THAN_OR_EQUAL_TO);

        this.numberOperators.add(Operator.ADDITION);
        this.numberOperators.add(Operator.SUBTRACTION);
        this.numberOperators.add(Operator.UNARY_MINUS);
        this.numberOperators.add(Operator.UNARY_PLUS);
        this.numberOperators.add(Operator.MULTIPLICATION);
        this.numberOperators.add(Operator.DIVISION);
        this.numberOperators.add(Operator.MODULO_DIVISION);
        this.numberOperators.add(Operator.EXPONENTIATION);

        this.stringOperators.add(Operator.STARTS_WITH);
        this.stringOperators.add(Operator.CONTAINS);
        this.stringOperators.add(Operator.ENDS_WITH);
        this.stringOperators.add(Operator.CONCAT);
        this.stringOperators.add(Operator.MATCHES);

        this.booleanOperators.add(Operator.AND);
        this.booleanOperators.add(Operator.OR);
        this.booleanOperators.add(Operator.XOR);
        this.booleanOperators.add(Operator.NOT);

        booleanFunctions.add("toBoolean");
        booleanFunctions.add("toBooleanOrNull");
        booleanFunctions.add("point.withinBBox");

        numberFunctions.add("toInteger");
        numberFunctions.add("toIntegerOrNull");
        numberFunctions.add("toFloat");
        numberFunctions.add("toFloatOrNull");
        numberFunctions.add("timestamp");
        numberFunctions.add("size");
        numberFunctions.add("abs");
        numberFunctions.add("ceil");
        numberFunctions.add("floor");
        numberFunctions.add("rand");
        numberFunctions.add("round");
        numberFunctions.add("sign");
        numberFunctions.add("e");
        numberFunctions.add("exp");
        numberFunctions.add("log");
        numberFunctions.add("log10");
        numberFunctions.add("acos");
        numberFunctions.add("asin");
        numberFunctions.add("atan");
        numberFunctions.add("atan2");
        numberFunctions.add("cos");
        numberFunctions.add("cot");
        numberFunctions.add("degrees");
        numberFunctions.add("haversin");
        numberFunctions.add("pi");
        numberFunctions.add("radians");
        numberFunctions.add("sin");
        numberFunctions.add("tan");
        numberFunctions.add("point.distance");
        numberFunctions.add("char_length");
        numberFunctions.add("elementId");
        numberFunctions.add("character_length");
        numberFunctions.add("length");


        stringFunctions.add("valueType");
        stringFunctions.add("left");
        stringFunctions.add("ltrim");
        stringFunctions.add("replace");
        stringFunctions.add("right");
        stringFunctions.add("rtrim");
        stringFunctions.add("substring");
        stringFunctions.add("toLower");
        stringFunctions.add("toUpper");
        stringFunctions.add("toString");
        stringFunctions.add("toStringOrNull");
        stringFunctions.add("trim");
        stringFunctions.add("elementId");
        stringFunctions.add("type");

        listFunctions.add("split");
        listFunctions.add("keys");
        listFunctions.add("labels");
        listFunctions.add("nodes");
        listFunctions.add("range");
        listFunctions.add("relationships");
        listFunctions.add("tail");
        listFunctions.add("toBooleanList");
        listFunctions.add("toFloatList");
        listFunctions.add("toIntegerList");
        listFunctions.add("toStringList");

        dateFunctions.add("date");

        timeFunctions.add("time");
        timeFunctions.add("localtime");

        datetimeFunctions.add("datetime");
        datetimeFunctions.add("localdatetime");

        durationFunctions.add("duration");

        pointFunctions.add("point");

        functionTypeMapping.put("String", stringFunctions);
        functionTypeMapping.put("Boolean", booleanFunctions);
        functionTypeMapping.put("Number", numberFunctions);
        functionTypeMapping.put("List", listFunctions);
        functionTypeMapping.put("Date", dateFunctions);
        functionTypeMapping.put("Time", timeFunctions);
        functionTypeMapping.put("Datetime", datetimeFunctions);
        functionTypeMapping.put("Duration", durationFunctions);
        functionTypeMapping.put("Point", pointFunctions);
    }

    public Model parseQueries(List<String> queries) {
        this.errors = 0;
        Model fullModel = new Model();
        //iterate over queries
        System.out.println("Parsing queries...");
        for(String q : queries) {
            fullModel.add(parseQuery(q));
        }

        if (queries.size() > 0) {
            double errorRate = 100.0 * this.errors / queries.size();
            System.out.println("Parsing complete. Errors: " + this.errors + " (" + errorRate + "%)");
        } else {
            System.out.println("No queries to parse.");
        }

        //cleanup undirectedNodeLabels sets if labels are found in source or target sets
        for (Map.Entry<String, RelationshipType> rtEntry: fullModel.getRelationshipTypes().entrySet()) {
            RelationshipType rt = rtEntry.getValue();
            if (rt.getUndirectedNodeLabels().size() >0) {
                rt.getUndirectedNodeLabels().removeAll(rt.getSourceNodeLabels());
                rt.getUndirectedNodeLabels().removeAll(rt.getTargetNodeLabels());
            }

            rt.dedupeProperties();
        }

        //dedupe properties
        for (Map.Entry<String, NodeLabel> nlEntry: fullModel.getNodeLabels().entrySet()) {
            NodeLabel nl = nlEntry.getValue();
            nl.dedupeProperties();
        }
//        System.out.println(fullModel);
        return fullModel;
    }

    public Model parseQuery(String query) {
        Model queryModel = new Model();
        //System.out.println("QUERY = " +query.substring(0, Math.min(query.length(), 100)).replaceAll("\n", " "));
        Map<String, NodeLabel> nodeLabels = new HashMap<>();
        Map<String, RelationshipType> relationshipTypes = new HashMap<>();

        //parse
        try {
            var statement = CypherParser.parse(query);
            var catalog = statement.getCatalog();

            //relationshipTypes : populate a map (w/o properties)
            for (StatementCatalog.Token type : catalog.getRelationshipTypes()) {
                List<String> sourceNodeLabels = catalog.getSourceNodes(type).stream()
                        .map(t -> t.value()).collect(Collectors.toList());
                List<String> targetNodeLabels = catalog.getTargetNodes(type).stream()
                        .map(t -> t.value()).collect(Collectors.toList());
                RelationshipType rt = new RelationshipType(type.value());
                rt.setSourceNodeLabels( new HashSet<>(sourceNodeLabels) );
                rt.setTargetNodeLabels( new HashSet<>(targetNodeLabels) );
                relationshipTypes.put(type.value(), rt);
            }

            //node labels : populate a map of nodeLabels (w/o properties)
            for (StatementCatalog.Token label : catalog.getNodeLabels()) {
                nodeLabels.put(label.value(), new NodeLabel(label.value()));
//                System.out.println(label.value()+" => "+catalog.getUndirectedRelations(label));
                for  (StatementCatalog.Token undirRelType : catalog.getUndirectedRelations(label)) {

                    relationshipTypes.get(undirRelType.value()).addUndirectedNodelabel(label.value());
                }
            }

            //fill in the properties
            for (Property prop : catalog.getProperties()) {
                String key = prop.name();
                String propertyType = getPropertyType(prop, catalog);

                for (String label : prop.owningToken().stream()
                        .filter( t -> t.type().equals(NODE_LABEL))
                        .map(t -> t.value())
                        .collect(Collectors.toList())) {
                    if (propertyType != "?") {
                        nodeLabels.get(label).addProperty(key, propertyType);
                    } else {
                        nodeLabels.get(label).addProperty(key);
                    }
                }
                for (String type : prop.owningToken().stream()
                        .filter( t -> t.type().equals(RELATIONSHIP_TYPE))
                        .map(t -> t.value())
                        .collect(Collectors.toList())) {
                    if (propertyType != "?") {
                        relationshipTypes.get(type).addProperty(key, propertyType);
                    } else {
                        relationshipTypes.get(type).addProperty(key);
                    }
                }
            }

            queryModel.setNodeLabels(nodeLabels);
            queryModel.setRelationshipTypes(relationshipTypes);
        } catch (CyperDslParseException e) {
//            String parseExceptionText ="org.neo4j.cypherdsl.parser.internal.parser.javacc.ParseException:";
//            String cause = e.getCause().toString();
//            System.out.println("### [CyperDslParseException] " +
//                    cause.replace(parseExceptionText, "")
//                            .replaceAll("\n", " ")
//                            .substring(0, Math.min(100, cause.length() - parseExceptionText.length()))
//                    + " : " +shortenQueryForLogging(query)
//            );
            this.errors+=1;
        } catch (UnsupportedCypherException e) {
//            String cause = e.getCause().toString();
//            System.out.println("### [UnsupportedCypherException] " +
//                    cause.replaceAll("\n", " ")
//                    .substring(0, Math.min(100, cause.length()))
//                    + " : " +shortenQueryForLogging(query)
//            );
            this.errors+=1;
        } catch (NullPointerException npe) {
            System.out.println("### [NullPointerException] " + npe);
        } catch (Exception e) {
            System.out.println("### [Exception] " + e + " : " +shortenQueryForLogging(query) );
            this.errors+=1;
        }
//        System.out.println(queryModel);
        return queryModel;
    }


    private String shortenQueryForLogging(String query) {
        return query.replaceAll("\n", " ").substring(0, Math.min(100, query.length()));
    }

    private String getPropertyType(Property prop, StatementCatalog catalog) {
        // System.out.println("   "+prop.name());
        Set<String> propertyTypeCandidates = new HashSet<>();
        String propertyType="?";
        Collection<PropertyFilter> filters = catalog.getFilters(prop);

        if (filters != null) {
            //System.out.println("      "+prop.name()+" "+filters);
            //use filter expressions to try to find out the property types
            for (PropertyFilter filter: filters) {
                evaluateFilter(filter, propertyTypeCandidates);
            }

            //only set type if there's no ambiguity, i.e. only 1 candidate type
            if (propertyTypeCandidates.size() == 1) {
                //System.out.println("      " + propertyTypeCandidates.toString());
                propertyType=propertyTypeCandidates.stream().toList().get(0);
            }
        }
        return propertyType;
    }



    private void evaluateFilter(PropertyFilter f, Set<String> candidates){
        Operator operator = f.operator();
        Expression otherExpression;

        //get the opposite side of the predicate, that's compared to the property
        if ( f.left() != null && f.left().getClass().getName().equals(PROPERTY_CLASS) ) {
            otherExpression = f.right();
        } else if ( f.right() != null && f.right().getClass().getName().equals(PROPERTY_CLASS) ) {
            otherExpression = f.left();
        } else {
            return;
        }

        //special case for boolean predicate "WHERE n.prop" => no operator, no right side
        if (otherExpression == null) {
            if (operator == null) {
                candidates.add("Boolean");
            }
            return;
        }

        //skip some cases where we can't directly infer type (n.prop = $param, n.prop = .prop2)
        if (Parameter.class.isInstance(otherExpression)) { return; }
        if (otherExpression.getClass().getName().equals(PROPERTY_CLASS)) { return; }
        //System.out.println("         "+otherExpression);

        //operator is of a kind that implies both sides have the same type
        if (this.sameTypeOperators.contains(operator)) {
            //case A1 : property is compared to a literal
            if (Literal.class.isInstance(otherExpression)) {
                if (StringLiteral.class.isInstance(otherExpression)) {
                    candidates.add("String");
                } else if (NumberLiteral.class.isInstance(otherExpression)) {
                    candidates.add("Number");
                } else if (BooleanLiteral.class.isInstance(otherExpression)) {
                    candidates.add("Boolean");
                }
                //  System.out.println("         A1: "+otherExpression);
            //case A2 : property is compared to a list
            } else if (ListExpression.class.isInstance(otherExpression)) {
                //System.out.println("         A2: "+otherExpression);
                candidates.add("List");
            //case A3 : property is compared to a function with a defined return type
            } else if (FunctionInvocation.class.isInstance(otherExpression)) {
                String function = ((FunctionInvocation)otherExpression).getFunctionName();
                functionTypeMapping.entrySet().stream().forEach(e -> {
                    List<String> functionList = e.getValue();
                    if (functionList.contains(function)) {
                        candidates.add(e.getKey());
                        //System.out.println("         A3: func '"+function+"' => "+e.getKey());
                    }
                });
            }
        //case B1 : other operators directly imply a specific type (ex : STARTS_WITH => string)
        } else if (this.stringOperators.contains(operator)) {
            candidates.add("String");
            //System.out.println("         B1: "+operator+" string");
        } else if (this.numberOperators.contains(operator)) {
            candidates.add("Number");
            //System.out.println("         B1: "+operator+" Number");
        } else if (this.booleanOperators.contains(operator)) {
            candidates.add("Boolean");
            //System.out.println("         B1: "+operator+" Boolean");
        //case B2 : "n.prop IN [...]" pattern => use the type of the 1st literal in the list
        } else if (operator.equals(Operator.IN) && ListExpression.class.isInstance(otherExpression)) {
            //System.out.println("         B2: IN "+otherExpression);
            otherExpression.accept(new ListVisitor(candidates));
        }
    }

    protected class ListVisitor implements Visitor {
        private Set<String> candidates;
        private boolean found;
        public ListVisitor(Set<String> candidates) {
            this.candidates=candidates;
            this.found=false;
        }

        public void enter(Visitable v) {
            if (found) return;
            if (Literal.class.isInstance(v)) {
                if (StringLiteral.class.isInstance(v)) {
                    candidates.add("String");
                } else if (NumberLiteral.class.isInstance(v)) {
                    candidates.add("Number");
                } else if (BooleanLiteral.class.isInstance(v)) {
                    candidates.add("Boolean");
                }
            }

        }
        public void leave(Visitable segment) {
            if (this.candidates.size() > 0) found=true;
        }
    }
}
