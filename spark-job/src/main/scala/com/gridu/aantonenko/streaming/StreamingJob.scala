package com.gridu.aantonenko.streaming

import java.sql.Timestamp

import com.datastax.driver.core.utils.UUIDs
import org.apache.spark.sql.{Dataset, Row, SaveMode, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.OutputMode
import org.apache.spark.sql.types.{LongType, StringType, StructField, StructType}
import org.slf4j.{Logger, LoggerFactory}
import scopt.OptionParser

object StreamingJob {

  val logger: Logger = LoggerFactory.getLogger(this.getClass.getSimpleName)

  final case class JobParams(
    kafkaHost: String = "localhost",
    redisHost: String = "localhost",
    cassandraHost: String = "localhost",
    redisTtlMins: Int = 10
  )

  final case class InputRecord(event_id: String, `type`: String, ip: String, event_time: Long, datetime: Timestamp, url: String)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def main(args: Array[String]): Unit = {
    argsParser.parse(args, JobParams()) match {
      case Some(input) =>
        runJob(input)
      case None =>
        throw new Exception("Can't parse input arguments")
    }
  }

  private def runJob(jobParams: JobParams): Unit = {
    val kafkaSchema = StructType(Seq(
      StructField("schema", StringType),
      StructField("payload", StringType)
    ))

    val inputSchema = StructType(Seq(
      StructField("type", StringType, nullable = false),
      StructField("ip", StringType, nullable = false),
      StructField("event_time", LongType, nullable = false),
      StructField("url", StringType, nullable = false)
    ))

    val kafkaHost = jobParams.kafkaHost
    val redisHost = jobParams.redisHost
    val cassandraHost = jobParams.cassandraHost

    logger.info(s"kafkaHost = $kafkaHost")
    logger.info(s"redisHost = $redisHost")
    logger.info(s"cassandraHost = $cassandraHost")

    implicit val spark: SparkSession = SparkSession.builder()
      .appName("BotDetectionStreamingJob")
      .config("spark.cassandra.connection.host", cassandraHost)
      .config("spark.cassandra.auth.username", "cassandra")
      .config("spark.cassandra.auth.password", "cassandra")
      .getOrCreate()

    logger.info("Spark session's created")

    import spark.implicits._
    spark.sparkContext.setLogLevel("ERROR")

    val uuid = udf(() => UUIDs.timeBased().toString)

    // read clickstream from kafka
    val clickstream = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", s"$kafkaHost:9092")
      .option("subscribe", "click-stream")
      .option("startingOffsets", "latest")
      .load()
      .select(from_json('value.cast("string"), kafkaSchema).as("data"))
      .select(from_json($"data.payload", inputSchema).as("payload"))
      .select("payload.*")
      .withColumn("datetime", to_timestamp(from_unixtime('event_time)))
      .withColumn("event_id", uuid())
      .select('event_id, 'type, 'ip, 'url, 'event_time, 'datetime).as[InputRecord]
      .withWatermark("datetime", "10 minutes")

    val clickstreamWithBots = clickstream
      .withColumn("combined", struct('event_id, 'type, 'ip, 'url, 'event_time, 'datetime))
      .groupBy('ip, window('datetime, "10 seconds", "1 seconds").as("window"))
      .agg(count("*").as("requestNum"), collect_list("combined").as("requests"))
      .select(explode('requests).as("request"), when('requestNum >= 20, true).otherwise(false).as("is_bot"))
      .select($"request.event_id", $"request.type", $"request.ip", $"request.url", $"request.event_time", $"request.datetime", 'is_bot)

    logger.info("Started processing click-stream...")

    val write = clickstreamWithBots
      .writeStream
      .outputMode(OutputMode.Update())
      .foreachBatch((batchDF: Dataset[Row], _: Long) => {
        batchDF.persist()
        writeToCassandra(batchDF)
        writeToRedis(redisHost, jobParams.redisTtlMins, batchDF)
        batchDF.unpersist()
      })
      .start

    write.awaitTermination()
  }

  private def writeToCassandra(batchDF: Dataset[Row])(implicit spark: SparkSession): Unit = {
    import org.apache.spark.sql.cassandra._
    import spark.implicits._

    val cassandra = batchDF
      .distinct()
      .select('event_id, 'ip, 'event_time, 'type, 'url, 'is_bot)
      .groupBy('event_id, 'ip, 'event_time, 'type, 'url)
      .agg(max('is_bot).as("is_bot"))

    val rowCount = cassandra.count()
    logger.info(s"$rowCount records to write to Cassandra")

    cassandra.write
      .cassandraFormat(table = "clickstream", keyspace = "capstone", cluster = "clickstream")
      .mode(SaveMode.Append)
      .option("spark.cassandra.output.consistency.level", "ONE")
      .save()
  }

  // TODO: events are coming unsorted, so stale events will be put to bots-cache not on the event_time, but on the processing time
  private def writeToRedis(redisHost: String, redisTtlMins: Int, batchDF: Dataset[Row])(implicit spark: SparkSession): Unit = {
    import spark.implicits._

    val redis = batchDF
      .filter('is_bot and 'datetime + expr(s"interval $redisTtlMins minutes") > current_timestamp())
      .select('ip, 'datetime)
      .groupBy('ip)
      .agg(max('datetime).as("last_bot_activity"))

    val rowCount = redis.count()
    logger.info(s"$rowCount records to write to Redis")


    redis.write
      .format("org.apache.spark.sql.redis")
      .option("host", redisHost)
      .option("port", "6379")
      .option("table", "bots")
      .option("key.column", "ip")
      .option("ttl", 60 * redisTtlMins)
      .mode(SaveMode.Append)
      .save()
  }

  def argsParser: OptionParser[JobParams] =
    new OptionParser[JobParams](JobParams.getClass.getSimpleName) {

      opt[String]("kafkaHost")
        .required()
        .text("Kafka host name from docker-compose.yml")
        .action((value, config) => config.copy(kafkaHost = value))

      opt[String]("redisHost")
        .required()
        .text("Redis host name from docker-compose.yml")
        .action((value, config) => config.copy(redisHost = value))

      opt[String]("cassandraHost")
        .required()
        .text("Cassandra host name from docker-compose.yml")
        .action((value, config) => config.copy(cassandraHost = value))

      opt[Int]("redisTtlMins")
        .optional()
        .text("For how many minutes bots must be stored in Redis. Default is 10")
        .action((value, config) => config.copy(redisTtlMins = value))
    }

}
