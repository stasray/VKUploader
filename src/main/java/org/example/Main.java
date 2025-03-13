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
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.System.out;

public class Main {

    private static long groupId = 0L;
    private static boolean gui = true;
    private static String token = "---";
    public static int MAX_CONCURRENT_UPLOADS = 5;

    private static String currDirectory = "/";

    private static FileSystemManager fsm = null;
    private static VideoLoaderManager vlm = null;

    public static void main(String[] serverargs) {

        try {
            MAX_CONCURRENT_UPLOADS = Integer.parseInt(System.getenv("MAX_CONCURRENT_UPLOADS"));
        } catch (NumberFormatException e) {
            System.out.println("Error! max_concurrent cannot be lower than 1. Using default value: 5");
            MAX_CONCURRENT_UPLOADS = 5;
        }

        gui = false; //Boolean.parseBoolean(System.getenv("GUI"));

        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);

        if (gui) {
            System.setProperty("apple.awt.application.appearance", "NSAppearanceNameVibrantDark");
            SwingUtilities.invokeLater(() -> {
                String authUrl = showAuthDialog("https://oauth.vk.com/authorize?client_id=52502099&display=page&redirect_uri=https://oauth.vk.com/blank.html&scope=friends,video,groups&response_type=token&v=5.59");

                if (authUrl == null || authUrl.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Error: link is not entered!", "Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }

                if (authUrl.contains("access_token=")) {
                    token = authUrl.substring(authUrl.indexOf("access_token=") + 13);
                    if (token.contains("&")) {
                        token = token.substring(0, token.indexOf("&"));
                    }
                }

                String groupIdS = JOptionPane.showInputDialog(
                        null,
                        "Input group ID:",
                        "Group ID",
                        JOptionPane.QUESTION_MESSAGE
                );

                if (groupIdS == null || groupIdS.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(null, "Group ID not entered", "Errpr", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
                try {
                    groupId = Long.parseLong(groupIdS);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(null, "Group ID is incorrect", "Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }

                UserActor actor = new UserActor(198248840L, token);
                fsm = new FileSystemManager(vk, actor, groupId);
                vlm = new VideoLoaderManager(vk, actor, groupId);
                try {
                    fsm.syncWithVk();
                } catch (ClientException e) {
                    JOptionPane.showMessageDialog(null, "Connection error. Check internet connection", "Error", JOptionPane.ERROR_MESSAGE);
                    throw new RuntimeException(e);
                } catch (ApiException e) {
                    JOptionPane.showMessageDialog(null, "Token is invalid", "Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                    throw new RuntimeException(e);
                }

                MainWindow dialog = new MainWindow(fsm, vlm);
                dialog.pack();
                dialog.setLocationRelativeTo(null);
                dialog.setVisible(true);
                System.exit(0);
            });
            return;
        }

        Scanner scanner = new Scanner(System.in);

        System.out.println("Authorize by visiting the following link and paste the redirected URL here:");
        System.out.println("https://oauth.vk.com/authorize?client_id=52502099&display=page&redirect_uri=https://oauth.vk.com/blank.html&scope=friends,video,groups&response_type=token&v=5.59");
        System.out.print("Enter the URL: ");

        String authUrl = scanner.nextLine().trim();

        if (authUrl.isEmpty()) {
            System.err.println("Error: link is not entered!");
            System.exit(1);
        }

        String token = null;
        if (authUrl.contains("access_token=")) {
            token = authUrl.substring(authUrl.indexOf("access_token=") + 13);
            if (token.contains("&")) {
                token = token.substring(0, token.indexOf("&"));
            }
        }

        if (token == null || token.isEmpty()) {
            System.err.println("Error: Invalid token!");
            System.exit(1);
        }

        System.out.print("Enter Group ID: ");
        String groupIdS = scanner.nextLine().trim();

        if (groupIdS.isEmpty()) {
            System.err.println("Error: Group ID not entered!");
            System.exit(1);
        }

        long groupId;
        try {
            groupId = Long.parseLong(groupIdS);
        } catch (NumberFormatException e) {
            System.err.println("Error: Group ID is incorrect!");
            System.exit(1);
            return;
        }

        UserActor actor = new UserActor(198248840L, token);
        FileSystemManager fsm = new FileSystemManager(vk, actor, groupId);
        VideoLoaderManager vlm = new VideoLoaderManager(vk, actor, groupId);

        try {
            fsm.syncWithVk();
        } catch (ClientException e) {
            System.err.println("Error: Connection error. Check your internet connection.");
            System.exit(1);
        } catch (ApiException e) {
            System.err.println("Error: Token is invalid.");
            System.exit(1);
        }

        System.out.println("VK Video Uploader CLI started. Type 'help' for available commands.");


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
                try {
                    fsm.syncWithVk();
                } catch (ClientException e) {
                    out.println("Error! Can't sync with VK! Check internet connection.");
                    continue;
                } catch (ApiException e) {
                    out.println("Error! Can't sync with VK! Check token expire.");
                    continue;
                }
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
                try {
                    fsm.syncWithVk();
                } catch (ClientException e) {
                    out.println("Error! Can't sync with VK! Check internet connection.");
                    continue;
                } catch (ApiException e) {
                    out.println("Error! Can't sync with VK! Check token expire.");
                    continue;
                }
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
                try {
                    fsm.syncWithVk();
                } catch (ClientException e) {
                    out.println("Error! Can't sync with VK! Check internet connection.");
                    continue;
                } catch (ApiException e) {
                    out.println("Error! Can't sync with VK! Check token expire.");
                    continue;
                }
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
                try {
                    fsm.syncWithVk();
                } catch (ClientException e) {
                    out.println("Error! Can't sync with VK! Check internet connection.");
                    continue;
                } catch (ApiException e) {
                    out.println("Error! Can't sync with VK! Check token expire.");
                    continue;
                }
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
                try {
                    fsm.syncWithVk();
                } catch (ClientException e) {
                    out.println("Error! Can't sync with VK! Check internet connection.");
                    continue;
                } catch (ApiException e) {
                    out.println("Error! Can't sync with VK! Check token expire.");
                    continue;
                }
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

    private static String showAuthDialog(String authLink) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel("<html>Login with <a href='" + authLink + "'>link</a> and insert link from browser:</html>");
        JTextField textField = new JTextField(30);
        JButton copyButton = new JButton("Copy link");

        copyButton.addActionListener(e -> {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(authLink), null);
            JOptionPane.showMessageDialog(null, "Link copied!", "Done", JOptionPane.INFORMATION_MESSAGE);
        });

        panel.add(label, BorderLayout.NORTH);
        panel.add(textField, BorderLayout.CENTER);
        panel.add(copyButton, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(null, panel, "Authentication", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return textField.getText();
        }
        return null;
    }

    public static FileSystemManager getFileSystemManager() {
        return fsm;
    }

    public static VideoLoaderManager getVideoLoaderManager() {
        return vlm;
    }
}