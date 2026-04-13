LABEL authors="Mohit Awasthi"

# Use OpenJDK (you already need this)
FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY target/*.jar price-drop-service-0.0.1-SNAPSHOT.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]