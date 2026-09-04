FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace
COPY services/backend/pom.xml pom.xml
COPY services/backend/mvnw mvnw
COPY services/backend/.mvn .mvn
COPY services/backend/common common
COPY services/backend/persistence persistence
COPY services/backend/api api
COPY services/backend/publish-worker publish-worker
COPY services/backend/preview-gateway preview-gateway
COPY services/backend/local-dev local-dev

RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 10001 gateway && adduser -u 10001 -G gateway -s /bin/sh -D gateway
RUN mkdir -p /app /work && chown -R 10001:10001 /app /work
USER 10001:10001
WORKDIR /app
COPY --from=builder --chown=10001:10001 /workspace/preview-gateway/target/*-exec.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
