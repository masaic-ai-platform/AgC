#!/bin/bash
echo "Starting Temporal Client..."

# Find the fat JAR file
JAR_FILE=$(find build/libs -name "*-all.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: Fat JAR not found. Please run './gradlew :temporal_client:fatJar' first."
    exit 1
fi

echo "Using JAR: $JAR_FILE"

# Run the JAR
java -jar "$JAR_FILE"
