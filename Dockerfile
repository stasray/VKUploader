FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Копируем JAR-файл и другие ресурсы
COPY out/artifacts/VKVideoUploader_jar/*.jar app.jar

# Устанавливаем ffmpeg для извлечения метаданных видео
RUN apt-get update && apt-get install -y \
    ffmpeg \
    libxext6 \
    libxrender1 \
    libxtst6 \
    libxi6 \
    && rm -rf /var/lib/apt/lists/*

# Команда для запуска приложения
ENTRYPOINT ["java", "-jar", "app.jar"]