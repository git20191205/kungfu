FROM openjdk:8-jre-slim
LABEL maintainer="kungfu"
COPY target/*.jar app.jar
EXPOSE 8090

# JVM 优化参数（Gateway 内存可以小一些）
ENTRYPOINT ["java", \
  "-Xms256m", \
  "-Xmx256m", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=50", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
  "-jar", "app.jar"]
