FROM openjdk:11-jre-slim

RUN mkdir /rems
WORKDIR /rems

ENTRYPOINT ["java", "--illegal-access=deny", "-Drems.config=config/config.edn", "-jar", "rems.jar"]

COPY empty-config.edn /rems/config/config.edn
COPY target/uberjar/rems.jar /rems/rems.jar
