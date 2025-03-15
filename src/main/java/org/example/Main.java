package org.example;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import org.example.exceptions.DirectoryAlreadyExistsException;
import org.example.exceptions.DirectoryNotFoundException;
import org.example.exceptions.FileNotFoundException;
import org.example.managers.*;
import org.example.managers.FileSystemManager;
import org.example.ui.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.*;
import java.util.List;

import static java.lang.System.out;

public class Main {

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

        boolean gui = true; //Boolean.parseBoolean(System.getenv("GUI"));

        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);

        String[] credentials = CredentialManager.loadCredentials();
        String token = (credentials != null) ? credentials[0] : null;
        long groupId = (credentials != null) ? Long.parseLong(credentials[1]) : -1;
        UserActor actor = null;

        while (true) {
            if (token == null || groupId <= 0) {
                if (gui) {
                    token = authenticateWithGUI();
                } else {
                    token = authenticateWithCLI();
                }
                if (token == null) {
                    out.println("Token is invalid.");
                    continue;
                }

                if (gui) {
                    groupId = requestGroupIdWithGUI();
                } else {
                    groupId = requestGroupId();
                }

                actor = new UserActor(198248840L, token);
                fsm = new FileSystemAlbumsManager(vk, actor, groupId);
                vlm = new VideoLoaderManager(vk, actor, groupId);

                CredentialManager.saveCredentials(token, groupId);
            } else {
                actor = new UserActor(198248840L, token);
                fsm = new FileSystemAlbumsManager(vk, actor, groupId);
                vlm = new VideoLoaderManager(vk, actor, groupId);
            }
            if (CredentialManager.isTokenValid(fsm)) {
                break;
            } else {
                token = null;
                groupId = 0L;
            }
        }

        if (gui) {
            System.setProperty("apple.awt.application.appearance", "NSAppearanceNameVibrantDark");
            startGUI();
        } else {
            startCLI();
        }
    }

    private static String authenticateWithGUI() {
        String authUrl = "https://oauth.vk.com/authorize?client_id=52502099&display=page&redirect_uri=https://oauth.vk.com/blank.html&scope=friends,video,groups&response_type=token&v=5.59";
        String userInput = showAuthDialog(authUrl);
        return extractToken(userInput);
    }

    private static String authenticateWithCLI() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Authorize by visiting the following link and paste the redirected URL here:");
        System.out.println("https://oauth.vk.com/authorize?client_id=52502099&display=page&redirect_uri=https://oauth.vk.com/blank.html&scope=friends,video,groups&response_type=token&v=5.59");
        System.out.print("Enter the URL: ");
        String authUrl = scanner.nextLine().trim();
        return extractToken(authUrl);
    }

    private static long requestGroupId() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter Group ID: ");
        while (true) {
            try {
                return Long.parseLong(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.print("Invalid ID. Enter again: ");
            }
        }
    }

    private static long requestGroupIdWithGUI() {
        while (true) {
            String input = JOptionPane.showInputDialog(null, "Enter Group ID:", "Group ID", JOptionPane.QUESTION_MESSAGE);

            if (input == null) {
                JOptionPane.showMessageDialog(null, "Group ID entering is canceled", "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }

            try {
                return Long.parseLong(input.trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "ID is incorrect.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static String extractToken(String authUrl) {
        if (authUrl.contains("access_token=")) {
            String token = authUrl.substring(authUrl.indexOf("access_token=") + 13);
            if (token.contains("&")) {
                token = token.substring(0, token.indexOf("&"));
            }
            return token;
        }
        return null;
    }

    private static void startGUI() {
        SwingUtilities.invokeLater(() -> {
            MainWindow dialog = new MainWindow(fsm, vlm);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
            System.exit(0);
        });
    }

    private static void startCLI() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("VK Video Uploader CLI started. Type 'help' for available commands.");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;

            String[] cmdfull = input.split(" ");
            String cmd = cmdfull[0];
            String[] args = Arrays.copyOfRange(cmdfull, 1, cmdfull.length);

            if (cmd.equalsIgnoreCase("exit")) {
                System.out.println("VKVideoUploader stopped.");
                break;
            }

            if (cmd.equalsIgnoreCase("help")) {
                out.println("Commands:");
                out.println(" exit - stop server");
                out.println(" sync - sync with VK");
                out.println(" ls - check files and subfolders in current folder. (-s for sync)");
                out.println(" mkdir <name> - create folder");
                out.println(" cd <path> - go to path folder");
                out.println(" rmdir <name> - remove folder with all files and subfolders. WARNING! It will remove all video files in folder and subfolders!");
                out.println(" rmfile <name> - remove file in current directory.");
                out.println(" addvideo <path> - export videos from local path to VK. It can be folder with files or single file. Use -meta to save meta to comment.");
                out.println(" setcredentials <token> <groupId> - change credentials");
                continue;
            }
            if (cmd.equalsIgnoreCase("setcredentials")) {
                if (args.length < 2) {
                    System.out.println("Usage: setcredentials <token> <groupId>");
                } else {
                    String token = args[0];
                    long groupId;
                    try {
                        groupId = Long.parseLong(args[1]);
                        CredentialManager.saveCredentials(token, groupId);
                        System.out.println("Credentials updated successfully. Restart the application.");
                        System.exit(0);
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid groupId format. It should be a number.");
                    }
                }
                continue;
            }
            if (cmd.equalsIgnoreCase("ls")) {
                if (input.contains("-sync") || input.contains("-s")) {
                    try {
                        fsm.syncWithVk();
                    } catch (ClientException e) {
                        out.println("Error! Can't sync with VK! Check internet connection.");
                        continue;
                    } catch (ApiException e) {
                        out.println("Error! Can't sync with VK! Check token expire.");
                        continue;
                    }
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

                    newPath = Utils.normalizePath(newPath);

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
                } catch (DirectoryAlreadyExistsException e) {
                    out.println("Folder already exists.");
                } catch (ClientException e) {
                    out.println("Error! Can't sync with VK! Check internet connection.");
                } catch (ApiException e) {
                    out.println("Error! Can't sync with VK! Check token expire.");
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
                } catch (DirectoryNotFoundException e) {
                    out.println("Folder not found.");
                } catch (ClientException e) {
                    out.println("Error! Can't sync with VK! Check internet connection.");
                } catch (ApiException e) {
                    out.println("Error! Can't sync with VK! Check token expire.");
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
                } catch (FileNotFoundException e) {
                    out.println("File not found.");
                    continue;
                } catch (DirectoryNotFoundException e) {
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
                vlm.loadVideo(currDirectory, lst, fsm);
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
        return (result == JOptionPane.OK_OPTION) ? textField.getText() : null;
    }

    public static FileSystemManager getFileSystemManager() {
        return fsm;
    }

    public static VideoLoaderManager getVideoLoaderManager() {
        return vlm;
    }
}