FROM openjdk:8-jre-slim
LABEL maintainer="kungfu"
COPY target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
