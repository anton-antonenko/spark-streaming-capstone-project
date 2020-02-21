import java.io.File
import java.nio.file.Paths

import scala.util.matching.Regex

name := "spark-job"
organization := "com.gridu.aantonenko.streaming"
version := "1.0-SNAPSHOT"

scalaVersion := "2.11.12"

val sparkVersion = "2.4.1" // cannot use higher version because they need scala 2.12 and spark-redis doesn't support it yet
val scoptVersion = "3.7.0"
val sparkRedisVersion = "2.4.0"
val sparkCassandraVersion = "2.4.2"
val sparkTestVersion = s"${sparkVersion}_0.12.0"


libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % scoptVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion % Provided,
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,
  "com.redislabs" % "spark-redis" % sparkRedisVersion,
  "com.datastax.spark" %% "spark-cassandra-connector" % sparkCassandraVersion,
  "com.holdenkarau" %% "spark-testing-base" % sparkTestVersion % Test
)

// add wartremover checks
wartremoverErrors in (Compile, compile) ++= Warts.allBut(Wart.StringPlusAny, Wart.DefaultArguments, Wart.NonUnitStatements)

// add scalastyle check on Compile
lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")
scalastyleFailOnError := true
scalastyleSources := allBut("Test.scala".r)
compileScalastyle := scalastyle.in(Compile).toTask("").value
(compile in Compile) := ((compile in Compile) dependsOn compileScalastyle).value

// set up assembly
assemblyJarName in assembly := s"${name.value}-${version.value}.jar"
assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("com.google.common.**" -> "shaded.google.common.@1").inAll
)

// remove scala version from artifacts name
crossPaths := false


def allBut(excludeRegex: Regex): Seq[File] = {
  val sourcesRoot = Paths.get("./src/main/scala").toFile

  def recursiveListFiles(f: File): Seq[File] = {
    val these = f.listFiles.toList
    val good = these.filterNot(f => excludeRegex.findFirstIn(f.getName).isDefined).filterNot(_.isDirectory)
    good ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
  }

  recursiveListFiles(sourcesRoot)
}