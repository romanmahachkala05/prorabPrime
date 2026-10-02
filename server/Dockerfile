# Runs the prebuilt fat jar; the image does not build the project, because configuring the
# Gradle build needs the Android SDK. Build first: ./gradlew :server:buildFatJar
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/server-all.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
