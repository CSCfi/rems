# For documentation see docs/installing-upgrading.md

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache bash

RUN mkdir /rems
WORKDIR /rems

ENTRYPOINT ["bash","./docker-entrypoint.sh"]

COPY empty-config.edn /rems/config/config.edn
COPY target/uberjar/rems.jar /rems/rems.jar
COPY docker-entrypoint.sh /rems/docker-entrypoint.sh

RUN chmod 664 /opt/java/openjdk/lib/security/cacerts
