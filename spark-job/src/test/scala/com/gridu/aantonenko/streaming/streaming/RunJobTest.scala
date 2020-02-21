package com.gridu.aantonenko.streaming.streaming

import com.gridu.aantonenko.streaming.StreamingJob

class RunJobTest extends DataFrameSuite {

  "Job" should "run" in {
    StreamingJob.main(Array(
      "--kafkaHost=localhost",
      "--redisHost=localhost",
      "--cassandraHost=localhost"
    ))
  }

}
