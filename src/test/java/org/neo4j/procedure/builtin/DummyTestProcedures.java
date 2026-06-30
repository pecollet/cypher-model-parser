package org.neo4j.procedure.builtin;

import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.neo4j.procedure.UserFunction;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class DummyTestProcedures {

    public record DummyRecord(String field1, Long field2, boolean field3) {}

    @Procedure(name = "test.dummyProcedure", mode = Mode.READ)
    @Description("This is a dummy procedure.")
    public Stream<DummyRecord> dummyProcedure(
            @Name("paramString") String paramString,
            @Name("paramLong") Long paramLong,
            @Name("paramList") List<String> paramList
    ) {
        return Stream.empty();
    }

    @UserFunction(name = "test.dummyFunction")
    @Description("This is a dummy function.")
    public String dummyFunction(
            @Name("input") Map<String, Object> input
    ) {
        return "";
    }
}
