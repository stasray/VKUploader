package org.example.managers;

import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.example.exceptions.DirectoryAlreadyExistsException;
import org.example.exceptions.DirectoryNotFoundException;
import org.example.exceptions.FileAlreadyExistsException;
import org.example.exceptions.FileNotFoundException;

import java.util.List;
import java.util.Set;

public interface FileSystemManager {

    public void syncWithVk() throws ClientException, ApiException;

    public Set<String> getFolders();

    public boolean isDirectoryExists(String path);

    public List<String> getFiles(String path);

    public Set<String> getFolders(String path);

    public void updateTopic();

    public void addFile(String path, String filename) throws FileAlreadyExistsException, DirectoryNotFoundException;

    public void deleteFile(String path, String filename) throws FileNotFoundException, DirectoryNotFoundException;

    public void addDirectory(String path) throws DirectoryAlreadyExistsException, ClientException, ApiException;

    public void deleteFolder(String path) throws DirectoryNotFoundException, ClientException, ApiException;

}
