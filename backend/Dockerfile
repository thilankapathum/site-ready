# --- Build stage ---
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src

# Download dependencies first (better layer caching)
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -q

RUN mvn package -DskipTests -q

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup -S ssv && adduser -S ssv -G ssv

# Create keystore dir as root before switching user
# Keystore will be mounted at /app/keystore via docker volume
RUN mkdir -p /app/keystore && chown -R ssv:ssv /app

USER ssv

COPY --from=builder /app/target/site-ready-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]