package org.example.ui;

import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.example.Utils;
import org.example.exceptions.DirectoryNotFoundException;
import org.example.exceptions.FileNotFoundException;
import org.example.managers.FileSystemAlbumsManager;
import org.example.managers.FileSystemManager;
import org.example.managers.FileSystemTopicsManager;
import org.example.managers.VideoLoaderManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.net.URI;

class ActionButtonEditor extends DefaultCellEditor {
    private JPanel panel;
    private JTable table;
    private int row;
    private VideoLoaderManager vlm;

    private FileSystemManager fsm;

    private static Icon loadIcon(String path) {
        java.net.URL imgURL = ActionButtonRenderer.class.getResource(path);
        if (imgURL != null) {
            return new ImageIcon(imgURL);
        } else {
            System.err.println("Не удалось загрузить иконку: " + path);
            return new ImageIcon(); // пустая иконка
        }
    }

    private static final Icon deleteIcon = loadIcon("/icons/delete.png");
    private static final Icon infoIcon = loadIcon("/icons/info.png");
    private static final Icon linkIcon = loadIcon("/icons/link.png");

    public ActionButtonEditor(JCheckBox checkBox, VideoLoaderManager vlm, FileSystemManager fsm) {
        super(checkBox);
        this.vlm = vlm;
        this.fsm = fsm;
        panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        this.table = table;
        this.row = row;

        panel.removeAll();

        if (!"-".equals(table.getValueAt(row, 1))) {
            panel.add(createButton(linkIcon, e -> linkAction()));
            panel.add(createButton(deleteIcon, e -> deleteAction()));
        }
        if ("File".equals(table.getValueAt(row, 1))) {
            panel.add(createButton(infoIcon, e -> infoAction()));
        }

        return panel;
    }

    private JButton createButton(Icon icon, ActionListener action) {
        JButton button = new JButton(icon);
        button.setPreferredSize(new Dimension(25, 25));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.addActionListener(action);
        return button;
    }

    private void deleteAction() {
        String video = table.getValueAt(row, 0).toString();
        int choice = JOptionPane.showConfirmDialog(
                null,
                "Remove " + video + "?",
                "Are you sure?",
                JOptionPane.YES_NO_OPTION
        );

        if (choice == JOptionPane.YES_OPTION) {
            System.out.println("Deleting " + MainWindow.getCurrentDirectory() + video);
            if ("Folder".equals(table.getValueAt(row, 1).toString())) {
                try {
                    fsm.deleteFolder(video);
                    fsm.updateTopic();
                    ((DefaultTableModel) table.getModel()).removeRow(row);
                    table.clearSelection();
                    table.editingStopped(null);
                } catch (DirectoryNotFoundException e) {
                    showMessageDialog("Error", "Folder not found:\n" + e.getMessage(), JOptionPane.ERROR_MESSAGE);
                } catch (ClientException e) {
                    showMessageDialog("Connection error", "Check your internet connection and try again.", JOptionPane.ERROR_MESSAGE);
                } catch (ApiException e) {
                    showMessageDialog("Api error", "Check your token and try again.", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                try {
                    fsm.deleteFile(MainWindow.getCurrentDirectory(), video);
                    fsm.updateTopic();
                    vlm.deleteVideo(MainWindow.getCurrentDirectory(), video, fsm);
                } catch (FileNotFoundException | DirectoryNotFoundException | ClientException | ApiException e) {
                    showMessageDialog("Error", "Error while removing video:\n" + e.getMessage(), JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            System.out.println("Deletion canceled.");
        }
    }

    private void infoAction() {
        try {
            String meta = vlm.getVideoMetadata(MainWindow.getCurrentDirectory(), table.getValueAt(row, 0).toString(), fsm);
            showMessageDialog("Video meta:\n", meta, JOptionPane.INFORMATION_MESSAGE);
        } catch (ClientException | ApiException e) {
            showMessageDialog("Error", "Error while loading meta:\n" + e.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showMessageDialog(String title, String message, int messageType) {
        JOptionPane.showMessageDialog(null, message, title, messageType);
    }

    private void linkAction() {
        if (table.getValueAt(row, 1).toString().equals("Folder")) {
            if (!(fsm instanceof FileSystemAlbumsManager fsma)) return;
            String url = fsma.getAlbumLink(MainWindow.getCurrentDirectory() + table.getValueAt(row, 0).toString());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(url), null);
            return;
        }
        try {
            URI url = vlm.getVideoLink(MainWindow.getCurrentDirectory(), table.getValueAt(row, 0).toString());
            Utils.openWebpage(url);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(url.toString()), null);
        } catch (ClientException | ApiException e) {
            showMessageDialog("Error", "Video link not found:\n" + e.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
        System.out.println("Link " + row);
    }
}

