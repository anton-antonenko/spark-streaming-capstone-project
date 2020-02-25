#!/bin/bash

echo "Application:            ${SPARK_APPLICATION_JAR_LOCATION}"
echo "Main class:             ${SPARK_APPLICATION_MAIN_CLASS}"
echo "Spark master:           ${SPARK_MASTER_URL}"
echo "Spark params:           ${SPARK_PARAMS}"
echo "Application arguments:  ${SPARK_APPLICATION_ARGS}"

spark-submit --class ${SPARK_APPLICATION_MAIN_CLASS} \
             --master ${SPARK_MASTER_URL} \
             ${SPARK_PARAMS} \
             ${SPARK_APPLICATION_JAR_LOCATION} ${SPARK_APPLICATION_ARGS}
