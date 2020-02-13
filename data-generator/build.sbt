name := "data-generator"
organization := "com.gridu.aantonenko.clickstream.generator"
version := "1.0-SNAPSHOT"

scalaVersion := "2.12.10"

val scoptVersion = "3.7.0"
val scalatestVersion = "3.0.5"

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % scoptVersion,
  "com.typesafe.akka" %% "akka-stream" % "2.6.3",
  "net.liftweb" %% "lift-json" % "3.4.1",
  "org.scalatest" %% "scalatest" % scalatestVersion % Test
)

// FOLLOWING SETTINGS ARE NEEDED FOR DEPLOYMENT A FAT JAR USING CI SCRIPTS FOR CONCOURSE
assemblyJarName in assembly := s"${name.value}-${version.value}.jar"

// remove scala version from artifacts name
crossPaths := false

// disable publishing default jars
publishArtifact in (Compile, packageBin) := false
publishArtifact in (Compile, packageDoc) := false
publishArtifact in (Compile, packageSrc) := false