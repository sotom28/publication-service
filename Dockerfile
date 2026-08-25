# 1. Build stage: compila el jar con Maven
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# 2. Runtime stage: solo el JRE + el jar (imagen final liviana)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Puerto que usa la aplicación
EXPOSE 8083

# Las variables de entorno (DB_URL, DB_USERNAME, DB_PASSWORD, etc.)
# se pasan en tiempo de ejecución, ej: docker run --env-file .env ...
# Nunca copiar el archivo .env dentro de la imagen.

CMD ["java", "-jar", "app.jar"]
