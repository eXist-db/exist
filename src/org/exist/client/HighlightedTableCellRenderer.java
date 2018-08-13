/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
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

        final Color foreground;
        final Color background;
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
