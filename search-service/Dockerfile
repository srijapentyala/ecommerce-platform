FROM eclipse-temurin:21-jre
WORKDIR /app
COPY search-service/target/search-service-1.0.0.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
