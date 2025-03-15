package org.example.ui;


import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import org.example.Utils;
import org.example.exceptions.DirectoryAlreadyExistsException;
import org.example.managers.FileSystemManager;
import org.example.managers.VideoLoaderManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainWindow extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JPanel panelMain;
    private JLabel currentDirectoryLabel;
    private static JTable fileTable;
    private static DefaultTableModel tableModel;
    private final FileSystemManager fsm;
    private final VideoLoaderManager vlm;
    private static String currentDirectory = "/";

    public MainWindow(FileSystemManager fsm, VideoLoaderManager vlm) {
        this.fsm = fsm;
        this.vlm = vlm;
        $$$setupUI$$$();
        setContentPane(contentPane);
        setModal(true);

        buttonOK.setContentAreaFilled(false);
        buttonOK.setBorderPainted(false);
        buttonOK.setFocusPainted(false);
        buttonOK.setOpaque(false);
        buttonOK.addActionListener(e -> onAddFolder());


        buttonCancel.setContentAreaFilled(false);
        buttonCancel.setBorderPainted(false);
        buttonCancel.setFocusPainted(false);
        buttonCancel.setOpaque(false);
        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onAddVideo();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onAddVideo();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onAddVideo();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        createFileSystem();
    }

    private void createFileSystem() {
        String[] columnNames = {"Name", "Type", "Action"}; // TODO: rename, delete, get link (if video)
        tableModel = new DefaultTableModel(columnNames, 0);
        fileTable = new JTable(tableModel);

        fileTable.setBackground(new Color(42, 42, 58));
        fileTable.setForeground(new Color(176, 176, 176));
        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.setDefaultEditor(Object.class, null);
        fileTable.setBorder(null);

        for (int i = 0; i < fileTable.getColumnModel().getColumnCount(); i++) {
            fileTable.getColumnModel().getColumn(i).setCellRenderer(new IconCellRenderer(tableModel));
        }
        fileTable.getColumnModel().getColumn(2).setCellRenderer(new ActionButtonRenderer());
        fileTable.getColumnModel().getColumn(2).setCellEditor(new ActionButtonEditor(
                new JCheckBox(),
                vlm,
                fsm));

        fileTable.setShowGrid(false);
        fileTable.setBorder(BorderFactory.createEmptyBorder());
        fileTable.setFocusable(false);
        fileTable.setShowVerticalLines(false);
        fileTable.setShowHorizontalLines(false);
        fileTable.setRowHeight(40);

        JTableHeader header = fileTable.getTableHeader();
        header.setFont(new Font("Arial", Font.BOLD, 16));
        header.setBackground(new Color(42, 42, 58));
        header.setForeground(new Color(176, 176, 176));
        header.setOpaque(true);
        header.setPreferredSize(new Dimension(header.getWidth(), 40));


        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = fileTable.getSelectedRow();
                int column = fileTable.getSelectedColumn();

                if (row == -1 || column == -1) {
                    return;
                }

                String fileName = (String) tableModel.getValueAt(row, 0);
                String fileType = (String) tableModel.getValueAt(row, 1);

                if (fileName.equals("...")) {
                    if (!currentDirectory.equals("/")) {
                        currentDirectory = currentDirectory.substring(0, currentDirectory.lastIndexOf('/'));
                        int lastSlashIndex = currentDirectory.lastIndexOf('/');
                        if (lastSlashIndex > 0) {
                            currentDirectory = currentDirectory.substring(0, lastSlashIndex);
                            currentDirectory = Utils.normalizePath(currentDirectory);
                        } else {
                            currentDirectory = "/";
                        }
                    }
                    refreshTable();
                }

                if (fileType.equals("Folder")) {
                    currentDirectory = currentDirectory + fileName + "/";
                    refreshTable();
                }
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(fileTable);
        tableScrollPane.setBorder(BorderFactory.createEmptyBorder());
        tableScrollPane.getViewport().setBackground(new Color(42, 42, 48));
        panelMain.setLayout(new BorderLayout());
        panelMain.add(tableScrollPane, BorderLayout.CENTER);
        refreshTable();
        startRefresher();
    }

    private void startRefresher() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::refreshTable, 0, 3000, TimeUnit.MILLISECONDS);
    }

    private void refreshTable() {
        try {
            currentDirectoryLabel.setText(currentDirectory);
            tableModel.setRowCount(0);

            Set<String> folders = fsm.getFolders(currentDirectory);
            List<String> files = fsm.getFiles(currentDirectory);

            if (!currentDirectory.equals("/")) {
                tableModel.addRow(new Object[]{"...", "-", "-"});
            }

            for (String folder : folders) {

                String s = folder.substring(currentDirectory.length());

                int count = 0;
                for (char element : s.toCharArray()) {
                    if (element == '/') count++;
                }
                if (count <= 1) {
                    s = s.substring(0, s.indexOf("/"));
                    tableModel.addRow(new Object[]{s, "Folder", ""});
                }
            }

            for (String file : files) {
                tableModel.addRow(new Object[]{file.replaceAll("/", ""), "File", ""});
            }
            Set<Map.Entry<String, Integer>> progresses = vlm.getProgresses(currentDirectory).entrySet();
            for (Map.Entry<String, Integer> entry : progresses) {
                if (entry.getValue() > 0 && entry.getValue() < 100) {
                    tableModel.addRow(new Object[]{entry.getKey(), "Uploading: " + entry.getValue() + "%", ""});
                } else if (entry.getValue() == 0) {
                    tableModel.addRow(new Object[]{entry.getKey(), "Waiting...", ""});
                } else if (entry.getValue() == -1) {
                    tableModel.addRow(new Object[]{entry.getKey(), "Upload failed!", ""});
                }

            }
        } catch (Exception e) {
            e.printStackTrace(); // Можно обработать исключения
        }
    }

    private void onAddFolder() {
        String folderName = JOptionPane.showInputDialog(null, "Enter folder name:", "Create Folder", JOptionPane.QUESTION_MESSAGE);

        if (folderName != null) {
            if (folderName.matches("[0-9a-zA-Zа-яА-Я_-]+")) {
                try {
                    fsm.addDirectory(currentDirectory + folderName);
                    tableModel.addRow(new Object[]{folderName, "Folder", ""});
                    fsm.updateTopic();
                } catch (DirectoryAlreadyExistsException e) {
                    JOptionPane.showMessageDialog(null, "Directory already exists.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    throw new RuntimeException(e);
                } catch (ClientException e) {
                    JOptionPane.showMessageDialog(null, "Connection error", "Check your internet connection and try again.", JOptionPane.ERROR_MESSAGE);
                } catch (ApiException e) {
                    JOptionPane.showMessageDialog(null, "Api error", "Check your token and try again.", JOptionPane.ERROR_MESSAGE);
                }

            } else {
                JOptionPane.showMessageDialog(null, "Invalid folder name! Use only letters, numbers, '_', and '-'.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            System.out.println("Folder creation canceled.");
        }
    }


    private void onAddVideo() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Video File(s)");
        fileChooser.setMultiSelectionEnabled(true); // Разрешаем выбирать несколько файлов

        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Video Files (*.mp4, *.avi, *.mkv, *.mov, *.wmv, *.flv, *.webm, *.mpeg, *.3gp, *.ogg)",
                "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "mpeg", "3gp", "ogg"
        );
        fileChooser.setFileFilter(filter);

        int returnValue = fileChooser.showOpenDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            vlm.loadVideo(currentDirectory, Arrays.stream(selectedFiles).toList(), fsm);
        } else {
            System.out.println("File selection canceled.");
        }
    }

    public static String getCurrentDirectory() {
        return currentDirectory;
    }

    public static void main(String[] args) {
        MainWindow dialog = new MainWindow(null, null);
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        contentPane = new JPanel();
        contentPane.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(4, 1, new Insets(10, 10, 10, 10), -1, -1));
        contentPane.setBackground(new Color(-13750715));
        contentPane.setPreferredSize(new Dimension(1000, 600));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel1.setBackground(new Color(-14803410));
        panel1.setForeground(new Color(-15592942));
        contentPane.add(panel1, new com.intellij.uiDesigner.core.GridConstraints(3, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel2.setAutoscrolls(true);
        panel2.setBackground(new Color(-14013894));
        panel1.add(panel2, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setAlignmentX(0.0f);
        buttonOK.setBorderPainted(false);
        buttonOK.setContentAreaFilled(false);
        buttonOK.setFocusPainted(false);
        buttonOK.setFocusable(true);
        buttonOK.setIcon(new ImageIcon(getClass().getResource("/icons/mkdir.png")));
        buttonOK.setMaximumSize(new Dimension(48, 48));
        buttonOK.setMinimumSize(new Dimension(48, 48));
        buttonOK.setPreferredSize(new Dimension(48, 48));
        buttonOK.setText("");
        panel2.add(buttonOK);
        buttonCancel = new JButton();
        buttonCancel.setBorderPainted(false);
        buttonCancel.setContentAreaFilled(false);
        buttonCancel.setFocusPainted(false);
        buttonCancel.setIcon(new ImageIcon(getClass().getResource("/icons/video.png")));
        buttonCancel.setPreferredSize(new Dimension(48, 48));
        buttonCancel.setText("");
        panel2.add(buttonCancel);
        final JLabel label1 = new JLabel();
        label1.setBackground(new Color(-6908477));
        Font label1Font = this.$$$getFont$$$("Monospaced", Font.PLAIN, 24, label1.getFont());
        if (label1Font != null) label1.setFont(label1Font);
        label1.setForeground(new Color(-6908476));
        label1.setIcon(new ImageIcon(getClass().getResource("/icons/vk.png")));
        label1.setText("VideoUploader");
        contentPane.add(label1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        panelMain = new JPanel();
        panelMain.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panelMain.setBackground(new Color(-14013894));
        contentPane.add(panelMain, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panelMain.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        panelMain.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel3.setBackground(new Color(-14013894));
        contentPane.add(panel3, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel3.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        currentDirectoryLabel = new JLabel();
        currentDirectoryLabel.setForeground(new Color(-5197648));
        currentDirectoryLabel.setText("Label");
        panel3.add(currentDirectoryLabel, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST, com.intellij.uiDesigner.core.GridConstraints.FILL_NONE, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
