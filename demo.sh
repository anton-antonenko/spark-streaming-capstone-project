#!/bin/bash

# start all containers (BE CAREFUL - IT NEEDS A LOT OF MEMORY (5-6G))
docker-compose up -d

# to start all containers except the spark cluster use this:
docker-compose up -d kafka-cluster cassandra redis

# create a Kafka topic and Kafka Connector
KAFKA_CONTAINER_ID=$(docker ps -aqf "name=streaming-capstone-kafka")
# create a topic which we're gonna write to with 8 partitions (or if you computer has a different number of cores - use this number)
docker exec -it "$KAFKA_CONTAINER_ID" kafka-topics --create --topic click-stream --partitions 8 --replication-factor 1 --zookeeper 127.0.0.1:2181
# create a connector
docker exec -w /files/kafka_connect -it "$KAFKA_CONTAINER_ID" curl -X POST -H "Content-Type: application/json" --data @file_connector_conf.json http://localhost:8083/connectors
# open in a browser http://localhost:3030/kafka-connect-ui/#/cluster/fast-data-dev and make sure the connector has been created

# create Cassandra keyspace and table
CASSANDRA_CONTAINER_ID=$(docker ps -aqf "name=streaming-capstone-cassandra")
# create keyspace and table
docker exec -it "$CASSANDRA_CONTAINER_ID" cqlsh -u cassandra -p cassandra -e \
  "CREATE KEYSPACE capstone WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1};
  CREATE TABLE capstone.clickstream(ip text, event_time bigint, event_id uuid, type text, url text, is_bot boolean, PRIMARY KEY (ip, event_time, event_id)) WITH CLUSTERING ORDER BY (event_time ASC);"

# start the spark job
# it's supposed that it's already built. if not, run `./spark-job/build.sh`
# build the Spark job image
docker build --rm=true -t antonantonenko/spark-job ./spark-job

# to run the job in the Docker spark cluster use this:
docker run --name spark-job -h spark-job --rm -p 4040:4040/tcp --net=streamming-final-project_common antonantonenko/spark-job
# to run the job in the Docker local mode use this:
docker run --name spark-job -h spark-job --rm -p 4040:4040/tcp -e SPARK_MASTER_URL="local[*]" \
  -e SPARK_PARAMS='--driver-memory 2g' \
  --net=streamming-final-project_common antonantonenko/spark-job
# to run the job localy on your pc use this:
spark-submit --class com.gridu.aantonenko.streaming.StreamingJob \
             --master local[*] \
             --driver-memory 2g \
             ./spark-job/target/spark-job-1.0-SNAPSHOT.jar --kafkaHost=localhost --redisHost=localhost --cassandraHost=localhost

# start the data generator
# it's supposed that it's already built. if not, run `./data-generator/build.sh`
java -jar ./data-generator/target/data-generator-1.0-SNAPSHOT.jar --usersNum=100 --eventsPerSecond=3000