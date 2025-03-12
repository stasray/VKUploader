package org.example.ui;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

class ActionButtonRenderer extends JPanel implements TableCellRenderer {
    private static Icon loadIcon(String path) {
        java.net.URL imgURL = ActionButtonRenderer.class.getResource(path);
        if (imgURL != null) {
            return new ImageIcon(imgURL);
        } else {
            System.err.println("Cannot load icon: " + path);
            return new ImageIcon();
        }
    }

    private static final Icon deleteIcon = loadIcon("/icons/delete.png");
    private static final Icon infoIcon = loadIcon("/icons/info.png");
    private static final Icon linkIcon = loadIcon("/icons/link.png");
    public ActionButtonRenderer() {
        setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        removeAll();

        if (!"-".equals(table.getValueAt(row, 1)))
            add(createButton(deleteIcon));
        if ("File".equals(table.getValueAt(row, 1))) {
            add(createButton(infoIcon));
            add(createButton(linkIcon));
        }

        setOpaque(true);
        setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());

        return this;
    }

    private JButton createButton(Icon icon) {
        JButton button = new JButton(icon);
        button.setPreferredSize(new Dimension(25, 25));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        return button;
    }
}