# Build stage: from repo root so Gradle can see all modules
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy Gradle wrapper and root build files
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle.kts ./
# Copy all module sources (ingest + events-schema)
COPY events-schema events-schema
COPY event-ingest-service event-ingest-service

# Build the bootJar (no tests for faster image build)
RUN ./gradlew :event-ingest-service:bootJar --no-daemon -x test

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN adduser -D -u 1000 appuser
USER appuser

COPY --from=builder /build/event-ingest-service/build/libs/event-ingest-service-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
