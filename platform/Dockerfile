# Stage 1: Build your app image based on amazoncorretto
FROM bellsoft/liberica-openjre-alpine-musl:21

# Accept version as build argument
ARG BUILD_VERSION=0.8.1

RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

# Copy the built JAR with a fixed name
COPY open-responses-server/build/libs/openresponses-${BUILD_VERSION}.jar /app/openresponses.jar

EXPOSE 6644
# Start the Java application directly
ENTRYPOINT ["java", "-jar", "/app/openresponses.jar"]
