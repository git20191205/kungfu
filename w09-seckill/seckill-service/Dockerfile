FROM openjdk:8-jre-slim
LABEL maintainer="kungfu"
COPY target/*.jar app.jar
EXPOSE 8084

# JVM 优化参数
# -Xms512m -Xmx512m: 固定堆内存，避免动态扩容
# -XX:+UseG1GC: 使用 G1 垃圾收集器
# -XX:MaxGCPauseMillis=50: 最大 GC 停顿时间 50ms
# -XX:+HeapDumpOnOutOfMemoryError: OOM 时自动生成 heap dump
# -XX:HeapDumpPath=/tmp: heap dump 文件路径
ENTRYPOINT ["java", \
  "-Xms512m", \
  "-Xmx512m", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=50", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/tmp/heapdump.hprof", \
  "-jar", "app.jar"]
