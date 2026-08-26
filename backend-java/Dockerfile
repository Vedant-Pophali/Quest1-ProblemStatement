# Stage 1: Build the Java Fat JAR using Maven
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY backend-java/pom.xml .
COPY backend-java/src ./src
# This builds the single jar with all dependencies included
RUN mvn clean compile assembly:single -DskipTests

# Stage 2: Create the clean Production Image
FROM eclipse-temurin:21-jre
WORKDIR /app

# Install FFmpeg, Python, and yt-dlp at the OS Level
RUN apt-get update && apt-get install -y ffmpeg python3 python3-pip curl && \
    curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp && \
    chmod a+rx /usr/local/bin/yt-dlp && \
    apt-get clean

# Copy the compiled JAR from Stage 1
COPY --from=builder /app/target/*-jar-with-dependencies.jar app.jar

# Copy the frontend folder so Javalin can serve your UI
COPY frontend ./frontend

EXPOSE 7070
CMD ["java", "-jar", "app.jar"]