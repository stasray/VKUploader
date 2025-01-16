package org.example;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class FileSystemManager {
    private static int topicId = 0;
    private final VkApiClient vk;
    private final UserActor actor;

    private final Long groupId;

    private HashMap<String, List<String>> fileSystem;

    public FileSystemManager(VkApiClient vk, UserActor actor, Long groupId) {
        this.vk = vk;
        this.actor = actor;
        this.fileSystem = new HashMap<>();
        fileSystem.put("/", new ArrayList<>());
        this.groupId = groupId;
    }


    public void addFile(String path, String filename) {
        String directoryPath = getDirectoryPath(path);
        if (fileSystem.containsKey(directoryPath)) {
            fileSystem.get(directoryPath).add(filename);
            updateTopic();
        } else {
            throw new IllegalArgumentException("Directory doesn't exist");
        }
    }


    public void addDirectory(String path) {
        String directoryPath = getDirectoryPath(path);
        String newDirName = getFolderName(path);
        if (fileSystem.containsKey(directoryPath)) {
            if (fileSystem.containsKey(path)) {
                throw new IllegalArgumentException("Directory already exists");
            }
            fileSystem.get(directoryPath).add(newDirName);
            fileSystem.put(path, new ArrayList<>());
            updateTopic();
        } else {
            throw new IllegalArgumentException("Directory doesn't exist");
        }
    }

    public void delete(String path) {
        String directoryPath = getDirectoryPath(path);
        String nameToDelete = getFolderName(path);

        if (fileSystem.containsKey(directoryPath)) {
            List<String> filesAndFolders = fileSystem.get(directoryPath);
            if (filesAndFolders.contains(nameToDelete)) {
                filesAndFolders.remove(nameToDelete);
                fileSystem.remove(path);
                updateTopic();
            } else {
                throw new IllegalArgumentException("File or directory not found");
            }
        } else {
            throw new IllegalArgumentException("Directory doesn't exist");
        }

    }


    public List<String> list(String path) {
        return fileSystem.getOrDefault(path, new ArrayList<>());
    }


    private String buildTextRepresentation() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : fileSystem.entrySet()) {
            if (entry.getKey().equals("/")) {
                sb.append("/\n");
            } else {
                sb.append(entry.getKey()).append("\n");
            }
            for (String fileOrFolder : entry.getValue()) {
                if (!fileOrFolder.endsWith("/")) {
                    sb.append(entry.getKey()).append(fileOrFolder).append("\n");
                }

            }
        }
        return sb.toString();
    }


    private void loadTextRepresentation(String text) {
        fileSystem.clear();
        fileSystem.put("/", new ArrayList<>());
        String[] lines = text.split("\n");
        for (String line : lines) {
            if(line.endsWith("/")) {
                if (line.equals("/")) {
                    continue;
                }
                fileSystem.put(line, new ArrayList<>());
                String parentPath = getDirectoryPath(line);
                String newDirName = getFolderName(line);
                fileSystem.get(parentPath).add(newDirName);
            } else {
                String directoryPath = getDirectoryPath(line);
                String fileName = getFolderName(line);
                if(fileSystem.containsKey(directoryPath)) {
                    fileSystem.get(directoryPath).add(fileName);
                } else {
                    // TODO: handle errors
                    throw new IllegalArgumentException("Invalid file path");
                }

            }
        }
    }

    private void updateTopic() {
        String text = buildTextRepresentation();
        try {
            if (topicId == 0) {
                topicId = vk.board().addTopic(actor).groupId(groupId).title("FOLDERS").text(text).fromGroup(true).execute();
            } else {
                vk.board().editComment(actor, groupId, topicId, 0).message(text).execute();

            }

        } catch (ApiException | ClientException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to update VK topic", e);
        }
    }

    public void loadTopic(){
        try {
            if(topicId == 0) {
                var response = vk.board().getTopics(actor).groupId(groupId).count(1).execute();
                if(response.getItems().isEmpty()){
                    topicId = vk.board().addTopic(actor).groupId(groupId).title("FOLDERS").text("/\n").fromGroup(true).execute();

                } else {
                    topicId = response.getItems().get(0).getId();
                    var topicResponse = vk.board().getComments(actor).groupId(groupId).topicId(Integer.valueOf(topicId)).count(1).execute();
                    if(!topicResponse.getItems().isEmpty()) {
                        loadTextRepresentation(topicResponse.getItems().get(0).getText());
                    }
                }
            } else {
                var topicResponse = vk.board().getComments(actor).groupId(groupId).topicId(Integer.valueOf(topicId)).count(1).execute();
                if(!topicResponse.getItems().isEmpty()) {
                    loadTextRepresentation(topicResponse.getItems().get(0).getText());
                }
            }
        } catch (ApiException | ClientException e) {
            throw new RuntimeException(e);
        }

    }

    private String getDirectoryPath(String path) {
        int lastSlash = path.lastIndexOf("/");
        if (lastSlash <= 0) {
            return "/";
        }
        return path.substring(0, lastSlash+1);
    }

    private String getFolderName(String path) {
        int lastSlash = path.lastIndexOf("/");
        return path.substring(lastSlash+1);
    }

}
