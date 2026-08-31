FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /app/target/wally-customer-support-*.jar /app/app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
