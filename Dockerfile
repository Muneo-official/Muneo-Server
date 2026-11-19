# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x ./gradlew

# Cache Gradle dependencies/plugins layer first.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar -x test

RUN JAR_FILE="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "${JAR_FILE}" \
    && cp "${JAR_FILE}" /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /workspace/app.jar /app/app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-XX:-UseBASE64Intrinsics", "-jar", "/app/app.jar"]
