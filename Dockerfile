FROM openjdk:11-jre-slim

RUN mkdir /rems
WORKDIR /rems

ENTRYPOINT ["./docker-entrypoint.sh"]

COPY empty-config.edn /rems/config/config.edn
COPY docker-entrypoint.sh /rems/docker-entrypoint.sh
