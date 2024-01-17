package org.neo4j.cs;

import org.neo4j.cypherdsl.core.StatementCatalog;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class QueryFilter {
    private static String prefixRegex_CYPHER="^CYPHER\s+";
    private static String prefixRegex_PLANNER="(planner=(cost|idp|dp)\s+)?";
    private static String prefixRegex_CCPLANNER="(connectComponentsPlanner=(greedy|idp)\s+)?";
    private static String prefixRegex_UPDATESTRATEGY="(updateStrategy=(default|eager)\s+)?";
    private static String prefixRegex_RUNTIME="(runtime=(interpreted|pipelined|slotted|parallel)\s+)?";
    private static String prefixRegex_EXPRESSIONENGINE="(expressionEngine=(compiled|interpreted|default)\s+)?";
    private static String prefixRegex_OPERATORENGINE="(operatorEngine=(compiled|interpreted|default)\s+)?";
    private static String prefixRegex_IPF="(interpretedPipesFallback=(default|disabled|whitelisted_plans_only|all)\s+)?";
    private static String prefixRegex_REPLAN="(replan=(default|force|skip)\s+)?";

    public List<String> filter(List<String> queries) {
        return queries.stream()
                .map(q -> q.trim())
                //ignore explain queries (sometimes used for autocomplete)
                // and admin queries like SHOW DATABASES
                .filter( q ->
                           !q.toUpperCase().startsWith("EXPLAIN")
                        && !q.toUpperCase().startsWith("SHOW ")
                        && !matchApocMetaCombinatorialQueries(q)
                        && !q.contains("This query is just used to load the cypher compiler during warmup. Please ignore")
                )
                //CYPHER modifiers are not supported by cypherDSL, so remove them
                .map(q -> q.replaceAll(
                                "(?i)"+ prefixRegex_CYPHER
                                            + prefixRegex_PLANNER
                                            + prefixRegex_CCPLANNER
                                            + prefixRegex_UPDATESTRATEGY
                                            + prefixRegex_RUNTIME
                                            + prefixRegex_EXPRESSIONENGINE
                                            + prefixRegex_OPERATORENGINE
                                            + prefixRegex_IPF
                                            + prefixRegex_REPLAN,
                                "")
                           .replaceAll("(?i)"+ "^PROFILE","")
                           .trim()
                )
                .collect(Collectors.toList());
    }


    //apoc.meta.schema throws many queries combining all known labels+types to retrieve counts, even if they don't exist in the model
    private boolean matchApocMetaCombinatorialQueries(String query) {
        String tokenRegex =  "([A-Za-z_][\\w]*|`.+`)";
        Pattern pattern1 = Pattern.compile("^MATCH \\(:"+tokenRegex+"\\)-\\[r:"+tokenRegex+"\\]->\\(\\) RETURN count\\(r\\) AS count$");
        Pattern pattern2 = Pattern.compile("^MATCH \\(\\)-\\[r:"+tokenRegex+"\\]->\\(:"+tokenRegex+"\\) RETURN count\\(r\\) AS count$");

        Matcher matcher1 = pattern1.matcher(query);
        Matcher matcher2 = pattern2.matcher(query);
        boolean result = matcher1.find() || matcher2.find();
        if (result) System.out.println("Filtering suspected apoc.meta query: "+query);
        return result;
    }




}
