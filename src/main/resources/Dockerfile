FROM openjdk:8-alpine

WORKDIR /data

COPY mqtt.jsap /data/mqtt.jsap
COPY host.jsap /data/host.jsap

COPY arces.crt /data/arces.crt
COPY client_swamp.crt /data/client_swamp.crt
COPY swamp.crt /data/swamp.crt

COPY SwampMapper-0-SNAPSHOT-jar-with-dependencies.jar /data/agent.jar

ENTRYPOINT ["java","-jar","agent.jar"]

