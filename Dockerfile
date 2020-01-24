FROM openjdk:11-jre-slim

RUN mkdir /rems
WORKDIR /rems

ENTRYPOINT ["./entrypoint.sh"]

COPY empty-config.edn /rems/config/config.edn
COPY target/uberjar/rems.jar /rems/rems.jar
COPY entrypoint.sh /rems/entrypoint.sh
