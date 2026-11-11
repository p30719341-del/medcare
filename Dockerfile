# ---- Construcción ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- Ejecución ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV TZ=America/Lima
# Usuario sin privilegios; ./data (archivos y copias si usas H2) debe poder escribirse
RUN useradd -r -u 1001 medcare && mkdir -p /app/data && chown -R medcare /app
USER medcare
EXPOSE 8080
# Zona horaria de Lima: las citas usan la fecha/hora "de hoy" del servidor
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-Duser.timezone=America/Lima","-jar","app.jar"]
