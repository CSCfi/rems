# For documentation see docs/installing-upgrading.md

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache bash gettext

RUN mkdir -p /rems/keys
RUN mkdir -p /rems/theme/extra-translations

WORKDIR /rems

ENTRYPOINT ["bash","./docker-entrypoint.sh"]

ADD https://github.com/CSCfi/rems/releases/download/v2.38.1/rems.jar /rems/rems.jar
COPY config.edn.template /rems/config/config.edn.template
COPY example-theme/extra-styles.css /rems/theme/extra-styles.css
COPY theme/extra-translations/en.edn /rems/theme/extra-translations/en.edn
COPY theme/theme.edn /rems/theme/theme.edn
COPY docker-entrypoint.sh /rems/docker-entrypoint.sh

RUN chmod 664 /opt/java/openjdk/lib/security/cacerts
