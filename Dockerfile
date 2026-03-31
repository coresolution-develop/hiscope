# Stage 1: Build
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src

RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

# Stage 2: Run
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xmx200m -Xms100m -XX:MaxMetaspaceSize=100m -Djava.security.egd=file:/dev/./urandom"

EXPOSE ${PORT:-8099}

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
