name := "data-generator"
organization := "com.gridu.aantonenko.clickstream.generator"
version := "1.0-SNAPSHOT"

scalaVersion := "2.12.10"

val scoptVersion = "3.7.0"
val akkaStreamsVersion = "2.6.3"
val liftJsonVersion = "3.4.1"

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % scoptVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaStreamsVersion,
  "net.liftweb" %% "lift-json" % liftJsonVersion
)


// add wartremover checks
wartremoverErrors in (Compile, compile) ++= Warts.allBut(Wart.StringPlusAny, Wart.Any, Wart.DefaultArguments, Wart.NonUnitStatements)

// add scalastyle check on Compile
lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")
scalastyleFailOnError := true
compileScalastyle := scalastyle.in(Compile).toTask("").value
(compile in Compile) := ((compile in Compile) dependsOn compileScalastyle).value

// set jar name for assembly
assemblyJarName in assembly := s"${name.value}-${version.value}.jar"

// remove scala version from artifacts name
crossPaths := false