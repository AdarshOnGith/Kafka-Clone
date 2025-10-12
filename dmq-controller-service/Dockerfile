FROM eclipse-temurin:17-jre-alpine

# Install etcd client dependencies
RUN apk add --no-cache bash curl

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY target/dmq-controller-service-1.0.0.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8085/actuator/health || exit 1

# Expose ports
# 8085: HTTP REST API
EXPOSE 8085

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
