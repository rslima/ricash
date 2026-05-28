# syntax=docker/dockerfile:1

# Multi-stage build for the Ricash Spring Boot API (Java 25 / Spring Boot 4.0.x).
# Replaces `mvn spring-boot:build-image`, which currently fails on Docker 29.x
# while uploading the application layers to the buildpack creator container.

############################
# Stage 1 — build the jar
############################
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the Maven wrapper and descriptor first so dependency resolution is cached
# as its own layer and only re-runs when pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Warm the local Maven repository. The BuildKit cache mount keeps ~/.m2 between
# builds without baking it into the image.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B dependency:go-offline

# Build the application (tests are skipped; run them separately in CI).
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B clean package -DskipTests

# Explode the repackaged jar into Spring Boot layers and give the thin
# application jar a stable name so the runtime stage doesn't depend on the
# project version.
RUN java -Djarmode=tools -jar target/*.jar \
        extract --layers --application-filename app.jar --destination extracted

############################
# Stage 2 — runtime image
############################
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Run as an unprivileged user.
RUN groupadd --system spring && useradd --system --gid spring --home /app spring

# Copy the layers most-stable-first so changes to application code don't
# invalidate the (large, slow-changing) dependency layer.
COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

USER spring:spring
EXPOSE 8080

# The thin app.jar declares Main-Class and a relative Class-Path (lib/*), so a
# plain `java -jar` is all that is needed. Pass JVM flags via JAVA_TOOL_OPTIONS
# (e.g. -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75").
ENTRYPOINT ["java", "-jar", "app.jar"]
