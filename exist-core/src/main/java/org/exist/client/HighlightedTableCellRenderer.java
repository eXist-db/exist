/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.client;

import org.exist.client.ClientFrame.ResourceTableModel;
import org.exist.xmldb.XmldbURI;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;

public class HighlightedTableCellRenderer<T extends AbstractTableModel> extends DefaultTableCellRenderer {
    
    private static final Color collectionBackground = new Color(225, 235, 224);
    private static final Color collectionForeground = Color.black;
    private static final Color highBackground = new Color(115, 130, 189);
    private static final Color highForeground = Color.white;
    private static final Color altBackground = new Color(235, 235, 235);
        
    /*
     * (non-Javadoc)
     *
     * @see javax.swing.table.TableCellRenderer#getTableCellRendererComponent(javax.swing.JTable,
     *           java.lang.Object, boolean, boolean, int, int)
     */
    @Override
    public Component getTableCellRendererComponent(final JTable table, Object value, final boolean isSelected, final boolean hasFocus, final int row, final int column) {
        if(value instanceof XmldbURI rI) {
            value = new PrettyXmldbURI(rI);
        }
        
        final Component renderer = super.getTableCellRendererComponent(table, value, isSelected,hasFocus, row, column);
        
        if(renderer instanceof JCheckBox box) {
            box.setOpaque(true);
        } else if(renderer instanceof JLabel label) {
            label.setOpaque(true);
        }

        final Color foreground;
        final Color background;
        final T resources = (T)table.getModel();
        if (isSelected) {
            foreground = highForeground;
            background = highBackground;
        } else if (resources instanceof ResourceTableModel model && model.getRow(row).isCollection()) {
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
