# ---- Stage 1: build the jar with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies first, in their own cached layer
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests need Docker (Testcontainers), so they run outside the image build
RUN mvn -B -q package -DskipTests

# ---- Stage 2: runtime image with only the JRE and the jar ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 spring
COPY --from=build /build/target/order-service.jar app.jar
USER spring

EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
