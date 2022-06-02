# Base Image
FROM tomcat:9.0-jdk8

ENV LAREX_VERSION="0.7.4"

# Enable Networking on port 8080 (Tomcat)
EXPOSE 8080

# Installing dependencies and deleting cache
RUN apt-get update && apt-get install -y \
    wget

# Download maven project
COPY Larex.war /usr/local/tomcat/webapps/Larex.war

# Create books and savedir path
RUN mkdir /home/books /home/savedir

# Copy larex.properties
COPY dev.properties /larex.properties
ENV LAREX_CONFIG=/larex.properties
