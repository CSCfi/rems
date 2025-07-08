# For documentation see docs/installing-upgrading.md

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache bash

RUN mkdir -p /rems/keys

WORKDIR /rems

ENTRYPOINT ["bash","./docker-entrypoint.sh"]

ADD https://github.com/CSCfi/rems/releases/download/v2.38.1/rems.jar /rems/rems.jar
COPY config.edn.template /rems/config/config.edn.template
COPY example-theme/extra-styles.css /rems/example-theme/extra-styles.css
COPY docker-entrypoint.sh /rems/docker-entrypoint.sh

RUN chmod 664 /opt/java/openjdk/lib/security/cacerts
