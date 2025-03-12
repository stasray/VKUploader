package org.example.objects;

public class FileNode {
    private final String name;
    private final boolean isDirectory;

    public FileNode(String name, boolean isDirectory) {
        this.name = name;
        this.isDirectory = isDirectory;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    @Override
    public String toString() {
        return name;
    }
}