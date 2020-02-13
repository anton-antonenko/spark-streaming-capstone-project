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
  # create the topic we're gonna write to with 3 partitions
  kafka-topics --create --topic click-stream --partitions 3 --replication-factor 1 --zookeeper 127.0.0.1:2181
  # create a connector
  curl -X POST -H "Content-Type: application/json" --data @file_connector_conf.json http://localhost:8083/connectors
  # shut down the terminal
  # open in browser http://localhost:3030/kafka-connect-ui/#/cluster/fast-data-dev and make sure the connector has been created

# start data generator
java -jar ./data-generator/target/data-generator-1.0-SNAPSHOT.jar --usersNum=100 --eventsPerSecond=3000

# start Spark job
java -jar ./spark-job/target/spark-job-1.0-SNAPSHOT.jar
