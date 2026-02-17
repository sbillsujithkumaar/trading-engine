# Build stage (creates the runnable app)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy everything needed to build
COPY . .

# Build the app (skip tests only if you want faster builds)
RUN chmod +x ./gradlew && ./gradlew clean :app:installDist -x test

# Runtime stage (smaller image)
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the built distribution from the build stage
COPY --from=build /app/app/build/install/app /app/app

# Create data folder inside container (we will mount a volume here)
RUN mkdir -p /data

# Your app should write to /data when running in Docker
ENV DATA_DIR=/data

EXPOSE 8080

# Run the installed distribution
CMD ["/app/app/bin/app"]
