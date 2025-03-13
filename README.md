# VK VideoUploader

VK VideoUploader — это утилита для автоматизированной выгрузки видеороликов в группы ВКонтакте с организацией виртуальной файловой системы. Поддерживает работу на Windows, MacOS и Linux на архитектурах arm64, x86/x64.

## Описание
Todo to doo too doooooo

## Установка (Без GUI)
### 1. Установите Docker
Убедитесь, что Docker установлен на вашем компьютере. Если Docker не установлен, следуйте официальной инструкции:
- [Установка Docker для Windows](https://docs.docker.com/desktop/windows/install/)
- [Установка Docker для macOS](https://docs.docker.com/desktop/mac/install/)
- [Установка Docker для Linux](https://docs.docker.com/engine/install/)

### 2. Запустите контейнер
### 🔧 Запуск в консольном режиме

Для запуска приложения в консольном режиме выполните следующую команду:

```bash
docker run -it \
  -v "ДОМАШНЯЯ_ДИРЕКТОРИЯ":/root
  krut74891/vk-video-uploader:latest
```
**Важно:** В параметре `-v` укажите путь к рабочей папке, где хранятся видео для загрузки. Путь к директории может отличаться в зависимости от операционной системы.

Пример для различных ОС:
- **Windows**: `-v C:/Users/Stanislav/Documents:/root`
- **MacOS/Linux**: `-v /home/user/videos:/root`
### 🛠 Обязательные параметры:
При запуске необходимо указать токен и ID группы.

| Параметр       | Описание |
|---------------|----------|
| `Token`   | Вставьте сюда ссылку целиком из адресной строки после авторизации на [этой странице](https://oauth.vk.com/authorize?client_id=52502099&display=page&redirect_uri=https://oauth.vk.com/blank.html&scope=friends,video,groups&response_type=token&v=5.59). |
| `Group ID` | Укажите числовой ID вашей группы ВКонтакте. |

**Готово! Если вы указали все параметры, утилита должна запуститься.**

### 🎨 Запуск с графическим интерфейсом (GUI)

Для работы графического интерфейса необходимо наличие X11 Server на вашем компьютере.
Для установки X11 сервера ознакомьтесь с инструкциями на [этой странице](install_x11.md).
#### Если X11 установлен:
1. Узнайте свой IPv4 адрес. Это можно сделать через команду `ipconfig` в командной строке или терминале.
4. Запустите контейнер с настройками для графического интерфейса:

```bash
docker run -it `
  -v "ДОМАШНЯЯ_ДИРЕКТОРИЯ":/root `
  --env DISPLAY=192.168.1.151:0 `
  -e GUI="true" `
  krut74891/vk-video-uploader:latest
```

Замените `192.168.1.151` на свой IP адрес, который вы нашли в предыдущем шаге. Не забудьте указать порт `:0`! Остальные параметры укажите аналогично этапу с запуском в консольном режиме.


---

### Требования
- Docker
- X11 Server (Для GUI, необязательно)
---

