# Dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /docker-app
COPY target/golfbeta-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/docker-app/app.jar"]
