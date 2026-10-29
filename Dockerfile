FROM eclipse-temurin:21-jdk-jammy AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
ENV HOST=0.0.0.0 PORT=8000 DB_PATH=/data/cuenta.db
WORKDIR /app
RUN groupadd --gid 10001 appuser \
    && useradd --uid 10001 --gid 10001 --create-home appuser \
    && mkdir /data && chown appuser:appuser /data
COPY --from=build --chown=appuser:appuser /build/target/cuenta-clara.jar ./cuenta-clara.jar
USER appuser
VOLUME ["/data"]
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "cuenta-clara.jar"]
