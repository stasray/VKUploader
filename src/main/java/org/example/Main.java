package org.example;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import org.example.managers.FileSystemManager;
import org.example.managers.VideoLoaderManager;
import org.example.ui.MainWindow;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.System.out;

public class Main {

    private static long groupId = 227898147;
    private static boolean gui = true;
    private static String token = "---";
    public static int MAX_CONCURRENT_UPLOADS = 5;

    private static String currDirectory = "/";

    private static FileSystemManager fsm = null;
    private static VideoLoaderManager vlm = null;

    public static void main(String[] serverargs) {
        token = "https://oauth.vk.com/blank.html#access_token=vk1.a.VpV3kiNEuaHDrxOqQv5rE7JsITABmY-M7vmdiHIERia29TU_ITrN40WTM6D-232IzmYKKhaPqBrFPZJUmU_HHuc3LTFH4aM4T3LNkvnyShoR4v_n6yxorOJehqiQJDJWRk60gog4X_ZjMjitXiXDoQ5V3x-Jb7wS6VQLdzVjKJ6B_BSqVZq9XVnrkhGM3ePC_Nvs1ibB3Wl35PNY31W5Jw&expires_in=86400&user_id=198248840";
        groupId = 227898147L;
        //token = System.getenv("VK_TOKEN");

        if (token == null) {
            out.println("Token not found.");
            return;
        }
        if (token.contains("access_token=")) {
            token = token.substring(token.indexOf("access_token=") + 13);
            if (token.contains("&")) {
                token = token.substring(0, token.indexOf("&"));
            }
        }

        /*try {
            groupId = Long.parseLong(System.getenv("VK_GROUP_ID"));
        } catch (NumberFormatException e) {
            out.println("Group ID is invalid.");
            return;
        }*/

        try {
            MAX_CONCURRENT_UPLOADS = Integer.parseInt(System.getenv("MAX_CONCURRENT_UPLOADS"));
        } catch (NumberFormatException e) {
            System.out.println("Error! max_concurrent cannot be lower than 1. Using default value: 5");
            MAX_CONCURRENT_UPLOADS = 5;
        }

        gui = true;
        //gui = !Boolean.parseBoolean(System.getenv("NOGUI"));

        out.println("VKVideoUploader started. Use \"help\" for list of commands.");

        /**TODO: check token*/

        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);
        UserActor actor = new UserActor(198248840L, token);
        fsm = new FileSystemManager(vk, actor, groupId);
        vlm = new VideoLoaderManager(vk, actor, groupId);
        fsm.syncWithVk();

        if (gui) {
            System.setProperty("apple.awt.application.appearance", "NSAppearanceNameVibrantDark");
            SwingUtilities.invokeLater(() -> {
                MainWindow dialog = new MainWindow(fsm, vlm);
                dialog.pack();
                dialog.setLocationRelativeTo(null);
                dialog.setVisible(true);
                System.exit(0);
            });
        }

        Scanner scanner = new Scanner(System.in);

