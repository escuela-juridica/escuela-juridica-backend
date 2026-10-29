FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/escuela-juridica-api-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-Xms256m", "-Xmx1g", "-Duser.timezone=America/Lima", "-jar", "app.jar"]
