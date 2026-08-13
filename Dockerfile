# ---------- Etapa 1: build ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---------- Etapa 2: separacao em camadas ----------
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /layers
COPY --from=build /build/target/orderflow-fulfillment-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------- Etapa 3: imagem final ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S fulfillment && adduser -S fulfillment -G fulfillment
RUN apk add --no-cache curl

COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./

USER fulfillment
EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8081/actuator/health/liveness || exit 1

#
# O `jarmode=tools` da etapa anterior produz `app.jar` + `lib/`, com o Class-Path
# no manifesto: quem inicia e o `java -jar`. O JarLauncher pertence ao formato
# antigo (`jarmode=layertools`), que explodia as classes do loader na imagem.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", \
            "-jar", "app.jar"]