        while (true) {
            out.print(currDirectory + " > ");
            String[] cmdfull = scanner.nextLine().trim().split(" ");
            String cmd = cmdfull[0];
            String[] args = Arrays.copyOfRange(cmdfull, 1, cmdfull.length);

            if (cmd.equalsIgnoreCase("exit")) {
                out.println("VKVideoUploader stopped.");
                break;
            }
            if (cmd.equalsIgnoreCase("help")) {
                out.println("Commands:");
                out.println(" exit - stop server");
                out.println(" sync - sync with VK");
                out.println(" ls - check files and subfolders in current folder.");
                out.println(" mkdir <name> - create folder");
                out.println(" cd <path> - go to path folder");
                out.println(" rmdir <name> - remove folder with all files and subfolders. WARNING! It will remove all video files in folder and subfolders!");
                out.println(" rmfile <name> - remove file in current directory.");
                out.println(" addvideo <path> - export videos from local path to VK. It can be folder with files or single file. Use -meta to save meta to comment.");
                continue;
            }
            if (cmd.equalsIgnoreCase("ls")) {
                fsm.syncWithVk();
                //TODO: учесть, что папка может быть удалена в момент использования
                out.println("Files and folders in " + currDirectory);
                fsm.getFolders(currDirectory).forEach(s -> {
                    s = s.substring(currDirectory.length());

                    int count=0;
                    for (char element : s.toCharArray()) {
                        if (element == '/') count++;
                    }
                    if (count <= 1) {
                        s = s.substring(0, s.indexOf("/"));
                        out.println(s);
                    }
                });
                fsm.getFiles(currDirectory).forEach(out::println);
                continue;
            }
            if (cmd.equalsIgnoreCase("sync")) {
                fsm.syncWithVk();
                continue;
            }
            if (cmd.equalsIgnoreCase("cd")) {
                if (args.length != 1) {
                    out.println("Usage: cd <path>");
                    continue;
                }

                String targetPath = args[0];
                if (targetPath.equals("..")) {
                    // Переход на уровень выше
                    if (!currDirectory.equals("/")) {
                        currDirectory = currDirectory.substring(0, currDirectory.lastIndexOf("/"));
                        currDirectory = currDirectory.substring(0, currDirectory.lastIndexOf("/")) + '/';
                        if (currDirectory.isEmpty()) {
                            currDirectory = "/";
                        }
                    }
                } else {
                    // Переход в указанную директорию
                    String newPath;
                    if (targetPath.startsWith("/")) {
                        // Абсолютный путь
                        newPath = targetPath;
                    } else {
                        // Относительный путь
                        newPath = currDirectory.endsWith("/") ? currDirectory + targetPath : currDirectory + "/" + targetPath;
                    }

                    newPath = FileSystemManager.normalizePath(newPath);

                    if (fsm.isDirectoryExists(newPath)) {
                        currDirectory = newPath;
                    } else {
                        out.println("Directory not found: " + targetPath);
                    }
                }

                continue;
            }

            if (cmd.equalsIgnoreCase("mkdir")) {
                if (args.length != 1) {
                    out.println("Usage: mkdir <folder_name>");
                    continue;
                }
                String foldername = args[0];
                if (!foldername.matches("[0-9a-zA-Zа-яА-Я_-]+")) {
                    out.println("Error. Allowed characters: 0-9, a-z, A-Z, а-я, А-Я, '_', '-'");
                    continue;
                }
                fsm.syncWithVk();
                try {
                    fsm.addDirectory(currDirectory + foldername);
                    fsm.updateTopic();
                    out.println("Folder created.");
                } catch (FileSystemManager.DirectoryAlreadyExistsException e) {
                    out.println("Folder already exists.");
                }
                continue;
            }
            if (cmd.equalsIgnoreCase("rmdir")) {
                if (args.length != 1) {
                    out.println("Usage: rmdir <folder_name>");
                    continue;
                }
                String foldername = args[0];
                if (!foldername.matches("[0-9a-zA-Zа-яА-Я_-]+")) {
                    out.println("Error. Allowed characters: 0-9, a-z, A-Z, а-я, А-Я, '_', '-'");
                    continue;
                }
                fsm.syncWithVk();
                try {
                    fsm.deleteFolder(currDirectory + foldername);
                    fsm.updateTopic();

                    out.println("Loading");
                    vlm.deleteVideosFromFolder(currDirectory + foldername);
                    out.println("Files removed.");
                    continue;
                } catch (FileSystemManager.DirectoryNotFoundException e) {
                    out.println("Folder not found.");
                } catch (ClientException e) {
                    throw new RuntimeException(e);
                } catch (ApiException e) {
                    throw new RuntimeException(e);
                }
            }
            if (cmd.equalsIgnoreCase("rmfile")) {
                if (args.length != 1) {
                    out.println("Usage: rmfile <name>");
                    continue;
                }
                String filename = args[0];
                if (!filename.matches("[0-9a-zA-Zа-яА-Я._-]+")) {
                    out.println("Error. Allowed characters: 0-9, a-z, A-Z, а-я, А-Я, '_', '-', '.'");
                    continue;
                }
                fsm.syncWithVk();
                try {
                    fsm.deleteFile(currDirectory, filename);
                    fsm.updateTopic();
                    out.println("Loading");
                    fsm.deleteFile(currDirectory, filename);
                    out.println("File removed.");
                    continue;
                } catch (FileSystemManager.FileNotFoundException e) {
                    out.println("File not found.");
                    continue;
                } catch (FileSystemManager.DirectoryNotFoundException e) {
                    out.println("Directory not found.");
                    continue;
                }
            }
            if (cmd.equalsIgnoreCase("addvideo")) {
                if (args.length == 0) {
                    out.println("Usage: addvideo <path>");
                    continue;
                }

                File dir = new File(args[0]);
                List<File> lst = new ArrayList<File>();
                if (dir.isDirectory()) {
                    for (File file : Objects.requireNonNull(dir.listFiles())) {
                        if (file.isFile())
                            lst.add(file);
                    }
                } else {
                    lst.add(dir);
                }
                vlm.loadVideo(currDirectory, lst);
            }
            else {
                out.println("Command not found.");
            }
        }
    }

    public static FileSystemManager getFileSystemManager() {
        return fsm;
    }

    public static VideoLoaderManager getVideoLoaderManager() {
        return vlm;
    }
}