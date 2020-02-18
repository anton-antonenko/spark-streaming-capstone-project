#!/bin/bash

DEMO_HOME=$(pwd)

# start all containers
docker-compose up

# create a Kafka topic and Kafka Connector
# find containerId with KafkaConnect cluster
KAFKA_CONTAINER_ID=$(docker ps -aqf "name=streaming-capstone-kafka")
docker exec -it "$KAFKA_CONTAINER_ID" bash
  # go to dir with conenctor configs
  cd /files/kafka_connect
  # create a topic which we're gonna write to with 8 partitions (or if you computer has a different number of cores - use this number)
  kafka-topics --create --topic click-stream --partitions 8 --replication-factor 1 --zookeeper 127.0.0.1:2181
  # create a connector
  curl -X POST -H "Content-Type: application/json" --data @file_connector_conf.json http://localhost:8083/connectors
  # shut down the terminal
  # open in a browser http://localhost:3030/kafka-connect-ui/#/cluster/fast-data-dev and make sure the connector has been created

# create Cassandra keyspace and table
# find containerId with Cassandra cluster
CASSANDRA_CONTAINER_ID=$(docker ps -aqf "name=streaming-capstone-cassandra")
docker exec -it "$CASSANDRA_CONTAINER_ID" bash
  # create a keyspace
  cqlsh -u cassandra -p cassandra -e "CREATE KEYSPACE capstone WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1};"
  cqlsh -u cassandra -p cassandra -e "CREATE TABLE capstone.clickstream(ip text, event_time bigint, event_id uuid, type text, url text, is_bot boolean, PRIMARY KEY (ip, event_time, event_id)) WITH CLUSTERING ORDER BY (event_time ASC);"


# start the data generator
# it's supposed that it's already built. if not, go to the `./data-generator` and run `sbt assembly`
java -jar ./data-generator/target/data-generator-1.0-SNAPSHOT.jar --usersNum=100 --eventsPerSecond=3000

# start the Spark job
# the same - if it's not built yet, go to the `./spark-job` and run `sbt assembly`
cp ./spark-job/target/spark-job-1.0-SNAPSHOT.jar ./files/spark-job
SPARK_CONTAINER_ID=$(docker ps -aqf "name=streaming-capstone-spark-master")
docker exec -it "$SPARK_CONTAINER_ID" bash
  /spark/bin/spark-submit \
   --class com.gridu.aantonenko.streaming.StreamingJob \
   --master spark://"$(hostname)":7077 \
   /spark-job/spark-job-1.0-SNAPSHOT.jar \
    --kafkaHost=streaming-capstone-kafka \
    --redisHost=streaming-capstone-redis \
    --cassandraHost=streaming-capstone-cassandra
