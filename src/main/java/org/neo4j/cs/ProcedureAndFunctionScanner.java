package org.neo4j.cs;

import org.neo4j.cypher.internal.frontend.phases.FieldSignature;
import org.neo4j.cypher.internal.frontend.phases.ProcedureSignature;
import org.neo4j.cypher.internal.frontend.phases.UserFunctionSignature;
import org.neo4j.cypher.internal.util.symbols.CypherType;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.neo4j.procedure.UserFunction;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ProcedureAndFunctionScanner {
    private final List<ProcedureSignature> procedures = new ArrayList<>();
    private final List<UserFunctionSignature> functions = new ArrayList<>();
    private final Set<File> scannedElements = new HashSet<>();
    private ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    private static final List<String> PACKAGE_PREFIXES = Arrays.asList(
            "org/neo4j/procedure/builtin/",
            "apoc/"
    );

    public List<ProcedureSignature> getProcedures() {
        return procedures;
    }

    public List<UserFunctionSignature> getFunctions() {
        return functions;
    }

    public void scanClassPath() {
        // 1. Try to scan via java.class.path
        String classpath = System.getProperty("java.class.path");
        if (classpath != null && !classpath.isEmpty()) {
            String[] elements = classpath.split(File.pathSeparator);
            for (String element : elements) {
                File file = new File(element);
                if (!file.exists()) {
                    continue;
                }
                try {
                    File canonical = file.getCanonicalFile();
                    if (this.scannedElements.add(canonical)) {
                        if (canonical.isDirectory()) {
                            scanDirectory(canonical, canonical, "");
                        } else if (canonical.isFile() && (canonical.getName().endsWith(".jar") || canonical.getName().endsWith(".zip"))) {
                            scanJar(canonical, true);
                        }
                    }
                } catch (Throwable t) {
                    // Ignore any scanning/class loading errors to be completely robust
                }
            }
        }

        // 2. Locate known Neo4j builtin, test, and scanner classes to guarantee discovery
        // even if running under a manifest bootstrap JAR or custom classloader in IDEs
        List<String> knownClasses = Arrays.asList(
                "org.neo4j.procedure.builtin.BuiltInProcedures",
                "org.neo4j.procedure.builtin.BuiltInDbmsProcedures",
                "org.neo4j.procedure.builtin.SchemaProcedure",
                "org.neo4j.procedure.builtin.DummyTestProcedures",
                "org.neo4j.cs.ProcedureAndFunctionScanner",
                "apoc.ApocConfig"
        );

        for (String className : knownClasses) {
            File location = locateJarOrDirectoryForClass(className);
            if (location != null && location.exists()) {
                try {
                    File canonical = location.getCanonicalFile();
                    if (this.scannedElements.add(canonical)) {
                        if (canonical.isDirectory()) {
                            scanDirectory(canonical, canonical, "");
                        } else if (canonical.isFile() && (canonical.getName().endsWith(".jar") || canonical.getName().endsWith(".zip"))) {
                            scanJar(canonical, true);
                        }
                    }
                } catch (Throwable t) {
                    // Ignore
                }
            }
        }
    }

    private File locateJarOrDirectoryForClass(String className) {
        try {
            Class<?> clazz = Class.forName(className, false, this.classLoader);
            java.security.ProtectionDomain pd = clazz.getProtectionDomain();
            if (pd != null && pd.getCodeSource() != null) {
                java.net.URL location = pd.getCodeSource().getLocation();
                if (location != null && "file".equals(location.getProtocol())) {
                    return new File(location.toURI());
                }
            }
        } catch (Throwable t) {
            // Ignore
        }

        // Alternative using resource loading
        try {
            String resourceName = className.replace('.', '/') + ".class";
            java.net.URL resource = this.classLoader.getResource(resourceName);
            if (resource != null) {
                String urlStr = resource.toString();
                if (urlStr.startsWith("jar:file:")) {
                    int exclam = urlStr.indexOf('!');
                    if (exclam > 0) {
                        String filePath = urlStr.substring("jar:file:".length(), exclam);
                        filePath = java.net.URLDecoder.decode(filePath, java.nio.charset.StandardCharsets.UTF_8);
                        return new File(filePath);
                    }
                } else if (urlStr.startsWith("file:")) {
                    String filePath = urlStr.substring("file:".length(), urlStr.length() - resourceName.length());
                    filePath = java.net.URLDecoder.decode(filePath, java.nio.charset.StandardCharsets.UTF_8);
                    return new File(filePath);
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
        return null;
    }

    public void scanPluginsDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        List<URL> urls = new ArrayList<>();
        List<File> jars = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && (file.getName().endsWith(".jar") || file.getName().endsWith(".zip"))) {
                try {
                    urls.add(file.toURI().toURL());
                    jars.add(file);
                } catch (Throwable t) {
                    // Ignore URL conversion errors
                }
            }
        }
        if (urls.isEmpty()) {
            return;
        }

        URLClassLoader urlClassLoader = new URLClassLoader(
                urls.toArray(new URL[0]),
                this.classLoader
        );
        this.classLoader = urlClassLoader;

        for (File jar : jars) {
            try {
                scanJar(jar, false);
            } catch (Throwable t) {
                // Ignore individual jar scanning errors
            }
        }
    }

    private void scanDirectory(File root, File current, String packagePath) {
        File[] files = current.listFiles();
        if (files == null) {
            return;
        }

        for (File f : files) {
            if (f.isDirectory()) {
                String subPath = packagePath.isEmpty() ? f.getName() : packagePath + "/" + f.getName();
                if (isPotentialPackage(subPath)) {
                    scanDirectory(root, f, subPath);
                }
            } else if (f.isFile() && f.getName().endsWith(".class")) {
                String subPath = packagePath.isEmpty() ? f.getName() : packagePath + "/" + f.getName();
                if (shouldProcess(subPath)) {
                    String className = subPath.substring(0, subPath.length() - ".class".length()).replace('/', '.');
                    processClass(className);
                }
            }
        }
    }

    private boolean isPotentialPackage(String subPath) {
        String pathWithSlash = subPath + "/";
        for (String prefix : PACKAGE_PREFIXES) {
            if (prefix.startsWith(pathWithSlash) || pathWithSlash.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldProcess(String subPath) {
        for (String prefix : PACKAGE_PREFIXES) {
            if (subPath.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void scanJar(File jarFile, boolean checkPrefix) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            // Check manifest for Class-Path header (important for manifest bootstrap JARs used by IntelliJ, Maven, etc.)
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest != null) {
                java.util.jar.Attributes attribs = manifest.getMainAttributes();
                String classPathAttr = attribs.getValue(java.util.jar.Attributes.Name.CLASS_PATH);
                if (classPathAttr != null && !classPathAttr.isEmpty()) {
                    File parentDir = jarFile.getParentFile();
                    String[] parts = classPathAttr.split("\\s+");
                    for (String part : parts) {
                        try {
                            File referencedFile = new File(parentDir, part);
                            if (referencedFile.exists()) {
                                File canonical = referencedFile.getCanonicalFile();
                                if (this.scannedElements.add(canonical)) {
                                    if (canonical.isDirectory()) {
                                        scanDirectory(canonical, canonical, "");
                                    } else if (canonical.isFile() && (canonical.getName().endsWith(".jar") || canonical.getName().endsWith(".zip"))) {
                                        scanJar(canonical, checkPrefix);
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            // Ignore referenced classpath element scanning issues
                        }
                    }
                }
            }

            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && (!checkPrefix || shouldProcess(name))) {
                    String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                    processClass(className);
                }
            }
        }
    }

    private void processClass(String className) {
        try {
            Class<?> clazz = Class.forName(className, false, this.classLoader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Procedure.class)) {
                    try {
                        buildProcedureSignature(method);
                    } catch (Throwable t) {
                        // Ignore individual method failures
                    }
                } else if (method.isAnnotationPresent(UserFunction.class)) {
                    try {
                        buildUserFunctionSignature(method);
                    } catch (Throwable t) {
                        // Ignore individual method failures
                    }
                }
            }
        } catch (Throwable t) {
            // Ignore class loading or parsing errors
        }
    }

    private void buildProcedureSignature(Method method) {
        Procedure procAnno = method.getAnnotation(Procedure.class);
        String fullName = procAnno.name();
        if (fullName.isEmpty()) {
            fullName = procAnno.value();
        }
        if (fullName.isEmpty()) {
            fullName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        }

        var namespaceAndName = parseName(fullName);
        var namespace = namespaceAndName.namespace;
        var simpleName = namespaceAndName.simpleName;

        var procName = new org.neo4j.cypher.internal.util.ProcedureName(
                namespace,
                simpleName,
                org.neo4j.cypher.internal.util.InputPosition$.MODULE$.NONE()
        );

        List<FieldSignature> inputFields = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            inputFields.add(buildFieldSignature(param));
        }

        Class<?> recordClass = getRecordClass(method);
        List<FieldSignature> outputFields = new ArrayList<>();
        if (recordClass != null && recordClass != void.class && recordClass != Void.class) {
            if (recordClass.isRecord()) {
                for (var component : recordClass.getRecordComponents()) {
                    outputFields.add(buildRecordComponentSignature(component));
                }
            } else {
                for (var field : recordClass.getFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        outputFields.add(buildFieldSignature(field));
                    }
                }
            }
        }

        scala.Option<scala.collection.immutable.IndexedSeq<FieldSignature>> scalaOutputFields = outputFields.isEmpty()
                ? scala.Option.empty()
                : scala.Option.apply(toScalaIndexedSeq(outputFields));

        var descriptionAnno = method.getAnnotation(Description.class);
        scala.Option<String> description = (descriptionAnno != null)
                ? scala.Option.apply(descriptionAnno.value())
                : scala.Option.empty();

        var accessMode = mapAccessMode(procAnno.mode());

        var pingSignature = new ProcedureSignature(
                procName,
                toScalaIndexedSeq(inputFields),
                scalaOutputFields,
                scala.Option.empty(), // deprecationInfo
                accessMode,
                description,
                procAnno.warning().isEmpty() ? scala.Option.empty() : scala.Option.apply(procAnno.warning()),
                procAnno.eager(),
                0, // id
                true, // systemProcedure
                true, // allowExpiredCredentials
                true // threadSafe
        );

        procedures.add(pingSignature);
    }

    private void buildUserFunctionSignature(Method method) {
        UserFunction funcAnno = method.getAnnotation(UserFunction.class);
        String fullName = funcAnno.name();
        if (fullName.isEmpty()) {
            fullName = funcAnno.value();
        }
        if (fullName.isEmpty()) {
            fullName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        }

        var namespaceAndName = parseName(fullName);
        var namespace = namespaceAndName.namespace;
        var simpleName = namespaceAndName.simpleName;

        var funcName = new org.neo4j.cypher.internal.util.FunctionName(
                namespace,
                simpleName,
                org.neo4j.cypher.internal.util.InputPosition$.MODULE$.NONE()
        );

        List<FieldSignature> inputFields = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            inputFields.add(buildFieldSignature(param));
        }

        CypherType outputType = mapJavaType(method.getReturnType(), method.getGenericReturnType());

        var descriptionAnno = method.getAnnotation(Description.class);
        scala.Option<String> description = (descriptionAnno != null)
                ? scala.Option.apply(descriptionAnno.value())
                : scala.Option.empty();

        var signature = new UserFunctionSignature(
                funcName,
                toScalaIndexedSeq(inputFields),
                outputType,
                scala.Option.empty(), // deprecationInfo
                description,
                false, // isAggregate
                0, // id
                true, // builtIn
                true // threadSafe
        );

        functions.add(signature);
    }

    private static class NamespaceAndSimpleName {
        final org.neo4j.cypher.internal.util.Namespace namespace;
        final String simpleName;

        NamespaceAndSimpleName(org.neo4j.cypher.internal.util.Namespace namespace, String simpleName) {
            this.namespace = namespace;
            this.simpleName = simpleName;
        }
    }

    private NamespaceAndSimpleName parseName(String fullName) {
        List<String> namespaceParts;
        String simpleName;
        if (fullName.contains(".")) {
            String[] parts = fullName.split("\\.");
            namespaceParts = java.util.Arrays.asList(parts).subList(0, parts.length - 1);
            simpleName = parts[parts.length - 1];
        } else {
            namespaceParts = Collections.emptyList();
            simpleName = fullName;
        }

        var namespace = new org.neo4j.cypher.internal.util.Namespace(
                scala.collection.immutable.List$.MODULE$.from(
                        scala.jdk.javaapi.CollectionConverters.asScala(namespaceParts)
                ),
                org.neo4j.cypher.internal.util.InputPosition$.MODULE$.NONE()
        );

        return new NamespaceAndSimpleName(namespace, simpleName);
    }

    private FieldSignature buildFieldSignature(Parameter param) {
        String name = param.isAnnotationPresent(Name.class)
                ? param.getAnnotation(Name.class).value()
                : param.getName();
        String desc = param.isAnnotationPresent(Name.class)
                ? param.getAnnotation(Name.class).description()
                : "";

        CypherType type = mapJavaType(param.getType(), param.getParameterizedType());
        return new FieldSignature(
                name,
                type,
                scala.Option.empty(),
                false,
                false,
                desc
        );
    }

    private FieldSignature buildFieldSignature(java.lang.reflect.Field field) {
        CypherType type = mapJavaType(field.getType(), field.getGenericType());
        return new FieldSignature(
                field.getName(),
                type,
                scala.Option.empty(),
                false,
                false,
                ""
        );
    }

    private FieldSignature buildRecordComponentSignature(java.lang.reflect.RecordComponent component) {
        CypherType type = mapJavaType(component.getType(), component.getGenericType());
        return new FieldSignature(
                component.getName(),
                type,
                scala.Option.empty(),
                false,
                false,
                ""
        );
    }

    private CypherType mapJavaType(Class<?> rawType, Type genericType) {
        if (rawType == String.class) {
            return org.neo4j.cypher.internal.util.symbols.StringType$.MODULE$.apply(true, org.neo4j.cypher.internal.util.InputPosition$.MODULE$.NONE());
        } else if (rawType == Long.class || rawType == long.class ||
                   rawType == Integer.class || rawType == int.class ||
                   rawType == Short.class || rawType == short.class ||
                   rawType == Byte.class || rawType == byte.class) {
            return org.neo4j.cypher.internal.util.symbols.IntegerType$.MODULE$.apply(true, org.neo4j.cypher.internal.util.InputPosition$.MODULE$.NONE());
        } else if (rawType == Boolean.class || rawType == boolean.class) {
            return org.neo4j.cypher.internal.util.symbols.BooleanType$.MODULE$.apply(true, org.neo4j.cypher.internal.util.InputPosition$.MODULE$.NONE());
        } else if (rawType == Double.class || rawType == double.class ||
                   rawType == Float.class || rawType == float.class) {
            return org.neo4j.cypher.internal.util.symbols.FloatType$.MODULE$.apply(true, org.neo4j.cypher.internal.util.InputPosition$.MODULE$.NONE());
        } else if (Collection.class.isAssignableFrom(rawType) || rawType.isArray()) {
            CypherType innerType = org.neo4j.cypher.internal.util.symbols.AnyType$.MODULE$.apply(true, org.neo4j.cypher.internal.util.InputPosition$.MODULE$.NONE());
            if (genericType instanceof ParameterizedType) {
                ParameterizedType pType = (ParameterizedType) genericType;
                Type[] typeArgs = pType.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    innerType = mapJavaType((Class<?>) typeArgs[0], typeArgs[0]);
                }
            } else if (rawType.isArray()) {
                innerType = mapJavaType(rawType.getComponentType(), rawType.getComponentType());
            }
            return org.neo4j.cypher.internal.util.symbols.ListType$.MODULE$.apply(innerType, true, org.neo4j.cypher.internal.util.InputPosition$.MODULE$.NONE());
        } else if (Map.class.isAssignableFrom(rawType)) {
            return org.neo4j.cypher.internal.util.symbols.MapType$.MODULE$.apply(true, org.neo4j.cypher.internal.util.InputPosition$.MODULE$.NONE());
        } else {
            return org.neo4j.cypher.internal.util.symbols.AnyType$.MODULE$.apply(true, org.neo4j.cypher.internal.util.InputPosition$.MODULE$.NONE());
        }
    }

    private Class<?> getRecordClass(Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) returnType;
            Type[] args = paramType.getActualTypeArguments();
            if (args.length > 0) {
                Type arg = args[0];
                if (arg instanceof Class) {
                    return (Class<?>) arg;
                } else if (arg instanceof ParameterizedType) {
                    return (Class<?>) ((ParameterizedType) arg).getRawType();
                }
            }
        }
        return method.getReturnType();
    }

    private org.neo4j.cypher.internal.frontend.phases.ProcedureAccessMode mapAccessMode(org.neo4j.procedure.Mode mode) {
        if (mode == null) {
            return org.neo4j.cypher.internal.frontend.phases.ProcedureReadOnlyAccess$.MODULE$;
        }
        switch (mode) {
            case READ:
                return org.neo4j.cypher.internal.frontend.phases.ProcedureReadOnlyAccess$.MODULE$;
            case WRITE:
                return org.neo4j.cypher.internal.frontend.phases.ProcedureReadWriteAccess$.MODULE$;
            case SCHEMA:
                return org.neo4j.cypher.internal.frontend.phases.ProcedureSchemaWriteAccess$.MODULE$;
            case DBMS:
                return org.neo4j.cypher.internal.frontend.phases.ProcedureDbmsAccess$.MODULE$;
            default:
                return org.neo4j.cypher.internal.frontend.phases.ProcedureReadOnlyAccess$.MODULE$;
        }
    }

    private static <T> scala.collection.immutable.IndexedSeq<T> toScalaIndexedSeq(java.util.List<T> javaList) {
        return scala.collection.immutable.Vector$.MODULE$.from(
                scala.jdk.javaapi.CollectionConverters.asScala(javaList)
        );
    }
}
