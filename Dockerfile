# Unified Single-Container Production Dockerfile for GymKhata
# Builds React PWA + Spring Boot into a single self-contained image for Cloud Run (same origin, zero CORS)

# Stage 1: Build Frontend PWA
FROM node:22-alpine AS frontend-builder
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: Build Spring Boot JAR with embedded frontend static assets
FROM maven:3.9.9-eclipse-temurin-21-alpine AS backend-builder
WORKDIR /workspace/backend
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY backend/src ./src
COPY --from=frontend-builder /workspace/frontend/dist ./src/main/resources/static
RUN mvn clean package -DskipTests -B

# Stage 3: Minimal, hardened production runtime
FROM eclipse-temurin:21-jre-alpine

# Install fontconfig and fonts for Devanagari PDF invoice rendering
RUN apk add --no-cache fontconfig ttf-dejavu curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Kolkata /etc/localtime && \
    echo "Asia/Kolkata" > /etc/timezone

# Create unprivileged non-root user
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -s /bin/sh -D appuser

WORKDIR /app

# Copy executable jar containing backend + static PWA assets
COPY --from=backend-builder --chown=appuser:appgroup /workspace/backend/target/*.jar app.jar

USER appuser:appgroup

# Honour Cloud Run $PORT (defaults to 8080)
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:${PORT}/api/v1/config/public || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
