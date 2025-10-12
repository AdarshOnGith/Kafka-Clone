FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY target/dmq-api-gateway-1.0.0.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose ports
# 8080: HTTP Gateway
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
