# Render has no native Java/JVM runtime -- Docker is the documented path.
# Multi-stage: build with a full JDK, run on a slim JRE.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# $PORT is Render's dynamically-assigned port (see application.yml server.port).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
