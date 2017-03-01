FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/rems.jar /rems/app.jar

EXPOSE 3000

CMD java -jar /rems/app.jar migrate && java -jar /rems/app.jar
