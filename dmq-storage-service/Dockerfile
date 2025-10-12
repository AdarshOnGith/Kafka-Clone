FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create data directories
RUN mkdir -p /app/data/storage /app/data/logs /app/data/indexes

# Copy the JAR file
COPY target/dmq-storage-service-1.0.0.jar app.jar

# Expose ports
EXPOSE 8082 9092

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
