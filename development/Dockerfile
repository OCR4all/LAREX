# Base Image
FROM tomcat:9.0-jdk8

ENV LAREX_VERSION="dev"

# Enable Networking on port 8080 (Tomcat)
EXPOSE 8080

# Installing dependencies and deleting cache
RUN apt-get update && apt-get install -y \
    wget

# Download maven project
COPY Larex.war /usr/local/tomcat/webapps/Larex.war

# Create books path
RUN mkdir /home/books

# Copy larex.config
COPY dev.config /larex.config
ENV LAREX_CONFIG=/larex.config
