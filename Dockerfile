# Multi-stage build for smaller final image
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy JAR from builder stage
COPY --from=builder /app/target/discord-leetcode-bot-*.jar app.jar

# Expose port (optional - mainly for health checks if you add them)
EXPOSE 8080

# Set active profile via environment variable
ENV SPRING_PROFILES_ACTIVE=ec2

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
