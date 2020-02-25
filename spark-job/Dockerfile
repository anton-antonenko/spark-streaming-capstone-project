FROM bde2020/spark-base:2.4.1-hadoop2.7

MAINTAINER Anton Antonenko <kingtoxa@gmail.com>

WORKDIR /app

# copy jar
COPY ./target/spark-job-1.0-SNAPSHOT.jar /app/
COPY ./submit-job.sh /

RUN chmod +x /submit-job.sh

ENV ENABLE_INIT_DAEMON false
ENV SPARK_MASTER_NAME spark-master
ENV SPARK_MASTER_PORT 7077
ENV SPARK_MASTER_URL spark://${SPARK_MASTER_NAME}:${SPARK_MASTER_PORT}
ENV SPARK_HOME /spark
ENV PATH "$SPARK_HOME/bin:${PATH}"
ENV SPARK_APPLICATION_JAR_LOCATION /app/spark-job-1.0-SNAPSHOT.jar
ENV SPARK_APPLICATION_MAIN_CLASS com.gridu.aantonenko.streaming.StreamingJob
ENV SPARK_APPLICATION_ARGS "--kafkaHost=streaming-capstone-kafka --redisHost=streaming-capstone-redis --cassandraHost=streaming-capstone-cassandra"
ENV SPARK_PARAMS "--driver-memory 1g --executor-memory 1g"

EXPOSE 4040/tcp

CMD ["/bin/bash", "/submit-job.sh"]