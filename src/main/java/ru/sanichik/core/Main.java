package ru.sanichik.core;

import ru.sanichik.auth.AuthManager;
import ru.sanichik.managers.CLIHandler;
import ru.sanichik.managers.FileSystemManager;
import ru.sanichik.managers.VideoLoaderManager;
import ru.sanichik.ui.MainWindow;

import javax.swing.*;
public class Main {

    public final static int MAX_CONCURRENT_UPLOADS = 5;
    public static String currDirectory = "/";
    private static FileSystemManager fsm = null;
    private static VideoLoaderManager vlm = null;

    public static void main(String[] args) {
        boolean gui = Boolean.parseBoolean(System.getenv("GUI"));

        AuthManager authManager = new AuthManager(gui);
        authManager.authenticate();

        fsm = authManager.getFileSystemManager();
        vlm = authManager.getVideoLoaderManager();

        if (gui) {
            startGUI();
        } else {
            startCLI();
        }
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
        CLIHandler cliHandler = new CLIHandler(fsm, vlm);
        cliHandler.startCLI();
    }

    public static FileSystemManager getFileSystemManager() {
        return fsm;
    }

    public static VideoLoaderManager getVideoLoaderManager() {
        return vlm;
    }
}