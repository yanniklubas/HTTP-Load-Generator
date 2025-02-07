FROM openjdk:slim

RUN mkdir -p /opt/httploadgenerator
COPY target/httploadgenerator.jar /opt/httploadgenerator/

ENV RUNMODE loadgenerator

EXPOSE 24225

CMD java -jar /opt/httploadgenerator/httploadgenerator.jar loadgenerator

