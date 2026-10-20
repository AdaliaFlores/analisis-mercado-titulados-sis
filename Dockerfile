# ---------- Etapa 1: compilar el proyecto ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
# Genera el jar ejecutable y lo copia como app.jar (ignora el "original-*.jar" que crea shade)
RUN mvn -B package -DskipTests \
 && cp "$(ls target/*.jar | grep -v '^target/original-' | head -n 1)" /app/app.jar

# ---------- Etapa 2: ejecutar (imagen liviana) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
# Memoria limitada: el plan gratuito tiene 512 MB
CMD ["java", "-Xmx350m", "-XX:+UseSerialGC", "-jar", "app.jar"]