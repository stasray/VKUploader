package ru.sanichik.auth;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import ru.sanichik.managers.FileSystemAlbumsManager;
import ru.sanichik.managers.FileSystemManager;
import ru.sanichik.managers.VideoLoaderManager;
import ru.sanichik.utils.CredentialManager;

import javax.swing.*;
import java.util.Scanner;
import java.util.logging.Logger;
public class AuthManager {
    private final boolean gui;
    private FileSystemManager fsm;
    private VideoLoaderManager vlm;
    private UserActor actor;
    private static final Logger logger = Logger.getLogger(AuthManager.class.getName());

    public AuthManager(boolean gui) {
        this.gui = gui;
    }

    public void authenticate() {
        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);

        String[] credentials = CredentialManager.loadCredentials();
        String token = (credentials != null) ? credentials[0] : null;
        long groupId = (credentials != null) ? Long.parseLong(credentials[1]) : -1;

        while (true) {
            if (token == null || groupId <= 0) {
                token = gui ? authenticateWithGUI() : authenticateWithCLI();
                if (token == null) {
                    logger.warning("Token is invalid.");
                    continue;
                }
                groupId = gui ? requestGroupIdWithGUI() : requestGroupId();
            }

            actor = new UserActor(198248840L, token);
            fsm = new FileSystemAlbumsManager(vk, actor, groupId);
            vlm = new VideoLoaderManager(vk, actor, groupId);

            if (CredentialManager.isTokenValid(fsm)) {
                CredentialManager.saveCredentials(token, groupId);
                break;
            } else {
                logger.warning("Token validation failed. Requesting new credentials.");
                token = null;
                groupId = -1;
            }
        }
    }


    private String authenticateWithGUI() {
        return extractToken(JOptionPane.showInputDialog("Enter VK token:"));
    }

    private String authenticateWithCLI() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter VK token: ");
        return extractToken(scanner.nextLine().trim());
    }

    private long requestGroupId() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter Group ID: ");
        return Long.parseLong(scanner.nextLine().trim());
    }

    private long requestGroupIdWithGUI() {
        return Long.parseLong(JOptionPane.showInputDialog("Enter Group ID:"));
    }

    public FileSystemManager getFileSystemManager() {
        return fsm;
    }

    public VideoLoaderManager getVideoLoaderManager() {
        return vlm;
    }

    public static String extractToken(String authUrl) {
        if (authUrl.contains("access_token=")) {
            String token = authUrl.substring(authUrl.indexOf("access_token=") + 13);
            if (token.contains("&")) {
                token = token.substring(0, token.indexOf("&"));
            }
            return token;
        }
        return null;
    }
}
