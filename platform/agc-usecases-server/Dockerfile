# Stage 1: Build your app image based on amazoncorretto
FROM bellsoft/liberica-openjre-alpine-musl:21

RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

# Copy the built JAR and configuration file into the image
COPY build/libs/agc-usecases-server-0.5.3-uc.jar /app/agc-usecases-server-0.5.3-uc.jar

EXPOSE 6644
# Start the Java application directly
ENTRYPOINT ["java", "-jar", "/app/agc-usecases-server-0.5.3-uc.jar"]
