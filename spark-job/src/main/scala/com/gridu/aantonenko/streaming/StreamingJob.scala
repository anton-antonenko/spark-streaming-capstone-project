package com.gridu.aantonenko.streaming

import java.sql.Timestamp

import org.apache.spark.sql.{Dataset, Row, SaveMode, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.OutputMode
import org.apache.spark.sql.types.{LongType, StringType, StructField, StructType}


object StreamingJob {

  case class InputRecord(`type`: String, ip: String, event_time: Long, datetime: Timestamp, url: String)

  def main(args: Array[String]): Unit = {

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

    val spark = SparkSession.builder()
      .appName("BotDetectionStreamingJob")
      .master("local")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    val redisTtlMins = 10

    // read clickstream from kafka
    val clickstream = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("subscribe", "click-stream")
      .option("startingOffsets", "earliest")
      .load()
      .select(from_json('value.cast("string"), kafkaSchema).as("data"))
      .select(from_json($"data.payload", inputSchema).as("payload"))
      .select("payload.*")
      .withColumn("datetime", to_timestamp(from_unixtime('event_time)))
      .select('type, 'ip, 'url, 'event_time, 'datetime).as[InputRecord]
      .withWatermark("datetime", "10 minutes")


    // detect bots
    val clickstreamWithBots = clickstream
      .withColumn("combined", struct('type, 'ip, 'url, 'event_time, 'datetime))
      .groupBy('ip, window('datetime, "10 seconds", "1 seconds").as("window"))
      .agg(count("*").as("requestNum"), collect_list("combined").as("requests"))
      .select(explode('requests).as("request"), 'window, 'requestNum)
      .withColumn("is_bot", when('requestNum >= 20, true).otherwise(false))
      .select($"request.type", $"request.ip", $"request.url", $"request.event_time", $"request.datetime", 'window, 'is_bot)
      .drop("window")


    // Write to console
    clickstreamWithBots
      .writeStream
      .format("console")
      .outputMode(OutputMode.Append())
      .option("truncate", "false")
      .start()
      .awaitTermination()

    // TODO: write to cassandra. can be done separately


    // Write results to redis
    val writeToRedisQuery = clickstreamWithBots
        .writeStream
        .outputMode(OutputMode.Update())
        .foreachBatch((batchDF: Dataset[Row], _: Long) => {
          // write bots to Redis
          val redis = batchDF
            .filter('is_bot and 'datetime + expr(s"interval $redisTtlMins minutes") > current_timestamp())
            .select('ip, 'datetime)
            .groupBy('ip)
            .agg(max('datetime).as("last_bot_activity"))

          redis.show(false)

          redis.write
            .format("org.apache.spark.sql.redis")
            .option("host", "localhost")
            .option("port", "6379")
            .option("table", "bots")
            .option("key.column", "ip")
            .option("ttl", 60 * redisTtlMins)
            .mode(SaveMode.Append)
            .save()

        })
      .start()

    writeToRedisQuery.awaitTermination()
  }
}
