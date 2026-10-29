# ========================================================
# Stage 1: Build the application with Maven and Java 21
# ========================================================
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build WAR
COPY src ./src
RUN mvn clean package -DskipTests

# ========================================================
# Stage 2: Run application on Apache Tomcat 10.1 (Java 21)
# ========================================================
FROM tomcat:10.1-jdk21-temurin

LABEL maintainer="CampusVerse Team <team@campusverse.edu>"
LABEL description="CampusVerse Lite - Student Collaboration Platform"

# Clean default web applications
RUN rm -rf /usr/local/tomcat/webapps/*

# Create persistent database directory
RUN mkdir -p /data && chmod 777 /data
VOLUME ["/data"]

# Copy compiled WAR as ROOT and as campusverse-lite context
COPY --from=builder /build/target/campusverse-lite.war /usr/local/tomcat/webapps/ROOT.war
COPY --from=builder /build/target/campusverse-lite.war /usr/local/tomcat/webapps/campusverse-lite.war

# Configure JVM flags and persistent database URL with AUTO_SERVER mode
ENV CAMPUSVERSE_DATA_DIR=/data
ENV JAVA_OPTS="-Dcampusverse.db.url=jdbc:h2:/data/campusverse_db;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE -Djava.awt.headless=true -XX:+UseG1GC"

# Expose HTTP port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD curl -f http://localhost:8080/home || exit 1

# Start Tomcat
CMD ["catalina.sh", "run"]

