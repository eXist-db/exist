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
package org.exist.client.security;

import javax.swing.JCheckBox;
import javax.swing.table.DefaultTableModel;
import org.exist.client.LabelledBoolean;
import org.exist.security.Permission;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class BasicPermissionsTableModel extends DefaultTableModel {
    
    public BasicPermissionsTableModel(final Permission permission) {
        
        super(
            new Object [][] {
                new Object[] {"User", (permission.getOwnerMode() & Permission.READ) == Permission.READ, (permission.getOwnerMode() & Permission.WRITE) == Permission.WRITE, (permission.getOwnerMode() & Permission.EXECUTE) == Permission.EXECUTE, new LabelledBoolean("SetUID", permission.isSetUid())},
                new Object[] {"Group", (permission.getGroupMode() & Permission.READ) == Permission.READ, (permission.getGroupMode() & Permission.WRITE) == Permission.WRITE, (permission.getGroupMode() & Permission.EXECUTE) == Permission.EXECUTE, new LabelledBoolean("SetGID", permission.isSetGid())},
                new Object[] {"Other", (permission.getOtherMode() & Permission.READ) == Permission.READ, (permission.getOtherMode() & Permission.WRITE) == Permission.WRITE, (permission.getOtherMode() & Permission.EXECUTE) == Permission.EXECUTE, new LabelledBoolean("Sticky", permission.isSticky())}
            },
            new String [] {
                "Permission", "Read", "Write", "Execute", "Special"
            }
        );
    }
           
    final Class[] types = new Class [] {
        java.lang.String.class, java.lang.Boolean.class, java.lang.Boolean.class, java.lang.Boolean.class, LabelledBoolean.class
    };

    boolean[] canEdit = new boolean [] {
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
    public int getMode() {
        int mode = 0;
        for(int i = 0; i < getRowCount(); i++) {
            if((Boolean)getValueAt(i, 1)) {
                mode |= Permission.READ;
            }
            if((Boolean)getValueAt(i, 2)) {
                mode |= Permission.WRITE;
            }
            if((Boolean)getValueAt(i, 3)) {
                mode |= Permission.EXECUTE;
            }
            
            if(i != getRowCount() - 1) {
                mode <<= 3;
            }
        }
        
        if(((LabelledBoolean)getValueAt(0, 4)).isSet()) {
            mode |= (Permission.SET_UID << 9);
        }
        if(((LabelledBoolean)getValueAt(1, 4)).isSet()) {
            mode |= (Permission.SET_GID << 9);
        }
        if(((LabelledBoolean)getValueAt(2, 4)).isSet()) {
            mode |= (Permission.STICKY << 9);
        }
        
        return mode;
    }
}
