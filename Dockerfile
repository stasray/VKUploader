FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Копируем JAR-файл и другие ресурсы
COPY out/artifacts/VKVideoUploader_jar/*.jar app.jar

# Устанавливаем ffmpeg для извлечения метаданных видео
RUN apt-get update && apt-get install -y ffmpeg

# Команда для запуска приложения
ENTRYPOINT ["java", "-jar", "app.jar"]