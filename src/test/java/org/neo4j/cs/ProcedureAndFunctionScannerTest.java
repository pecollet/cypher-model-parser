package org.neo4j.cs;

import org.junit.jupiter.api.Test;
import org.neo4j.cypher.internal.frontend.phases.ProcedureSignature;
import org.neo4j.cypher.internal.frontend.phases.UserFunctionSignature;
import org.neo4j.cypher.internal.util.symbols.BooleanType;
import org.neo4j.cypher.internal.util.symbols.IntegerType;
import org.neo4j.cypher.internal.util.symbols.ListType;
import org.neo4j.cypher.internal.util.symbols.MapType;
import org.neo4j.cypher.internal.util.symbols.StringType;

import static org.junit.jupiter.api.Assertions.*;

public class ProcedureAndFunctionScannerTest {

    @Test
    public void testScanning() {
        ProcedureAndFunctionScanner scanner = new ProcedureAndFunctionScanner();
        scanner.scanClassPath();

        // 1. Check procedures
        assertFalse(scanner.getProcedures().isEmpty(), "Should find some procedures");
        
        // Find our dummy procedure
        ProcedureSignature dummyProc = scanner.getProcedures().stream()
                .filter(p -> p.name().fullName().equals("test.dummyProcedure"))
                .findFirst()
                .orElse(null);

        assertNotNull(dummyProc, "Should locate test.dummyProcedure");
        assertEquals("test.dummyProcedure", dummyProc.name().fullName());
        assertEquals("This is a dummy procedure.", dummyProc.description().get());
        assertTrue(dummyProc.accessMode() instanceof org.neo4j.cypher.internal.frontend.phases.ProcedureReadOnlyAccess$);

        // Inputs
        var inputs = dummyProc.inputSignature();
        assertEquals(3, inputs.size());
        assertEquals("paramString", inputs.apply(0).name());
        assertTrue(inputs.apply(0).typ() instanceof StringType);
        assertEquals("paramLong", inputs.apply(1).name());
        assertTrue(inputs.apply(1).typ() instanceof IntegerType);
        assertEquals("paramList", inputs.apply(2).name());
        assertTrue(inputs.apply(2).typ() instanceof ListType);

        // Outputs
        assertTrue(dummyProc.outputSignature().isDefined());
        var outputs = dummyProc.outputSignature().get();
        assertEquals(3, outputs.size());
        assertEquals("field1", outputs.apply(0).name());
        assertTrue(outputs.apply(0).typ() instanceof StringType);
        assertEquals("field2", outputs.apply(1).name());
        assertTrue(outputs.apply(1).typ() instanceof IntegerType);
        assertEquals("field3", outputs.apply(2).name());
        assertTrue(outputs.apply(2).typ() instanceof BooleanType);

        // 2. Check functions
        assertFalse(scanner.getFunctions().isEmpty(), "Should find some functions");

        // Find our dummy function
        UserFunctionSignature dummyFunc = scanner.getFunctions().stream()
                .filter(f -> f.name().fullName().equals("test.dummyFunction"))
                .findFirst()
                .orElse(null);

        assertNotNull(dummyFunc, "Should locate test.dummyFunction");
        assertEquals("This is a dummy function.", dummyFunc.description().get());
        assertTrue(dummyFunc.outputType() instanceof StringType);

        var funcInputs = dummyFunc.inputSignature();
        assertEquals(1, funcInputs.size());
        assertEquals("input", funcInputs.apply(0).name());
        assertTrue(funcInputs.apply(0).typ() instanceof MapType);
    }

    @Test
    public void testBuiltInPingIsPresent() {
        ProcedureAndFunctionScanner scanner = new ProcedureAndFunctionScanner();
        scanner.scanClassPath();

        // Check if db.ping is found on standard classpath (from neo4j-procedure jar)
        ProcedureSignature pingProc = scanner.getProcedures().stream()
                .filter(p -> p.name().fullName().equals("db.ping"))
                .findFirst()
                .orElse(null);

        assertNotNull(pingProc, "Should locate standard db.ping procedure");
        assertEquals("db.ping", pingProc.name().fullName());
    }

    @Test
    public void testScanPluginsDirectory() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("neo4j-plugins-test");
        java.nio.file.Path jarPath = tempDir.resolve("my-plugin.jar");

        // Prepare the JAR with DummyTestProcedures class
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(jarPath.toFile());
             java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(fos)) {
            
            // Write class entry
            String classResourcePath = "/org/neo4j/procedure/builtin/DummyTestProcedures.class";
            try (java.io.InputStream is = ProcedureAndFunctionScannerTest.class.getResourceAsStream(classResourcePath)) {
                assertNotNull(is, "DummyTestProcedures.class should be available as resource");
                jos.putNextEntry(new java.util.zip.ZipEntry("org/neo4j/procedure/builtin/DummyTestProcedures.class"));
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    jos.write(buffer, 0, bytesRead);
                }
                jos.closeEntry();
            }
        }

        // Scan the directory
        ProcedureAndFunctionScanner scanner = new ProcedureAndFunctionScanner();
        // Since we are scanning a custom directory, scanClassPath won't have it, but scanPluginsDirectory will
        scanner.scanPluginsDirectory(tempDir.toFile());

        // Verify we found test.dummyProcedure
        assertFalse(scanner.getProcedures().isEmpty(), "Should find procedures in the plugins directory");
        ProcedureSignature dummyProc = scanner.getProcedures().stream()
                .filter(p -> p.name().fullName().equals("test.dummyProcedure"))
                .findFirst()
                .orElse(null);
        assertNotNull(dummyProc, "Should find the plugin procedure");

        // Clean up
        java.nio.file.Files.deleteIfExists(jarPath);
        java.nio.file.Files.deleteIfExists(tempDir);
    }
}
