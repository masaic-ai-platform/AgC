#!/bin/bash

# Temporal Client Runner Script
# Usage: ./run.sh [temporal_target] [namespace] [api_key]

TEMPORAL_TARGET=${1:-"us-east-1.aws.api.temporal.io:7233"}
TEMPORAL_NAMESPACE=${2:-"your-namespace.your-account-id"}
TEMPORAL_API_KEY=${3:-"your-api-key"}

echo "Starting Temporal Client..."
echo "Target: $TEMPORAL_TARGET"
echo "Namespace: $TEMPORAL_NAMESPACE"
echo "API Key: ${TEMPORAL_API_KEY:0:10}..."

# Find the fat JAR file
JAR_FILE=$(find build/libs -name "*-all.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: Fat JAR not found. Please run './gradlew :temporal_client:fatJar' first."
    exit 1
fi

echo "Using JAR: $JAR_FILE"

export TEMPORAL_TARGET
export TEMPORAL_NAMESPACE
export TEMPORAL_API_KEY

java -jar "$JAR_FILE"
