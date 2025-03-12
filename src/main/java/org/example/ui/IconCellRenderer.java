package org.example.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class IconCellRenderer extends JLabel implements TableCellRenderer {
    private static final Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
    private static final Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
    private static final Icon upIcon = UIManager.getIcon("FileView.upFolderIcon");

    private DefaultTableModel tableModel;

    public IconCellRenderer(DefaultTableModel tableModel) {
        this.tableModel = tableModel;
        setOpaque(true);
        setFont(new Font("Arial", Font.PLAIN, 16));
        setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 10));
    }
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        setText(value.toString());


        if (column == 0) {
            String type = (String) tableModel.getValueAt(row, 1);
            if (type.equals("-")) {
                setIcon(upIcon);
            } else if ("Folder".equals(type)) {
                setIcon(folderIcon);
            } else {
                setIcon(fileIcon);
            }
        }

        setOpaque(true);
        setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
        return this;
    }
}
