FROM openjdk:18-jdk-slim as MAVEN_BUILD
COPY ./ ./
RUN ./mvnw clean package

FROM amazoncorretto:20-alpine
COPY --from=MAVEN_BUILD target/sms-chat-1.0-SNAPSHOT.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
