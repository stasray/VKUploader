package ru.sanichik.managers;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.video.VideoAlbum;
import com.vk.api.sdk.objects.video.VideoFull;
import com.vk.api.sdk.objects.video.responses.AddAlbumResponse;
import ru.sanichik.utils.Utils;
import ru.sanichik.exceptions.DirectoryAlreadyExistsException;
import ru.sanichik.exceptions.DirectoryNotFoundException;
import ru.sanichik.exceptions.FileAlreadyExistsException;
import ru.sanichik.exceptions.FileNotFoundException;
import ru.sanichik.objects.VideoObject;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages video albums and files within a simulated file system structure.
 * Provides synchronization with VK albums and operations like renaming, deletion, and retrieval.
 */
public class FileSystemAlbumsManager implements FileSystemManager {
    private final VkApiClient vk;
    private final UserActor actor;
    private final Long groupId;
    private final Map<String, List<VideoObject>> fileSystem; // название_альбома [файлы]
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

    public void syncWithVk() throws ClientException, ApiException {
        fileSystem.clear();
        albums.clear();
        fileSystem.put("/", new ArrayList<>());

        int count = 100;
        int offsetAlbums = 0;
        int totalAlbums = 0;
        int loadedAlbums = 0;

        System.out.print("Synchronization with VK Albums started. It may take a while...\n");

        while (true) {
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

            totalAlbums += albumsResponse.size();

            if (albumsResponse.size() < count) {
                break;
            }
        }

        totalAlbums -= 2;
        offsetAlbums = 0;

        while (true) {
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

                    fileSystem.get(title).addAll(videosResponse.stream().map(VideoObject::new).toList());

                    if (videosResponse.size() < count) break;
                    try {
                        Thread.sleep(200L);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                loadedAlbums++;
                System.out.print("\rLoaded albums: " + loadedAlbums + "/" + totalAlbums);
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            if (albumsResponse.size() < count) break;
        }

        System.out.println("\nSynchronization completed! Loaded " + loadedAlbums + " albums.");
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

    public List<VideoObject> getFiles(String path) {
        final String fpath = Utils.normalizePath(path);;
        return this.fileSystem.getOrDefault(fpath, new ArrayList<>());
    }

    @Deprecated
    public void updateTopic() {
    }

    @Override
    public void renameVideo(String path, VideoObject videoObject, String newFileName) throws ClientException, ApiException {
        path = Utils.normalizePath(path);
        List<VideoObject> files = fileSystem.getOrDefault(path, null);
        if (files == null || !files.contains(videoObject)) return;

        String newFileNameWithoutExtension = newFileName.contains(".") ? newFileName.substring(0, newFileName.lastIndexOf('.')) : newFileName;

        vk.video().edit(actor)
                .videoId(videoObject.getId())
                .ownerId(groupId * -1L)
                .name(newFileNameWithoutExtension)
                .execute();

        videoObject.setTitle(newFileNameWithoutExtension);
        fileSystem.put(path, files);
    }

    @Override
    public void renameFolder(String path, String newPath) throws ClientException, ApiException {
        path = Utils.normalizePath(path);
        Integer id = albums.getOrDefault(path, null);
        if (id == null) return;

        System.out.println("Album " + path + " was renamed to " + newPath.replaceFirst("/", ""));
        vk.video().editAlbum(actor)
                .groupId(groupId).albumId(id)
                .title(newPath.replaceFirst("/", "")).execute();
        // Обновляем ключи в хешмапах
        Map<String, List<VideoObject>> updatedFileSystem = new HashMap<>();
        Map<String, Integer> updatedAlbums = new HashMap<>();

        for (Map.Entry<String, List<VideoObject>> entry : fileSystem.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(path)) {
                String updatedKey = key.replaceFirst(Pattern.quote(path), newPath);
                updatedFileSystem.put(updatedKey, entry.getValue());
            } else {
                updatedFileSystem.put(key, entry.getValue());
            }
        }

        for (Map.Entry<String, Integer> entry : albums.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(path)) {
                String updatedKey = key.replaceFirst(Pattern.quote(path), newPath);
                updatedAlbums.put(updatedKey, entry.getValue());
            } else {
                updatedAlbums.put(key, entry.getValue());
            }
        }

        fileSystem.clear();
        fileSystem.putAll(updatedFileSystem);

        albums.clear();
        albums.putAll(updatedAlbums);
    }

    public void addFile(String path, VideoObject video) throws FileAlreadyExistsException, DirectoryNotFoundException {
        String directoryPath = Utils.normalizePath(path);
        if (!fileSystem.containsKey(directoryPath)) {
            throw new DirectoryNotFoundException();
        }

        List<VideoObject> files = fileSystem.get(directoryPath);
        if (!files.contains(video)) {
            files.add(video);
        } else {
            throw new FileAlreadyExistsException();
        }
    }

    public VideoObject getVideo(String currDirectory, String filename) {
        if (!fileSystem.containsKey(currDirectory)) return null;
        for (VideoObject video : fileSystem.get(currDirectory)) {
            if (video.getTitle().equals(filename)) return video;
        }
        return null;
    }

    public void deleteFile(String path, VideoObject video) throws FileNotFoundException, DirectoryNotFoundException {
        String directoryPath = Utils.normalizePath(path);
        if (!fileSystem.containsKey(directoryPath)) {
            throw new DirectoryNotFoundException();
        }

        List<VideoObject> files = fileSystem.get(directoryPath);
        if (files.contains(video)) {
            files.remove(video);
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
