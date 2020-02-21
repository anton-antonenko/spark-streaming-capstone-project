package com.gridu.aantonenko.streaming.streaming

import com.holdenkarau.spark.testing.{DataFrameSuiteBase, SharedSparkContext, SparkSessionProvider}
import org.apache.spark.sql.SparkSession
import org.scalatest.{FlatSpec, Matchers}

class DataFrameSuite extends FlatSpec with Matchers with DataFrameSuiteBase with SharedSparkContext {

  override implicit protected def reuseContextIfPossible: Boolean = true

  override def beforeAll(): Unit = {
    super[SharedSparkContext].beforeAll()
    SparkSessionProvider._sparkSession = SparkSession.builder()
      .appName("tests")
      .master("local[*]")
      .getOrCreate()
  }
}
