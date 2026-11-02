FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 onboarding && mkdir -p /app/data && chown onboarding /app/data
COPY --from=build /app/target/onboarding-backend-*.jar app.jar
USER onboarding
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
