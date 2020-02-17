name := "spark-job"
organization := "com.gridu.aantonenko.streaming"
version := "1.0-SNAPSHOT"

scalaVersion := "2.11.12"

val sparkVersion = "2.4.1" // cannot use higher version because they need scala 2.12 and spark-redis doesn't support it yet
val scoptVersion = "3.7.0"
val sparkRedisVersion = "2.4.0"
val sparkCassandraVersion = "2.4.2"


libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % scoptVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion,
  "org.apache.spark" %% "spark-sql-kafka-0-10" % sparkVersion,
  "com.redislabs" % "spark-redis" % sparkRedisVersion,
  "com.datastax.spark" %% "spark-cassandra-connector" % sparkCassandraVersion
)

assemblyJarName in assembly := s"${name.value}-${version.value}.jar"
// remove scala version from artifacts name
crossPaths := false