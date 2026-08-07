# Build context is this directory (api/), not the repository root.
# Render: set the root directory to `api`. Railway: set the service root directory to `api`.

FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp -DskipTests package


FROM eclipse-temurin:25-jre

WORKDIR /app

# The platform injects PORT; application.yaml already reads it, defaulting to 8080.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

COPY --from=builder /build/target/*.jar app.jar

RUN useradd --system --create-home app && chown app:app app.jar
USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
