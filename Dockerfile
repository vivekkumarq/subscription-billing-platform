# syntax=docker/dockerfile:1

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy the descriptor first so the dependency layer is cached until pom.xml actually changes.
# Best effort: if a plugin cannot be resolved offline, the package step below still fetches it.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
RUN mvn -B -DskipTests package && \
    mv target/*.jar target/app.jar

# ---------- runtime ----------
FROM eclipse-temurin:17-jre-alpine AS runtime

# curl is used by the container healthcheck below.
RUN apk add --no-cache curl && \
    addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/app.jar /app/app.jar

USER app

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0" \
    SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
