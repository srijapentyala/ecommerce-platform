FROM eclipse-temurin:21-jre
WORKDIR /app
COPY notification-service/target/notification-service-1.0.0.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
