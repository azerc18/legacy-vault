# ---------- 1. Build FE ----------
FROM node:22-alpine AS fe
WORKDIR /fe
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---------- 2. Build BE ----------
FROM eclipse-temurin:25-jdk AS be
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
COPY --from=fe /fe/dist/ src/main/resources/static/
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

# ---------- 3. Run ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd -r -u 1001 app && mkdir -p /app/uploads && chown app /app/uploads
COPY --from=be /app/app.jar app.jar
USER app
EXPOSE 8084
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]