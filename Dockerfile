# Production Dockerfile for LAREX
# Multi-stage build for optimized production image
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

# Copy Gradle wrapper and build files
COPY gradlew .
COPY gradle gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Copy source code
COPY src src/

# Make gradlew executable
RUN chmod +x gradlew

# Build the application
RUN ./gradlew bootJar --no-daemon

# Production stage
FROM eclipse-temurin:21-jre

# Add labels
LABEL maintainer="OCR4all"
LABEL description="LAREX - Layout Analysis on Early Printed Books"
LABEL version="0.8-SNAPSHOT"

# Create non-root user
RUN groupadd -r larex && useradd -r -g larex larex

# Set working directory
WORKDIR /app

# Copy the built JAR from builder
COPY --from=builder /build/build/libs/Larex.jar /app/Larex.jar

# Create directories for books and savedir
RUN mkdir -p /home/books /home/savedir && \
    chown -R larex:larex /app /home/books /home/savedir

# Switch to non-root user
USER larex

# Expose port
EXPOSE 8080

# Set environment variables
ENV LAREX_CONFIG=/config/larex.yml \
    JAVA_OPTS="-Xmx2048m -XX:+UseG1GC"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/Larex.jar"]
