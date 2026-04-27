FROM openjdk:8-jre-slim
LABEL maintainer="kungfu"
COPY target/*.jar app.jar
EXPOSE 8085

# JVM 优化参数
ENTRYPOINT ["java", \
  "-Xms512m", \
  "-Xmx512m", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=50", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
  "-jar", "app.jar"]
