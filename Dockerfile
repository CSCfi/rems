FROM openjdk:11-jre-slim

RUN adduser --system --home /app app
WORKDIR /app
USER app

ENTRYPOINT ["java", "--illegal-access=deny", "-Drems.config=config.edn", "-jar", "rems.jar"]

COPY empty-config.edn /app/config.edn
COPY target/uberjar/rems.jar /app/rems.jar
