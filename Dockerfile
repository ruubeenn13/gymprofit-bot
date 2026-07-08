# ============================================================
# Dockerfile de GymProBot. Multi-stage (mismo estilo que la API GymProFit):
#   1) build   -> compila y empaqueta el fat-jar con JDK 21 + Maven
#   2) runtime -> imagen ligera JRE 21 que solo ejecuta el jar
# El bot expone /health (health server del JDK); Render enruta por $PORT.
# ============================================================

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache de dependencias: primero el pom, luego el resto
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src

# Empaqueta sin tests (ya corren en CI); shade genera el jar ejecutable
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Zona horaria: los jobs usan Europe/Madrid; el contenedor por defecto va en UTC (SPEC §3).
ENV TZ=Europe/Madrid

COPY --from=build /build/target/gymprofit-bot.jar app.jar

EXPOSE 8080

# MaxRAMPercentage=60 para aprovechar la instancia pequeña de Render sin OOM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-jar", "app.jar"]
