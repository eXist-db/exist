/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2020 The eXist-db Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.client.security;

import javax.swing.table.DefaultTableModel;
import org.exist.client.LabelledBoolean;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class BasicPermissionsTableModel extends DefaultTableModel {
    
    public BasicPermissionsTableModel(final ModeDisplay permission) {
        
        super(
            new Object [][] {
                new Object[] {"User",   permission.ownerRead, permission.ownerWrite, permission.ownerExecute, new LabelledBoolean("SetUID", permission.setUid)},
                new Object[] {"Group",  permission.groupRead, permission.groupWrite, permission.groupExecute, new LabelledBoolean("SetGID", permission.setGid)},
                new Object[] {"Other",  permission.otherRead, permission.otherWrite, permission.otherExecute, new LabelledBoolean("Sticky", permission.sticky)}
            },
            new String [] {
                "Permission", "Read", "Write", "Execute", "Special"
            }
        );
    }
           
    final Class[] types = new Class [] {
        java.lang.String.class, java.lang.Boolean.class, java.lang.Boolean.class, java.lang.Boolean.class, LabelledBoolean.class
    };

    final boolean[] canEdit = new boolean [] {
        false, true, true, true, true
    };

    @Override
    public Class getColumnClass(int columnIndex) {
        return types[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit[columnIndex];
    }

    @Override
    public void setValueAt(Object aValue, int row, int column) {
        super.setValueAt(aValue, row, column); //To change body of generated methods, choose Tools | Templates.
    }
    
    /**
     * Get the Mode described by the table model
     * 
     * @return The Unix mode of the permissions
     */
    public ModeDisplay getMode() {
        final ModeDisplay modeDisplay = new ModeDisplay();

        modeDisplay.ownerRead = (Boolean) getValueAt(0, 1);
        modeDisplay.ownerWrite = (Boolean) getValueAt(0, 2);
        modeDisplay.ownerExecute = (Boolean) getValueAt(0, 3);
        modeDisplay.setUid = ((LabelledBoolean)getValueAt(0, 4)).isSet();

        modeDisplay.groupRead = (Boolean) getValueAt(1, 1);
        modeDisplay.groupWrite = (Boolean) getValueAt(1, 2);
        modeDisplay.groupExecute = (Boolean) getValueAt(1, 3);
        modeDisplay.setGid = ((LabelledBoolean)getValueAt(1, 4)).isSet();

        modeDisplay.otherRead = (Boolean) getValueAt(2, 1);
        modeDisplay.otherWrite = (Boolean) getValueAt(2, 2);
        modeDisplay.otherExecute = (Boolean) getValueAt(2, 3);
        modeDisplay.sticky = ((LabelledBoolean)getValueAt(2, 4)).isSet();
        
        return modeDisplay;
    }
}
