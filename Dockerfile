# Use the official Gradle image with JDK 21 installed
FROM gradle:8.11.1-jdk21-alpine AS build

# Set work directory
WORKDIR /home/gradle/src

# Copy your source code into the container
COPY . /home/gradle/src

# Build the project using Gradle
RUN gradle clean build -x test --no-daemon

# For the runtime stage use the official OpenJDK 21 image from Docker Hub
FROM alpine/java:22.0.2-jdk

ARG GIT_SHA=dev
ARG BUILD_NUMBER=local
ARG BUILD_TIME=
ENV GIT_SHA=$GIT_SHA \
    BUILD_NUMBER=$BUILD_NUMBER \
    BUILD_TIME=$BUILD_TIME

# Expose port 8080 for your application
EXPOSE 8081

# Set work directory
WORKDIR /app

# Copy the built jar file from the build stage into this new container
COPY --from=build /home/gradle/src/service/build/libs/*.jar ./app.jar

ENTRYPOINT exec java -jar -Dspring.profiles.active=$ACTIVE_PROFILES ./app.jar
