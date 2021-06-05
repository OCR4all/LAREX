#!/bin/sh
####################################################################
######## Build and run a development Docker image for Larex ########
####################################################################

## Container name (must be lower case)
DOCKER_NAME="larex_dev"

## Image name
IMAGE_NAME="larex_dev"

## Local path to the directory containing the books (must be changed to the actual book directory location!)
BOOK_DIR="/Path/to/books"

## Local path to the directory containing the books (must be changed to the actual book directory location!)
## Ignored if empty!
SAVE_DIR=""

## Local path to the custom configuration file
CONFIG_FILE="${PWD}/dev.config"

## Port
PORT=5555

### Build LAREX
mvn clean install -f ../pom.xml
cp ../target/Larex.war .

## Stop and delete old container (if exists)
OLD="$(docker ps --all --quiet --filter=name="$DOCKER_NAME")"
if [[ -n "$OLD" ]]; then
  docker stop ${OLD} && docker rm ${OLD}
fi

## Build and start container
docker build -t ${IMAGE_NAME} . && \
if [ -z "$SAVE_DIR" ]
then
      docker run \
        -p ${PORT}:8080 \
        --name ${DOCKER_NAME} \
        -v ${CONFIG_FILE}:/larex.config \
        -v ${BOOK_DIR}:/home/books/ \
        -it ${IMAGE_NAME}

else
      docker run \
        -p ${PORT}:8080 \
        --name ${DOCKER_NAME} \
        -v ${CONFIG_FILE}:/larex.config \
        -v ${BOOK_DIR}:/home/books/ \
        -v ${SAVE_DIR}:/home/savedir \
        -it ${IMAGE_NAME}
fi
