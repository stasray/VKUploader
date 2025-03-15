package org.example.managers;

import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class CredentialManager {
    private static final String CREDENTIALS_FILE = ".vk_credentials";

    public static void saveCredentials(String token, long groupId) {
        try (FileWriter writer = new FileWriter(CREDENTIALS_FILE)) {
            Properties properties = new Properties();
            properties.setProperty("token", token);
            properties.setProperty("groupId", String.valueOf(groupId));
            properties.store(writer, "VK Credentials");
        } catch (IOException e) {
            System.err.println("Error saving credentials: " + e.getMessage());
        }
    }

    public static String[] loadCredentials() {
        File file = new File(CREDENTIALS_FILE);
        if (!file.exists()) return null;

        try (FileReader reader = new FileReader(file)) {
            Properties properties = new Properties();
            properties.load(reader);
            String token = properties.getProperty("token");
            String groupId = properties.getProperty("groupId");
            return new String[]{token, groupId};
        } catch (IOException e) {
            System.err.println("Error loading credentials: " + e.getMessage());
        }
        return null;
    }

    public static boolean isTokenValid(FileSystemManager fsm) {
        try {
            fsm.syncWithVk();
            return true;
        } catch (ClientException | ApiException e) {
            return false;
        }
    }
}

