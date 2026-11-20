FROM gradle:jdk25-noble
LABEL description="Development environment for Spring Boot application with hot reload"

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends libvips42t64 \
    && rm -rf /var/lib/apt/lists/*

# Copy build configuration
COPY build.gradle.kts settings.gradle.kts ./

# Download dependencies
RUN gradle dependencies --no-daemon --quiet

# Copy source code (will be overridden by volume mount in development)
COPY src ./src

# Expose ports
EXPOSE 8080 5005

# Enable continuous compilation and hot reload
CMD ["gradle", "bootRun", "--no-daemon", "--continuous", "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"]
