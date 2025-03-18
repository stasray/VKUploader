package ru.sanichik.utils;

import java.awt.*;
import java.net.URI;

public class Utils {
    private final static String FILE_NAME_PATTERN = "[0-9a-zA-Zа-яА-Я._ –-]+";

    public static boolean openWebpage(final URI uri) {
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(uri);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        if (!path.endsWith("/")) {
            path = path + "/";
        }

        return path;
    }

    public static boolean matchPattern(final String fileOrFolderName) {
        return fileOrFolderName.matches(FILE_NAME_PATTERN);
    }
}
