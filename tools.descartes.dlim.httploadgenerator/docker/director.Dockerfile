FROM openjdk:slim

RUN mkdir -p /opt/httploadgenerator
COPY target/httploadgenerator.jar /opt/httploadgenerator/

EXPOSE 8080

WORKDIR /loadgenerator

