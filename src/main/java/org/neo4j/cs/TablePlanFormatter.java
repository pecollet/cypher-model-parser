package org.neo4j.cs;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.neo4j.cypher.internal.logical.plans.LogicalPlan;
import org.neo4j.cypher.internal.expressions.LogicalVariable;

public class TablePlanFormatter {

    public static final String IDENTIFIERS = "Identifiers";
    public static final String DETAILS = "Details";
    public static final int MAX_DETAILS_COLUMN_WIDTH = 100;
    private static final String UNNAMED_PATTERN_STRING = "(UNNAMED|FRESHID|AGGREGATION|NODE|REL)(\\d+)";
    private static final Pattern UNNAMED_PATTERN = Pattern.compile(UNNAMED_PATTERN_STRING);
    private static final String OPERATOR = "Operator";
    private static final Pattern DEDUP_PATTERN = Pattern.compile("\\s*(\\S+)@\\d+");
    private static final List<String> HEADERS =
            asList(OPERATOR, DETAILS, IDENTIFIERS);

    private static void pad(int width, char chr, StringBuilder result) {
        result.append(repeat(chr, width));
    }

    private static int width(String header, Map<String, Integer> columns) {
        return 2 + Math.max(header.length(), columns.getOrDefault(header, 0));
    }

    private static void divider(
            List<String> headers, TableRow tableRow, StringBuilder result, Map<String, Integer> columns) {
        for (String header : headers) {
            if (tableRow != null && header.equals(OPERATOR) && tableRow.connection.isPresent()) {
                result.append("|");
                String connection = tableRow.connection.get();
                result.append(" ").append(connection);
                pad(width(header, columns) - connection.length() - 1, ' ', result);
            } else {
                result.append("+");
                pad(width(header, columns), '-', result);
            }
        }
        result.append("+").append("\n");
    }

