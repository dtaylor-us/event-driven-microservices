# Build stage: from repo root so Gradle can see all modules
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle.kts ./
COPY events-schema events-schema
COPY audit-service audit-service

RUN ./gradlew :audit-service:bootJar --no-daemon -x test

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN adduser -D -u 1000 appuser
USER appuser

COPY --from=builder /build/audit-service/build/libs/audit-service-*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
