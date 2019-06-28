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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;
import javax.swing.AbstractCellEditor;
import javax.swing.JCheckBox;
import javax.swing.SwingConstants;

/**
 * Editor for a LabelledBoolean using a JCheckBox
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class LabelledBooleanEditor extends AbstractCellEditor implements TableCellEditor {

    private LabelledBoolean current;
    
    @Override
    public Object getCellEditorValue() {
        return current;
    }

    @Override
    public Component getTableCellEditorComponent(final JTable table, final Object value, final boolean isSelected, final int row, final int column) {
        final LabelledBoolean lb = (LabelledBoolean)value;
        final JCheckBox chkBox = new JCheckBox(lb.getLabel(), lb.isSet());
        
        chkBox.setHorizontalAlignment(SwingConstants.LEFT);
        chkBox.setHorizontalTextPosition(SwingConstants.RIGHT);
        
        chkBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                current = lb.copy(!lb.isSet());
                fireEditingStopped(); //notify that editing is done!
            }
        });
        
        return chkBox;
    }
}
