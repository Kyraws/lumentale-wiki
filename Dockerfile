# Single-image build of the LumenTale wiki: SPA embedded in the Spring jar.
# Build context = the repo root.
#
#   docker build -t lumentale-wiki .
#   docker run -p 8080:8080 -e SPRING_DATASOURCE_URL=... \
#     -v /path/to/data:/app/data -e LUMENTALE_SEED_ON_EMPTY=true lumentale-wiki
#
# No game-derived data is baked into the image (it is not part of this repo).
# The app boots schema-only. If you have a dataset (see README — Data), mount
# it at /app/data and set LUMENTALE_SEED_ON_EMPTY=true.

# 1. build the SPA
FROM node:20-slim AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. build the jar with the SPA embedded as static resources
FROM eclipse-temurin:17-jdk AS jar
WORKDIR /src
COPY backend/ ./
COPY --from=web /web/dist/ src/main/resources/static/
RUN ./gradlew bootJar --no-daemon -x test && cp build/libs/*.jar /app.jar

# 3. runtime: jar + Flyway migrations
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=jar /app.jar app.jar
# schema migrations (Flyway reads filesystem:wiki-db/migrations relative to CWD)
COPY wiki-db /app/wiki-db
# Heap scales to the instance RAM; override JAVA_TOOL_OPTIONS to change it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
