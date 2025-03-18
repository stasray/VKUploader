package ru.sanichik.managers;

import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import ru.sanichik.exceptions.DirectoryAlreadyExistsException;
import ru.sanichik.exceptions.DirectoryNotFoundException;
import ru.sanichik.exceptions.FileNotFoundException;
import ru.sanichik.objects.VideoObject;
import ru.sanichik.utils.Utils;
import ru.sanichik.core.Main;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.Logger;

public class CLIHandler {

    private final FileSystemManager fsm;
    private final VideoLoaderManager vlm;
    private static final Logger logger = Logger.getLogger(CLIHandler.class.getName());

    public CLIHandler(FileSystemManager fsm, VideoLoaderManager vlm) {
        this.fsm = fsm;
        this.vlm = vlm;
    }

    public void startCLI() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("VK Video Uploader CLI started. Type 'help' for available commands.");

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) continue;

                String[] cmdfull = input.split(" ");
                String cmd = cmdfull[0];
                String[] args = Arrays.copyOfRange(cmdfull, 1, cmdfull.length);

                switch (cmd.toLowerCase()) {
                    case "exit" -> {
                        System.out.println("VKVideoUploader stopped.");
                        return;
                    }
                    case "help" -> printHelp();
                    case "ls" -> listFiles(args);
                    case "sync" -> syncWithVK();
                    case "mkdir" -> createDirectory(args);
                    case "rmdir" -> removeDirectory(args);
                    case "rmfile" -> removeFile(args);
                    case "addvideo" -> addVideo(args);
                    case "cd" -> changeDirectory(args);
                    default -> System.out.println("Command not found.");
                }
            }
        }
    }

    public void printHelp() {
        System.out.println("Commands:");
        System.out.println("  exit - stop server");
        System.out.println("  sync - sync with VK");
        System.out.println("  ls - list files and folders");
        System.out.println("  mkdir <name> - create folder");
        System.out.println("  cd <path> - change directory");
        System.out.println("  rmdir <name> - remove folder");
        System.out.println("  rmfile <name> - remove file");
        System.out.println("  addvideo <path> - upload video");
    }

    private void listFiles(String[] args) {
        if (args.length > 0 && (args[0].equals("-s") || args[0].equals("-sync"))) {
            syncWithVK();
        }
        System.out.println("Files and folders in " + Main.currDirectory);
        fsm.getFolders(Main.currDirectory).forEach(logger::info);
        fsm.getFiles(Main.currDirectory).forEach(v -> System.out.println(v.getTitle()));
    }

    private void syncWithVK() {
        try {
            fsm.syncWithVk();
        } catch (ClientException | ApiException e) {
            logger.severe("Error! Can't sync with VK! Check internet connection or token.");
        }
    }

    private void createDirectory(String[] args) {
        if (args.length != 1 || !Utils.matchPattern(args[0])) {
            logger.warning("Invalid folder name.");
            return;
        }
        try {
            fsm.addDirectory(Main.currDirectory + args[0]);
            System.out.println("Folder created.");
        } catch (DirectoryAlreadyExistsException e) {
            logger.warning("Folder already exists.");
        } catch (ClientException | ApiException e) {
            logger.severe("Error! Check internet connection or token.");
        }
    }

    private void removeDirectory(String[] args) {
        if (args.length != 1) {
            logger.warning("Usage: rmdir <folder_name>");
            return;
        }
        try {
            fsm.deleteFolder(Main.currDirectory + args[0]);
            vlm.deleteVideosFromFolder(Main.currDirectory + args[0]);
            System.out.println("Folder removed.");
        } catch (DirectoryNotFoundException e) {
            logger.warning("Folder not found.");
        } catch (ClientException | ApiException e) {
            logger.severe("Error! Check internet connection or token.");
        }
    }

    private void removeFile(String[] args) {
        if (args.length != 1) {
            logger.warning("Usage: rmfile <name>");
            return;
        }
        try {
            VideoObject video = fsm.getVideo(Main.currDirectory, args[0]);
            vlm.deleteVideo(video);
            fsm.deleteFile(Main.currDirectory, video);
            System.out.println("File removed.");
        } catch (FileNotFoundException e) {
            logger.warning("File not found.");
        } catch (DirectoryNotFoundException e) {
            logger.warning("Directory not found.");
        } catch (ClientException | ApiException e) {
            logger.severe("Error! Check internet connection or token.");
        }
    }

    private void addVideo(String[] args) {
        if (args.length == 0) {
            logger.warning("Usage: addvideo <path>");
            return;
        }
        File file = new File(args[0]);
        List<File> files = file.isDirectory() ? Arrays.asList(Objects.requireNonNull(file.listFiles(File::isFile))) : List.of(file);
        vlm.loadVideo(Main.currDirectory, files, fsm);
        //todo: check if selected is video
    }

    private void changeDirectory(String[] args) {
        if (args.length != 1) {
            logger.warning("Usage: cd <path>");
            return;
        }
        String newPath = args[0].equals("..") ? Main.currDirectory.substring(0, Main.currDirectory.lastIndexOf("/")) : Main.currDirectory + args[0];
        newPath = Utils.normalizePath(newPath);
        if (fsm.isDirectoryExists(newPath)) {
            Main.currDirectory = newPath;
        } else {
            logger.warning("Directory not found.");
        }
    }
}
