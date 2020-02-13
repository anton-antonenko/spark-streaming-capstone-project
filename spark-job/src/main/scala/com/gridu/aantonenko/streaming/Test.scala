package com.gridu.aantonenko.streaming

import java.sql.Timestamp

import com.gridu.aantonenko.streaming.StreamingJob.InputRecord
import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.LongType

// Implementation of bot detection on static data
// THIS APPROACH DOESN'T WORK WITH STREAMING :(((
object Test {

  case class Bot(bot_ip: String, banned_since: Timestamp, banned_due: Timestamp)

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Test")
      .master("local")
      .getOrCreate()

    spark.sparkContext.setLogLevel("ERROR")
    import spark.implicits._

    val clickstream: Dataset[InputRecord] = spark.createDataset(data =
      (1 to 1 map (_ => InputRecord("click", "1.1.1.1", 1581432240, new Timestamp(1581432240000l), "url"))) ++ //"2020-02-11 16:44:00"
      (1 to 1 map (_ => InputRecord("click", "1.1.1.1", 1581432241, new Timestamp(1581432241000l), "url"))) ++ //"2020-02-11 16:44:01"
      (1 to 5 map (_ => InputRecord("click", "1.1.1.1", 1581432242, new Timestamp(1581432242000l), "url"))) ++ //"2020-02-11 16:44:02"
      (1 to 5 map (_ => InputRecord("click", "1.1.1.1", 1581432243, new Timestamp(1581432243000l), "url"))) ++ //"2020-02-11 16:44:03"
      (1 to 5 map (_ => InputRecord("click", "1.1.1.1", 1581432244, new Timestamp(1581432244000l), "url"))) ++ //"2020-02-11 16:44:04"
      (1 to 5 map (_ => InputRecord("click", "1.1.1.1", 1581432245, new Timestamp(1581432245000l), "url"))) ++ //"2020-02-11 16:44:05"
      (0 to 14 map (i => InputRecord("click", "1.1.1.1", 1581432246 + i, new Timestamp(1581432246000l + i * 1000), "url"))) ++ //"2020-02-11 16:44:06-23"
      (1 to 5 map (_ => InputRecord("click", "1.1.1.1", 1581432264, new Timestamp(1581432264000l), "url"))) ++ //"2020-02-11 16:44:24"
      (1 to 5 map (_ => InputRecord("click", "1.1.1.1", 1581432265, new Timestamp(1581432265000l), "url"))) ++ //"2020-02-11 16:44:24"
      (1 to 5 map (_ => InputRecord("click", "1.1.1.1", 1581432266, new Timestamp(1581432266000l), "url"))) ++ //"2020-02-11 16:44:24"
      (1 to 5 map (_ => InputRecord("click", "1.1.1.1", 1581432267, new Timestamp(1581432267000l), "url"))) ++ //"2020-02-11 16:44:24"
      (0 to 12 map (i => InputRecord("click", "1.1.1.1", 1581432271 + i, new Timestamp(1581432271000l + i * 1000), "url")))
    )

    clickstream.show(70, false)
    println("clickstream: " + clickstream.count())



    val ipWindow = Window.partitionBy("ip").orderBy("event_time")
    val ipSessionWindow = Window.partitionBy("ip", "session")
    val newBotSession = ('is_bot_activity and not(lag("is_bot_activity", 1).over(ipWindow))).cast("bigint")

    val botBlockingTimeInSeconds = 5
    val bots = clickstream
      .withColumn("diff_in_seconds", 'datetime.cast(LongType) - lag("datetime", 19).over(ipWindow).cast(LongType))
      .withColumn("is_bot_activity", when('diff_in_seconds <= 10, true).otherwise(false))
      .withColumn("session", sum(newBotSession).over(ipWindow))
      .withColumn("last_bot_activity", max(when('is_bot_activity, 'datetime)).over(ipSessionWindow))
      .withColumn("is_bot", when('datetime.cast(LongType) - 'last_bot_activity.cast(LongType) <= botBlockingTimeInSeconds, true).otherwise(false))
      .drop("diff_in_seconds", "is_bot_activity", "session", "last_bot_activity")


    bots.show(70,false)
    println("bots: " + bots.count())

  }


}
