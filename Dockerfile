# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies separately to leverage Docker cache
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Create a non-root user to run the application
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
# Set environment variables
ENV JAVA_OPTS="-Xms512m -Xmx1024m"
# Expose the application port
EXPOSE 8080
# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
