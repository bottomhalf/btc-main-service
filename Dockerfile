FROM maven:3.8.1-openjdk-17 AS MAVEN

MAINTAINER BOTTOMHALF

# Create .m2 directory if it doesn't exist
RUN mkdir -p /root/.m2

COPY pom.xml /build/
COPY src /build/src/

WORKDIR /build/
RUN mvn package

FROM eclipse-temurin:17-jdk
WORKDIR /app
EXPOSE 7801

COPY --from=MAVEN /build/target/btc-svc-manager.jar /app/

ENTRYPOINT ["java", "-jar", "btc-svc-manager.jar", "--spring.profiles.active=prod"]