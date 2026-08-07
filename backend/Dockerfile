# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies separately from source changes
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system --ingroup spring spring

# logback-spring.xml writes to logs/ relative to the working directory, and this
# process runs unprivileged - so the directory has to exist and be owned by that
# user before the switch. Creating it after USER is too late: /app still belongs
# to root, Logback cannot create /app/logs, and Spring Boot treats a logging
# configuration error as fatal - the JVM exits before the application context
# starts, so nothing ever answers /actuator/health.
RUN mkdir -p /app/logs && chown -R spring:spring /app

COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

USER spring:spring

EXPOSE 4001

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:4001/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
