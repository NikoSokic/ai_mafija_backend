# Build: bez testova (testovi se pokreću lokalno s ./gradlew build)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# Run
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
# Render Free ima 512 MB RAM-a
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
