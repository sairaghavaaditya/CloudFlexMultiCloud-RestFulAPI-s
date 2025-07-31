# --- Multi-stage build for Spring Boot application ---

# Stage 1: Build the Spring Boot application
# Uses Maven with Eclipse Temurin JDK 17, matching your Spring Boot 3.1.0
FROM maven:3.9.6-eclipse-temurin-17 AS build

# Set the working directory inside the container for this stage
WORKDIR /app

# Copy the Maven build configuration
COPY pom.xml .

# Copy the source code
COPY src ./src

# Build the Spring Boot application into an executable JAR.
# Skips tests for faster build in the Docker image.
RUN mvn clean package -DskipTests

# Stage 2: Create the final lightweight runtime image
# Uses a smaller JRE 17 Alpine image for runtime
FROM eclipse-temurin:17-jre-alpine

# Set the working directory inside the container for this stage
WORKDIR /app

# Copy the built JAR file from the 'build' stage
# The JAR name will be CloudFlexMultiCloud-0.0.1-SNAPSHOT.jar based on your pom.xml
COPY --from=build /app/target/CloudFlexMultiCloud-0.0.1-SNAPSHOT.jar app.jar

# IMPORTANT: Copy your keystore.p12 file into the container
# Since it's directly under src/main/resources, the path is now simpler
COPY src/main/resources/keystore.p12 /app/keystore.p12

# Expose the port your Spring Boot application listens on (8443 in your case)
# Render will use this to route external traffic.
EXPOSE 8443

# Define the command to run your Spring Boot application when the container starts.
# We're also explicitly setting the keystore path here relative to the /app directory
# and passing the password via an environment variable for security.
ENTRYPOINT ["java", "-Dserver.ssl.key-store=/app/keystore.p12", "-Dserver.ssl.key-store-password=${SSL_KEY_STORE_PASSWORD}", "-jar", "app.jar"]