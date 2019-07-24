/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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

import java.awt.Component;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;
import javax.swing.table.TableCellRenderer;

/**
 * Renders a LabelledBoolean as a JCheckBox
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class LabelledBooleanRenderer extends JCheckBox
        implements TableCellRenderer, UIResource {
    
    private static final Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);

    public LabelledBooleanRenderer() {
        super();
        setHorizontalAlignment(SwingConstants.LEFT);
        setBorderPainted(true);
        setHorizontalTextPosition(SwingConstants.RIGHT);
    }

    @Override
    public Component getTableCellRendererComponent(final JTable table, final Object value, 
            final boolean isSelected, final boolean hasFocus, final int row, final int column) {
        
        if(isSelected) {
            setForeground(table.getSelectionForeground());
            super.setBackground(table.getSelectionBackground());
        } else {
            setForeground(table.getForeground());
            setBackground(table.getBackground());
        }
        
        //set selected
        setSelected(value != null && ((LabelledBoolean)value).isSet());
        
        //set label
        if(value != null) {
            setText(((LabelledBoolean)value).getLabel());
        }

        if (hasFocus) {
            setBorder(UIManager.getBorder("Table.focusCellHighlightBorder"));
        } else {
            setBorder(noFocusBorder);
        }

        return this;
    }
}
