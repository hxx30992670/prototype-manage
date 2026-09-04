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
WORKDIR /app
COPY --from=builder /workspace/api/target/*-exec.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