    public String formatPlan(LogicalPlan plan) {
        Map<String, Integer> columns = new HashMap<>();
        
        List<String> planLines = Arrays.stream(plan.toString().split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
                
        int[] lineIndex = new int[]{0};
        List<TableRow> tableRows = accumulate(plan, new Root(), columns, planLines, lineIndex);

        // Remove Identifiers column if we have a Details column
        List<String> headers = HEADERS.stream()
                .filter(header ->
                        columns.containsKey(header) && !(header.equals(IDENTIFIERS) && columns.containsKey(DETAILS)))
                .collect(Collectors.toList());

        StringBuilder result = new StringBuilder();

        List<TableRow> allTableRows = new ArrayList<>();
        Map<String, Cell> headerMap = headers.stream()
                .map(header -> Pair.of(header, new LeftJustifiedCell(header)))
                .collect(toMap(p -> p._1, p -> p._2));
        allTableRows.add(new TableRow(OPERATOR, headerMap, Optional.empty()));
        allTableRows.addAll(tableRows);
        for (int rowIndex = 0; rowIndex < allTableRows.size(); rowIndex++) {
            TableRow tableRow = allTableRows.get(rowIndex);
            divider(headers, tableRow, result, columns);
            for (int rowLineIndex = 0; rowLineIndex < tableRow.height; rowLineIndex++) {
                for (String header : headers) {
                    Cell cell = tableRow.get(header);
                    String defaultText = "";
                    if (header.equals(OPERATOR) && rowIndex + 1 < allTableRows.size()) {
                        defaultText = allTableRows
                                .get(rowIndex + 1)
                                .connection
                                .orElse("")
                                .replace('\\', ' ');
                    }
                    result.append("| ");
                    int columnWidth = width(header, columns);
                    cell.writePaddedLine(rowLineIndex, defaultText, columnWidth, result);
                    result.append(" ");
                }
                result.append("|").append("\n");
            }
        }
        divider(headers, null, result, columns);

        return result.toString();
    }

    private List<TableRow> accumulate(LogicalPlan plan, Level level, Map<String, Integer> columns, List<String> planLines, int[] lineIndex) {
        String rawLine = lineIndex[0] < planLines.size() ? planLines.get(lineIndex[0]) : "";
        lineIndex[0]++;

        String opCall = parseOpCall(rawLine);
        String operatorName = parseOperatorName(opCall);
        String detailsStr = parseDetails(opCall);

        String capitalizedOpName = capitalize(operatorName);
        String line = level.line() + capitalizedOpName;
        mapping(OPERATOR, new LeftJustifiedCell(line), columns);

        Map<String, Cell> rowDetails = details(plan, detailsStr, columns);

        return Stream.concat(
                        Stream.of(new TableRow(line, rowDetails, level.connector())),
                        children(plan, level, columns, planLines, lineIndex).stream().flatMap(Collection::stream))
                .collect(Collectors.toList());
    }

    private List<List<TableRow>> children(LogicalPlan plan, Level level, Map<String, Integer> columns, List<String> planLines, int[] lineIndex) {
        List<List<TableRow>> result = new ArrayList<>();
        if (plan.rhs().isDefined()) {
            result.add(accumulate(plan.rhs().get(), level.fork(), columns, planLines, lineIndex));
            result.add(accumulate(plan.lhs().get(), level.child(), columns, planLines, lineIndex));
        } else if (plan.lhs().isDefined()) {
            result.add(accumulate(plan.lhs().get(), level.child(), columns, planLines, lineIndex));
        }
        return result;
    }

    private static Map<String, Cell> details(LogicalPlan plan, String detailsStr, Map<String, Integer> columns) {
        Map<String, Cell> cellMap = new HashMap<>();

        if (detailsStr != null && !detailsStr.isEmpty()) {
            mapping(DETAILS, new LeftJustifiedCell(splitDetails(detailsStr)), columns)
                    .ifPresent(p -> cellMap.put(p._1, p._2));
        }

        String idents = identifiers(plan, columns);
        if (idents != null && !idents.isEmpty()) {
            cellMap.put(IDENTIFIERS, new LeftJustifiedCell(idents));
        }

        return cellMap;
    }

    private static Optional<Pair<String, Cell>> mapping(String key, Cell value, Map<String, Integer> columns) {
        update(columns, key, value.length);
        return Optional.of(Pair.of(key, value));
    }

    private static String replaceAllIn(Pattern pattern, String s, Function<Matcher, String> mapper) {
        StringBuilder sb = new StringBuilder();
        Matcher matcher = pattern.matcher(s);
        while (matcher.find()) {
            matcher.appendReplacement(sb, mapper.apply(matcher));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String removeGeneratedNames(String s) {
        String named = replaceAllIn(UNNAMED_PATTERN, s, m -> "anon[" + m.group(2) + "]");
        return replaceAllIn(DEDUP_PATTERN, named, m -> m.group(1));
    }

    private static void update(Map<String, Integer> columns, String key, int length) {
        columns.put(key, Math.max(columns.getOrDefault(key, 0), length));
    }

    private static String identifiers(LogicalPlan plan, Map<String, Integer> columns) {
        Set<LogicalVariable> scalaSet = scala.jdk.javaapi.CollectionConverters.asJava(plan.availableSymbols());
        String result = scalaSet.stream()
                .map(LogicalVariable::name)
                .map(TablePlanFormatter::removeGeneratedNames)
                .sorted()
                .collect(Collectors.joining(", "));
        if (!result.isEmpty()) {
            update(columns, IDENTIFIERS, result.length());
        }
        return result;
    }

    private static String[] splitDetails(String original) {
        List<String> detailsList = new ArrayList<>();

        int currentPos = 0;
        while (currentPos < original.length()) {
            int newPos = Math.min(original.length(), currentPos + MAX_DETAILS_COLUMN_WIDTH);
            detailsList.add(original.substring(currentPos, newPos));
            currentPos = newPos;
        }

        return detailsList.toArray(new String[0]);
    }

    private static String parseOpCall(String rawLine) {
        if (rawLine == null) {
            return "";
        }
        int firstLetterIdx = -1;
        for (int i = 0; i < rawLine.length(); i++) {
            char c = rawLine.charAt(i);
            if (Character.isLetter(c)) {
                firstLetterIdx = i;
                break;
            }
        }
        if (firstLetterIdx != -1) {
            return rawLine.substring(firstLetterIdx);
        }
        return rawLine.trim();
    }

    private static String parseOperatorName(String opCall) {
        int firstParen = opCall.indexOf('(');
        if (firstParen != -1) {
            return opCall.substring(0, firstParen);
        }
        return opCall;
    }

    private static String parseDetails(String opCall) {
        int firstParen = opCall.indexOf('(');
        int lastParen = opCall.lastIndexOf(')');
        if (firstParen != -1 && lastParen != -1 && lastParen > firstParen) {
            return opCall.substring(firstParen + 1, lastParen);
        }
        return "";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String repeat(String str, int count) {
        if (count <= 0) {
            return "";
        }
        return str.repeat(count);
    }

    private static String repeat(char chr, int count) {
        if (count <= 0) {
            return "";
        }
        return String.valueOf(chr).repeat(count);
    }

    static class TableRow {
        private final String tree;
        private final Map<String, Cell> cells;
        private final Optional<String> connection;
        private final int height;

        TableRow(String tree, Map<String, Cell> cells, Optional<String> connection) {
            this.tree = tree;
            this.cells = cells;
            this.connection = connection == null ? Optional.empty() : connection;
            this.height =
                    cells.values().stream().mapToInt(v -> v.lines.length).max().orElse(0);
        }

        Cell get(String key) {
            if (key.equals(TablePlanFormatter.OPERATOR)) {
                return new LeftJustifiedCell(tree);
            } else {
                return cells.getOrDefault(key, new LeftJustifiedCell(""));
            }
        }
    }

    abstract static class Cell {
        final int length;
        final String[] lines;

        Cell(String[] lines) {
            this.length = Stream.of(lines).mapToInt(String::length).max().orElse(0);
            this.lines = lines;
        }

        abstract void writePaddedLine(int lineIndex, String orElseValue, int columnWidth, StringBuilder result);

        protected static int paddingWidth(int columnWidth, String line) {
            return columnWidth - line.length() - 2;
        }

        protected String getLineOrElse(int lineIndex, String orElseValue) {
            if (lineIndex < lines.length) {
                return lines[lineIndex];
            } else {
                return orElseValue;
            }
        }
    }

    static class LeftJustifiedCell extends Cell {
        LeftJustifiedCell(String... lines) {
            super(lines);
        }

        @Override
        void writePaddedLine(int lineIndex, String orElseValue, int columnWidth, StringBuilder result) {
            String line = getLineOrElse(lineIndex, orElseValue);
            result.append(line);
            pad(paddingWidth(columnWidth, line), ' ', result);
        }
    }

    static class RightJustifiedCell extends Cell {
        RightJustifiedCell(String... lines) {
            super(lines);
        }

        @Override
        void writePaddedLine(int lineIndex, String orElseValue, int columnWidth, StringBuilder result) {
            String line = getLineOrElse(lineIndex, orElseValue);
            pad(paddingWidth(columnWidth, line), ' ', result);
            result.append(line);
        }
    }

    abstract static class Level {
        abstract Level child();

        abstract Level fork();

        abstract String line();

        abstract Optional<String> connector();
    }

    static class Root extends Level {
        @Override
        Level child() {
            return new Child(1);
        }

        @Override
        Level fork() {
            return new Fork(2);
        }

        @Override
        String line() {
            return "+";
        }

        @Override
        Optional<String> connector() {
            return Optional.empty();
        }
    }

    static class Child extends Level {
        private final int level;

        Child(int level) {
            this.level = level;
        }

        @Override
        Level child() {
            return new Child(level);
        }

        @Override
        Level fork() {
            return new Fork(level + 1);
        }

        @Override
        String line() {
            return repeat("| ", level - 1) + "+";
        }

        @Override
        Optional<String> connector() {
            return Optional.of(repeat("| ", level));
        }
    }

    static class Fork extends Level {
        private final int level;

        Fork(int level) {
            this.level = level;
        }

        @Override
        Level child() {
            return new Child(level);
        }

        @Override
        Level fork() {
            return new Fork(level + 1);
        }

        @Override
        String line() {
            return repeat("| ", level - 1) + "+";
        }

        @Override
        Optional<String> connector() {
            return Optional.of(repeat("| ", level - 2) + "|\\");
        }
    }

    static final class Pair<T1, T2> {
        final T1 _1;
        final T2 _2;

        private Pair(T1 _1, T2 _2) {
            this._1 = _1;
            this._2 = _2;
        }

        public static <T1, T2> Pair<T1, T2> of(T1 _1, T2 _2) {
            return new Pair<>(_1, _2);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Pair<?, ?> pair = (Pair<?, ?>) o;
            return _1.equals(pair._1) && _2.equals(pair._2);
        }

        @Override
        public int hashCode() {
            return 31 * _1.hashCode() + _2.hashCode();
        }
    }
}
