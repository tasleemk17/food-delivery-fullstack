# ---- Stage 1: build the jar with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Download dependencies first, in their own cached layer. This step re-runs
# only when pom.xml changes, not on every code change.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests are run separately (IntelliJ or `mvnw test`), not inside the image build.
RUN mvn -B -q package -DskipTests

# ---- Stage 2: small runtime image with only the JRE and the jar ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 spring
COPY --from=build /build/target/gateway.jar app.jar
USER spring

EXPOSE 8080
# Use at most 75% of the container's memory limit (set in docker-compose.yml)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
