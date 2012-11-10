package org.exist.client;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import org.exist.client.ClientFrame.ResourceTableModel;
import org.exist.xmldb.XmldbURI;

public class HighlightedTableCellRenderer<T extends AbstractTableModel> extends DefaultTableCellRenderer {
    
    private final static Color collectionBackground = new Color(225, 235, 224);
    private final static Color collectionForeground = Color.black;
    private final static Color highBackground = new Color(115, 130, 189);
    private final static Color highForeground = Color.white;
    private final static Color altBackground = new Color(235, 235, 235);
        
    /*
     * (non-Javadoc)
     *
     * @see javax.swing.table.TableCellRenderer#getTableCellRendererComponent(javax.swing.JTable,
     *           java.lang.Object, boolean, boolean, int, int)
     */
    @Override
    public Component getTableCellRendererComponent(final JTable table, Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
        if(value instanceof XmldbURI) {
            value = new PrettyXmldbURI((XmldbURI)value);
        }
        
        final Component renderer = super.getTableCellRendererComponent(table, value, isSelected,hasFocus, row, column);
        
        if(renderer instanceof JCheckBox) {
            ((JCheckBox)renderer).setOpaque(true);
        } else if(renderer instanceof JLabel) {
            ((JLabel)renderer).setOpaque(true);
        }

        final Color foreground, background;
        final T resources = (T)table.getModel();
        if (isSelected) {
            foreground = highForeground;
            background = highBackground;
        } else if (resources instanceof ResourceTableModel && ((ResourceTableModel)resources).getRow(row).isCollection()) {
            foreground = collectionForeground;
            background = collectionBackground;
        } else if (row % 2 == 0) {
            background = altBackground;
            foreground = Color.black;
        } else {
            foreground = Color.black;
            background = Color.white;
        }

        renderer.setForeground(foreground);
        renderer.setBackground(background);
        
        return renderer;
    }
}
