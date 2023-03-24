FROM eclipse-temurin:19-jdk as MAVEN_BUILD
COPY ./ ./
RUN ./mvnw clean package

FROM eclipse-temurin:19-jre
COPY --from=MAVEN_BUILD target/sms-chatbot-1.0-SNAPSHOT.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
