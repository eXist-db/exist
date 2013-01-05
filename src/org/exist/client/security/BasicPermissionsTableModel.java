/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
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

import javax.swing.table.DefaultTableModel;
import org.exist.security.Permission;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class BasicPermissionsTableModel extends DefaultTableModel {
    
    public BasicPermissionsTableModel(final Permission permission) {
        
        super(
            new Object [][] {
                new Object[] {"User", (permission.getOwnerMode() & Permission.READ) == Permission.READ, (permission.getOwnerMode() & Permission.WRITE) == Permission.WRITE, (permission.getOwnerMode() & Permission.EXECUTE) == Permission.EXECUTE},
                new Object[] {"Group", (permission.getGroupMode() & Permission.READ) == Permission.READ, (permission.getGroupMode() & Permission.WRITE) == Permission.WRITE, (permission.getGroupMode() & Permission.EXECUTE) == Permission.EXECUTE},
                new Object[] {"Other", (permission.getOtherMode() & Permission.READ) == Permission.READ, (permission.getOtherMode() & Permission.WRITE) == Permission.WRITE, (permission.getOtherMode() & Permission.EXECUTE) == Permission.EXECUTE}
            },
            new String [] {
                "Permission", "Read", "Write", "Execute"
            }
        );
    }
           
    final Class[] types = new Class [] {
        java.lang.String.class, java.lang.Boolean.class, java.lang.Boolean.class, java.lang.Boolean.class
    };

    boolean[] canEdit = new boolean [] {
        false, true, true, true
    };

    @Override
    public Class getColumnClass(int columnIndex) {
        return types [columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit [columnIndex];
    }
    
    /**
     * Get the Mode described by the table model
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
        
        return mode;
    }
}
