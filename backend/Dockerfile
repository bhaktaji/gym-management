# Multi-stage Dockerfile for GymKhata Spring Boot Backend (Java 21)
# Stage 1: Build JAR with Maven
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# Cache dependency layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compile and package application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal, hardened production runtime
FROM eclipse-temurin:21-jre-alpine

# Install fontconfig and DejaVu fonts for Devanagari & Latin PDF invoice generation
RUN apk add --no-cache fontconfig ttf-dejavu curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Kolkata /etc/localtime && \
    echo "Asia/Kolkata" > /etc/timezone

# Create unprivileged non-root user
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -s /bin/sh -D appuser

WORKDIR /app

# Copy executable jar from builder
COPY --from=builder --chown=appuser:appgroup /workspace/target/*.jar app.jar

USER appuser:appgroup

# Honour Cloud Run $PORT (defaults to 8080)
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:${PORT}/api/v1/config/public || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
