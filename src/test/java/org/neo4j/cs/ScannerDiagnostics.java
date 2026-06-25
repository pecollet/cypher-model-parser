package org.neo4j.cs;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

public class ScannerDiagnostics {
    @Test
    public void runDiagnostics() {
        String className = "org.neo4j.procedure.builtin.BuiltInProcedures";
        System.out.println("=== DIAGNOSTICS FOR " + className + " ===");
        try {
            Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            System.out.println("1. Class.forName succeeded!");
            
            try {
                System.out.println("2. Trying clazz.getDeclaredMethods()...");
                Method[] methods = clazz.getDeclaredMethods();
                System.out.println("   Success! Found " + methods.length + " methods.");
            } catch (Throwable t) {
                System.out.println("   FAILED clazz.getDeclaredMethods()!");
                t.printStackTrace();
            }

            try {
                System.out.println("3. Trying clazz.getMethods()...");
                Method[] methods = clazz.getMethods();
                System.out.println("   Success! Found " + methods.length + " public methods.");
            } catch (Throwable t) {
                System.out.println("   FAILED clazz.getMethods()!");
                t.printStackTrace();
            }

        } catch (Throwable t) {
            System.out.println("Class.forName FAILED!");
            t.printStackTrace();
        }
    }
}
