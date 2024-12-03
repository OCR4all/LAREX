# Base Image
FROM tomcat:11-jdk21

ENV LAREX_VERSION="0.7.4"

# Enable Networking on port 8080 (Tomcat)
EXPOSE 8080

# Install dependencies
RUN apt-get update && apt-get install -y \
    wget

# Deploy Larex
COPY Larex.war /usr/local/tomcat/webapps/Larex.war

# Create directories for books and savedir
RUN mkdir /home/books /home/savedir

# Copy larex.properties
COPY dev.properties /larex.properties
ENV LAREX_CONFIG=/larex.properties

# Default working directory
WORKDIR /usr/local/tomcat
