# For documentation see docs/installing-upgrading.md

FROM openjdk:11-jre-slim

RUN mkdir /rems
WORKDIR /rems

ENTRYPOINT ["./docker-entrypoint.sh"]

COPY empty-config.edn /rems/config/config.edn
COPY target/uberjar/rems.jar /rems/rems.jar
COPY docker-entrypoint.sh /rems/docker-entrypoint.sh

RUN chmod 664 /usr/local/openjdk-11/lib/security/cacerts
