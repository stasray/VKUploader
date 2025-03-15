package org.example.managers;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.video.VideoAlbum;
import com.vk.api.sdk.objects.video.VideoAlbumFull;
import com.vk.api.sdk.objects.video.VideoFull;
import com.vk.api.sdk.objects.video.responses.AddAlbumResponse;
import org.example.Utils;
import org.example.exceptions.DirectoryAlreadyExistsException;
import org.example.exceptions.DirectoryNotFoundException;
import org.example.exceptions.FileAlreadyExistsException;
import org.example.exceptions.FileNotFoundException;

import java.util.*;
import java.util.stream.Collectors;

public class FileSystemAlbumsManager implements FileSystemManager {
    private final VkApiClient vk;
    private final UserActor actor;
    private final Long groupId;
    private final Map<String, List<String>> fileSystem; // название_альбома [файлы]
    private final Map<String, Integer> albums; // название_альбома айди_альбома

    public FileSystemAlbumsManager(VkApiClient vk, UserActor actor, Long groupId) {
        this.vk = vk;
        this.actor = actor;
        this.groupId = groupId;
        this.fileSystem = new HashMap<>();
        this.albums = new HashMap<>();
        fileSystem.put("/", new ArrayList<>());
    }

    public Integer getAlbumID(String path) {
        path = Utils.normalizePath(path);
        return albums.getOrDefault(path, null);
    }

    // Метод для синхронизации состояния fileSystem с реальным содержимым топика
    public void syncWithVk() throws ClientException, ApiException {
        fileSystem.clear();
        albums.clear();
        fileSystem.put("/", new ArrayList<>());

        int count = 100;
        int offsetAlbums = 0;

        System.out.println("sync started");
        while (true) {
            //TODO:: и сделать в queuemanager паузу
            System.out.println("sync...");
            List<VideoAlbum> albumsResponse = vk.video()
                    .getAlbums(actor)
                    .ownerId(-groupId)
                    .count(count)
                    .offset(offsetAlbums)
                    .needSystem(true)
                    .execute().getItems();
            offsetAlbums += count;
            if (albumsResponse == null || albumsResponse.isEmpty()) {
                break;
            }
            for (VideoAlbum album : albumsResponse) {
                if (album.getTitle().equals("Добавленные") || album.getTitle().equals("Популярные")) continue;
                int id = album.getId();
                String title = Utils.normalizePath(album.getTitle());
                System.out.println(title);
                if (!album.getTitle().equals("Загруженные")) {
                    albums.put(title, id);
                    fileSystem.put(title, new ArrayList<>());
                } else {
                    title = "/";
                }

                int offsetVideos = 0;

                while (true) {
                    List<VideoFull> videosResponse;
                    if (album.getTitle().equals("Загруженные")) {
                        videosResponse = vk.video().get(actor)
                                .ownerId(-groupId)
                                .count(count)
                                .offset(offsetVideos)
                                .execute().getItems();
                    } else {
                        videosResponse = vk.video().get(actor)
                                .ownerId(-groupId)
                                .albumId(id)
                                .count(count)
                                .offset(offsetVideos)
                                .execute().getItems();
                    }

                    offsetVideos += count;
                    if (videosResponse == null || videosResponse.isEmpty()) {
                        break;
                    }
                    for (VideoFull video : videosResponse) {
                        List<String> videoList = fileSystem.get(title);
                        if (!videoList.contains(video.getTitle())) {
                            videoList.add(video.getTitle());
                        }
                    }
                    if (videosResponse.size() < count) break;
                    try {
                        Thread.sleep(200L);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (albumsResponse.size() < count) break;
        }
    }

    public Set<String> getFolders() {
        return this.fileSystem.keySet();
    }

    public boolean isDirectoryExists(String path) {
        path = Utils.normalizePath(path);
        return fileSystem.containsKey(path);
    }

    public Set<String> getFolders(String path) {
        final String fpath = Utils.normalizePath(path);
        return this.fileSystem.keySet().stream().filter(s -> s.startsWith(fpath) && !fpath.equals(s)).collect(Collectors.toSet());
    }

    public List<String> getFiles(String path) {
        final String fpath = Utils.normalizePath(path);;
        return this.fileSystem.getOrDefault(fpath, new ArrayList<>());
    }

    @Deprecated
    public void updateTopic() {
    }

    public void addFile(String path, String filename) throws FileAlreadyExistsException, DirectoryNotFoundException {
        String directoryPath = Utils.normalizePath(path);
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
        String directoryPath = Utils.normalizePath(path);
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

    public String getAlbumLink(String path) {
        path = Utils.normalizePath(path);
        Integer albumId = albums.getOrDefault(path, null);
        if (albumId == null) return null;
        return "https://vkvideo.ru/playlist/-" + groupId + "_" + albumId;
    }

    public void addDirectory(String path) throws DirectoryAlreadyExistsException, ClientException, ApiException {
        String normalizedPath = Utils.normalizePath(path);

        if (fileSystem.containsKey(normalizedPath)) {
            throw new DirectoryAlreadyExistsException();
        }

        // Получаем родительский путь
        String parentPath = getDirectoryPath(normalizedPath);

        // Если родительская директория не существует, создаём её
        if (!fileSystem.containsKey(parentPath)) {
            addDirectory(parentPath);
        }

        String title = normalizedPath.substring(1, normalizedPath.length()-1);

        AddAlbumResponse response = vk.video().addAlbum(actor).groupId(groupId)
                .title(title).execute();
        fileSystem.put(normalizedPath, new ArrayList<>());
        albums.put(normalizedPath, response.getAlbumId());
    }

    public synchronized void deleteFolder(String path) throws DirectoryNotFoundException, ClientException, ApiException {
        String normalizedPath = Utils.normalizePath(path);
        if (!fileSystem.containsKey(normalizedPath)) {
            throw new DirectoryNotFoundException();
        }

        List<String> subPaths = fileSystem.keySet().stream()
                .filter(s -> s.startsWith(normalizedPath) && !normalizedPath.equals(s))
                .toList();

        for (String subPath : subPaths) {
            deleteFolder(subPath);
        }

        vk.video().deleteAlbum(actor).groupId(groupId).albumId(
                albums.get(normalizedPath)
        ).execute();

        fileSystem.remove(normalizedPath);
        albums.remove(normalizedPath);

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
