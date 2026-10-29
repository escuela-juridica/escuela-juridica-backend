FROM eclipse-temurin:21-jre

RUN groupadd --system --gid 10001 esejurce \
 && useradd --system --uid 10001 --gid esejurce --no-create-home --shell /usr/sbin/nologin esejurce

WORKDIR /app
COPY target/escuela-juridica-api-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod

USER esejurce
EXPOSE 8080

ENTRYPOINT ["java", "-Xms256m", "-Xmx1g", "-Duser.timezone=America/Lima", "-jar", "app.jar"]
