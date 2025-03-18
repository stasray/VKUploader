package ru.sanichik.managers;

import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import ru.sanichik.exceptions.DirectoryAlreadyExistsException;
import ru.sanichik.exceptions.DirectoryNotFoundException;
import ru.sanichik.exceptions.FileAlreadyExistsException;
import ru.sanichik.exceptions.FileNotFoundException;
import ru.sanichik.objects.VideoObject;

import java.util.List;
import java.util.Set;

public interface FileSystemManager {

    /**
     * Synchronizes the local file system structure with VK albums.
     * Fetches albums and videos from VK and updates the local structure accordingly.
     *
     * @throws ClientException If a client-side error occurs while interacting with the VK API.
     * @throws ApiException    If an API error occurs during synchronization.
     */
    void syncWithVk() throws ClientException, ApiException;

    /**
     * Retrieves a set of all available folder names in the file system.
     *
     * @return A set of folder names.
     */
    Set<String> getFolders();

    /**
     * Checks whether a given directory exists in the file system.
     *
     * @param path The directory path.
     * @return True if the directory exists, false otherwise.
     */
    boolean isDirectoryExists(String path);

    /**
     * Retrieves a list of video files located in the specified directory.
     *
     * @param path The directory path.
     * @return A list of {@link VideoObject} instances.
     */
    List<VideoObject> getFiles(String path);

    /**
     * Retrieves a set of subfolders located within the specified directory.
     *
     * @param path The parent directory path.
     * @return A set of subfolder names.
     */
    Set<String> getFolders(String path);

    /**
     * Updates the topic of the system. (Deprecated, does nothing)
     */
    @Deprecated
    void updateTopic();

    /**
     * Retrieves a specific video file from the given directory.
     *
     * @param currDirectory The directory where the video is located.
     * @param filename      The name of the video file.
     * @return The corresponding {@link VideoObject}, or null if not found.
     */
    VideoObject getVideo(String currDirectory, String filename);

    /**
     * Renames a video file in the specified directory.
     *
     * @param path        The directory path.
     * @param videoObject The video object to rename.
     * @param newFileName The new name for the video file.
     * @throws ClientException If a client-side error occurs while interacting with VK.
     * @throws ApiException    If an API error occurs during the renaming process.
     */
    void renameVideo(String path, VideoObject videoObject, String newFileName) throws ClientException, ApiException;

    /**
     * Renames a folder (album) in the file system.
     *
     * @param path    The current folder path.
     * @param newName The new folder name.
     * @throws ClientException If a client-side error occurs while interacting with VK.
     * @throws ApiException    If an API error occurs during the renaming process.
     */
    void renameFolder(String path, String newName) throws ClientException, ApiException;

    /**
     * Adds a video file to the specified directory.
     *
     * @param path  The directory path.
     * @param video The video file to add.
     * @throws FileAlreadyExistsException If the video already exists in the directory.
     * @throws DirectoryNotFoundException If the specified directory does not exist.
     */
    void addFile(String path, VideoObject video) throws FileAlreadyExistsException, DirectoryNotFoundException;

    /**
     * Deletes a video file from the specified directory.
     *
     * @param path  The directory path.
     * @param video The video file to delete.
     * @throws FileNotFoundException      If the file does not exist.
     * @throws DirectoryNotFoundException If the specified directory does not exist.
     */
    void deleteFile(String path, VideoObject video) throws FileNotFoundException, DirectoryNotFoundException;

    /**
     * Creates a new directory (album) in the file system.
     *
     * @param path The path of the new directory.
     * @throws DirectoryAlreadyExistsException If the directory already exists.
     * @throws ClientException                 If a client-side error occurs while interacting with VK.
     * @throws ApiException                    If an API error occurs during directory creation.
     */
    void addDirectory(String path) throws DirectoryAlreadyExistsException, ClientException, ApiException;

    /**
     * Deletes a directory (album) and all its contents from the file system.
     *
     * @param path The path of the directory to delete.
     * @throws DirectoryNotFoundException If the directory does not exist.
     * @throws ClientException            If a client-side error occurs while interacting with VK.
     * @throws ApiException               If an API error occurs during the deletion process.
     */
    void deleteFolder(String path) throws DirectoryNotFoundException, ClientException, ApiException;
}
