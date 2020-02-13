#!/bin/bash

DEMO_HOME=$(pwd)

# start all containers
cd "$DEMO_HOME/docker-compose"
docker-compose up

# (in another terminal tab) start a hosted tools, mapped on our code
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

# (in another terminal tab) start data generator
cd "$DEMO_HOME"
java -jar ./data-generator/target/data-generator-1.0-SNAPSHOT.jar --usersNum=100 --eventsPerSecond=3000

# (in another terminal tab) start Spark job
cd "$DEMO_HOME"
java -jar ./spark-job/target/spark-job-1.0-SNAPSHOT.jar
