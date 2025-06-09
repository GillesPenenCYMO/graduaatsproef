#FROM openjdk:21-slim
#VOLUME /tmp
#
#COPY run.sh /run.sh
#RUN chmod +x /run.sh
#
#COPY target/libs/ /libs/
#COPY target/*.jar /jade-adapter.jar
#ENTRYPOINT [ "/run.sh" ]

FROM openjdk:21-slim

EXPOSE 8081
WORKDIR /app
ADD target/*.jar /app/uberjar.jar

CMD ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "uberjar.jar"]

