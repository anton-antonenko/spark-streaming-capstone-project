#!/bin/bash

DEMO_HOME=$(pwd)

# start all containers
docker-compose up

# create Kafka topic and Kafka Connector
# find containerId with KafkaConnect cluster
docker ps
docker exec -it <containerId> bash
  # go to dir with conenctor configs
  cd /files/kafka_connect
  # create a topic which we're gonna write to with 8 partitions (or if you computer has a different number of cores - use this number)
  kafka-topics --create --topic click-stream --partitions 8 --replication-factor 1 --zookeeper 127.0.0.1:2181
  # create a connector
  curl -X POST -H "Content-Type: application/json" --data @file_connector_conf.json http://localhost:8083/connectors
  # shut down the terminal
  # open in a browser http://localhost:3030/kafka-connect-ui/#/cluster/fast-data-dev and make sure the connector has been created

# start the data generator
# it's supposed that it's already built. if not, go to the `./data-generator` and run `sbt assembly`
java -jar ./data-generator/target/data-generator-1.0-SNAPSHOT.jar --usersNum=100 --eventsPerSecond=3000

# start the Spark job
# the same - if it's not built yet, go to the `./spark-job` and run `sbt assembly`
java -jar ./spark-job/target/spark-job-1.0-SNAPSHOT.jar
