FROM eclipse-temurin:21-jre

# MODULE은 apps 아래의 경로다. 나뉜 서비스는 order-service/api처럼 두 단계가 된다 (ADR-0014).
# ARTIFACT는 jar의 이름이며 모듈 이름과 다를 수 있다 — api라는 이름만으로는 어느 서비스인지
# 알 수 없어서 order-api처럼 짓는다.
ARG MODULE
ARG ARTIFACT

WORKDIR /app
COPY apps/${MODULE}/build/libs/${ARTIFACT}-0.1.0.jar /app/service.jar
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
