# Build stage: from repo root so Gradle can see all modules
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle.kts ./
COPY events-schema events-schema
COPY pricing-consumer-service pricing-consumer-service

RUN ./gradlew :pricing-consumer-service:bootJar --no-daemon -x test

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN adduser -D -u 1000 appuser
USER appuser

COPY --from=builder /build/pricing-consumer-service/build/libs/pricing-consumer-service-*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
