package org.example.managers;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.board.Topic;

import java.util.*;
import java.util.stream.Collectors;

public class FileSystemManager {
    private static int topicId = 0;
    private final VkApiClient vk;
    private final UserActor actor;
    private final Long groupId;
    private final Map<String, List<String>> fileSystem;

    public FileSystemManager(VkApiClient vk, UserActor actor, Long groupId) {
        this.vk = vk;
        this.actor = actor;
        this.groupId = groupId;
        this.fileSystem = new HashMap<>();
        fileSystem.put("/", new ArrayList<>());
    }

    // Метод для синхронизации состояния fileSystem с реальным содержимым топика
    public void syncWithVk() throws ClientException, ApiException {
        List<Topic> topics = vk.board().getTopics(actor, groupId).execute().getItems();

        // Находим топик "FOLDERS"
        Optional<Topic> topicOptional = topics.stream()
                .filter(topic -> "FOLDERS".equals(topic.getTitle()))
                .findFirst();

        if (topicOptional.isPresent()) {
            topicId = topicOptional.get().getId();
            String text = vk.board().getComments(actor, groupId, topicId).execute().getItems().get(0).getText();

            // Обновляем fileSystem из текста
            parseFileSystemFromText(text);
        } else {
            topicId = 0; // Сбрасываем topicId, если топик не найден
            fileSystem.clear();
            fileSystem.put("/", new ArrayList<>()); // Корневая директория
        }
    }

    public Set<String> getFolders() {
        return this.fileSystem.keySet();
    }

    public boolean isDirectoryExists(String path) {
        path = normalizePath(path);
        return fileSystem.containsKey(path);
    }

    public Set<String> getFolders(String path) {
        final String fpath = normalizePath(path);
        return this.fileSystem.keySet().stream().filter(s -> s.startsWith(fpath) && !fpath.equals(s)).collect(Collectors.toSet());
    }

    public List<String> getFiles(String path) {
        final String fpath = normalizePath(path);;
        return this.fileSystem.getOrDefault(fpath, new ArrayList<>());
    }

    // Метод для обновления топика
    public void updateTopic() {
        String text = buildTextRepresentation();
        try {
            if (topicId == 0) {
                // Создаём новый топик
                topicId = vk.board().addTopic(actor)
                        .groupId(groupId)
                        .title("FOLDERS")
                        .text(text)
                        .fromGroup(true)
                        .execute();
            } else {
                // Обновляем существующий топик
                int id = vk.board().getComments(actor).groupId(groupId).topicId(topicId).execute().getItems().get(0).getId();
                vk.board().editComment(actor, groupId, topicId, id).message(text).execute();
            }
        } catch (ApiException | ClientException e) {
            throw new RuntimeException("Failed to update VK topic", e);
        }
    }

    // Добавление файла
    public void addFile(String path, String filename) throws FileAlreadyExistsException, DirectoryNotFoundException {
        String directoryPath = normalizePath(path);
        if (!fileSystem.containsKey(directoryPath)) {
            throw new DirectoryNotFoundException();
        }

        List<String> files = fileSystem.get(directoryPath);
        if (!files.contains(filename)) {
            files.add(filename);
        } else {
            throw new FileAlreadyExistsException();
        }
    }

    public void deleteFile(String path, String filename) throws FileNotFoundException, DirectoryNotFoundException {
        String directoryPath = normalizePath(path);
        if (!fileSystem.containsKey(directoryPath)) {
            throw new DirectoryNotFoundException();
        }

        List<String> files = fileSystem.get(directoryPath);
        if (files.contains(filename)) {
            files.remove(filename);
        } else {
            throw new FileNotFoundException();
        }
    }

    public static class DirectoryNotFoundException extends Throwable {
    }

    public static class FileNotFoundException extends Throwable {
    }

    public static class FileAlreadyExistsException extends Throwable {
    }

    public static class DirectoryAlreadyExistsException extends Throwable {
    }

    public void addDirectory(String path) throws DirectoryAlreadyExistsException {
        String normalizedPath = normalizePath(path);

        // Проверяем, существует ли директория
        if (fileSystem.containsKey(normalizedPath)) {
            throw new DirectoryAlreadyExistsException();
        }

        // Получаем родительский путь
        String parentPath = getDirectoryPath(normalizedPath);

        // Если родительская директория не существует, создаём её
        if (!fileSystem.containsKey(parentPath)) {
            addDirectory(parentPath);
        }

        // Создаём запись для текущей директории
        fileSystem.put(normalizedPath, new ArrayList<>());
    }

    public synchronized void deleteFolder(String path) throws DirectoryNotFoundException {
        String normalizedPath = normalizePath(path);
        if (!fileSystem.containsKey(normalizedPath)) {
            throw new DirectoryNotFoundException();
        }

        List<String> subPaths = fileSystem.keySet().stream()
                .filter(s -> s.startsWith(normalizedPath) && !normalizedPath.equals(s))
                .toList();

        for (String subPath : subPaths) {
            fileSystem.remove(subPath);
        }

        fileSystem.remove(normalizedPath);
    }


    // Метод для нормализации путей
    public static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        if (!path.endsWith("/")) {
            path = path + "/";
        }

        return path;
    }

    // Построение текстового представления файловой системы
    private String buildTextRepresentation() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : fileSystem.entrySet()) {
            sb.append(entry.getKey()).append("\n");
            for (String file : entry.getValue()) {
                sb.append(entry.getKey()).append(file).append("\n");
            }
        }
        return sb.toString();
    }

    // Парсинг файловой системы из текста
    private void parseFileSystemFromText(String text) {
        fileSystem.clear();
        fileSystem.put("/", new ArrayList<>());

        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.isEmpty() || !line.contains("/")) continue;

            int lastSlash = line.lastIndexOf("/");
            String directoryPath = line.substring(0, lastSlash + 1);
            String itemName = line.substring(lastSlash + 1);

            fileSystem.putIfAbsent(directoryPath, new ArrayList<>());
            if (!itemName.isEmpty()) {
                fileSystem.get(directoryPath).add(itemName);
            }
        }
    }

    private String getDirectoryPath(String path) {
        if (path.equals("/")) {
            return "/";
        }

        if (path.endsWith("/")) path = path.substring(0, path.length()-1);
        int lastSlash = path.lastIndexOf("/");
        return (lastSlash <= 0) ? "/" : path.substring(0, lastSlash+1);
    }

    private String getFolderName(String path) {
        int lastSlash = path.lastIndexOf("/");
        return path.substring(lastSlash + 1);
    }
}
