package org.neo4j.cs.ast

import org.neo4j.cypher.internal.expressions.functions.Function
import java.io.File
import java.net.JarURLConnection
//import org.neo4j.cypher.internal.expressions.functions

object CypherFunctionRegistry {
  private val pkg = "org.neo4j.cypher.internal.expressions.functions"

  val allFunctions: Map[String, Function] = {
    val loader = Thread.currentThread().getContextClassLoader
    val path = pkg.replace('.', '/')
    val resources = loader.getResources(path)

    val functionObjects = scala.collection.mutable.ArrayBuffer.empty[Function]

    while (resources.hasMoreElements) {
      val url = resources.nextElement()
      if (url.getProtocol == "file") {
        scanDirectory(new File(url.getFile), functionObjects)
      } else if (url.getProtocol == "jar") {
        scanJar(url.openConnection().asInstanceOf[JarURLConnection], path, functionObjects)
      }
    }
    functionObjects.map(f => f.name -> f).toMap
  }

  private def scanDirectory(dir: File, acc: scala.collection.mutable.ArrayBuffer[Function]): Unit = {
    dir.listFiles().filter(_.getName.endsWith("$.class")).foreach { file =>
      loadIfFunction(file.getName.stripSuffix(".class"), acc)
    }
  }

  private def scanJar(conn: JarURLConnection, path: String, acc: scala.collection.mutable.ArrayBuffer[Function]): Unit = {
    val jar = conn.getJarFile
    val entries = jar.entries()
    while (entries.hasMoreElements) {
      val name = entries.nextElement().getName
      if (name.startsWith(path) && name.endsWith("$.class")) {
        val className = name.replace('/', '.').stripSuffix(".class")
        loadIfFunction(className, acc)
      }
    }
  }

  private def loadIfFunction(fullClassName: String, acc: scala.collection.mutable.ArrayBuffer[Function]): Unit = {
    try {
      val clazz = Class.forName(fullClassName)
      // Scala case objects have a static MODULE$ field
      val field = clazz.getDeclaredField("MODULE$")
      field.get(null) match {
        case f: Function => acc += f
        case _ =>
      }
    } catch { case _: Throwable => }
  }
}