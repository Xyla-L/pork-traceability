FROM maven:3.9.9-eclipse-temurin-17 AS builder
ARG SERVICE
WORKDIR /workspace
COPY pom.xml ./
COPY common ./common
COPY gateway-service ./gateway-service
COPY auth-service ./auth-service
COPY breeding-service ./breeding-service
COPY slaughter-service ./slaughter-service
COPY distribution-service ./distribution-service
COPY sales-service ./sales-service
COPY trace-service ./trace-service
COPY blockchain-service ./blockchain-service
COPY file-service ./file-service
RUN mvn -B -pl "${SERVICE}" -am install -DskipTests \
    && mvn -B -pl "${SERVICE}" dependency:copy-dependencies -DoutputDirectory=/runtime-libs

FROM eclipse-temurin:17-jre-jammy
ARG SERVICE
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /workspace/${SERVICE}/target/${SERVICE}-*.jar /app/app.jar
COPY --from=builder /runtime-libs /app/lib
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /app/data && chown -R app:app /app
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp '/app/app.jar:/app/lib/*' $MAIN_CLASS"]
