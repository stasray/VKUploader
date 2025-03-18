FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Копируем JAR-файл и другие ресурсы
COPY out/artifacts/VKVideoUploader_jar/*.jar app.jar

# Устанавливаем зависимости
RUN apt-get update && apt-get install -y \
    ffmpeg \
    libxext6 \
    libxrender1 \
    libxtst6 \
    libxi6 \
    xvfb \
    x11vnc \
    fluxbox \
    websockify \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Устанавливаем noVNC
RUN mkdir -p /usr/share/novnc && \
    wget -qO- https://github.com/novnc/noVNC/archive/refs/tags/v1.3.0.tar.gz | tar xz --strip-components=1 -C /usr/share/novnc && \
    ln -s /usr/share/novnc/vnc.html /usr/share/novnc/index.html

# Открываем порты для VNC и noVNC
EXPOSE 5900 6080

# Скрипт запуска
CMD if [ "$GUI" = "true" ]; then \
        Xvfb :99 -screen 0 1280x1024x24 & \
        fluxbox & \
        x11vnc -display :99 -forever -nopw -listen 0.0.0.0 -xkb & \
        websockify --web /usr/share/novnc/ 6080 localhost:5900 & \
        DISPLAY=:99 java -jar app.jar; \
    else \
        java -jar app.jar; \
    fi
