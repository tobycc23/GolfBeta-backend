# Dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /docker-app

# Let the build pick up whatever JAR is produced in target/
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} golfbeta-app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/docker-app/golfbeta-app.jar"]