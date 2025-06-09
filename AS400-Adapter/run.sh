#!/usr/bin/env bash
if [ -z ${KAFKA_CONFIG_FILE} ]; then echo "KAFKA_CONFIG_FILE env var unset"; else echo "Kafka config path '$KAFKA_CONFIG_FILE'"; fi

if [[ -z $KAFKA_CONFIG_FILE ]]; then
    echo "one or more variables are undefined - Exiting"
    exit 1
else
    if test ! -f "$KAFKA_CONFIG_FILE"; then
        echo "Kafka config file path [$KAFKA_CONFIG_FILE] does not exist or is an invalid path - Exiting"
        exit 1
    fi

    java -jar /jade-adapter.jar $KAFKA_CONFIG_FILE
fi