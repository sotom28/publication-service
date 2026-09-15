# Imagen de runtime: recibe el jar ya compilado por el CI (GitHub Actions).
# El workflow ejecuta los tests y empaqueta (mvn package) antes de construir
# esta imagen; aquí solo se copia el jar del build context (target/).
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Ruta del jar dentro del build context. Por defecto el que genera mvn package.
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# Puerto que usa la aplicación
EXPOSE 8083

# Las variables de entorno (DB_HOST, DB_USER, DB_PASSWORD, SERVER_IP, etc.)
# se pasan en tiempo de ejecución (--env-file .env / variables del orquestador).
# Nunca copiar el archivo .env dentro de la imagen.

ENTRYPOINT ["java", "-jar", "app.jar"]