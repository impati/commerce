FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
COPY apps/${SERVICE}/build/libs/${SERVICE}-0.1.0.jar /app/service.jar
ENTRYPOINT ["java", "-jar", "/app/service.jar"]

