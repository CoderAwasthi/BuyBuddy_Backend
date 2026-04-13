LABEL authors="Mohit Awasthi"

# Use OpenJDK (you already need this)
FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]