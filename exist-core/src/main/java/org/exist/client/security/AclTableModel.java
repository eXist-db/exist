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
import org.exist.security.ACLPermission;
import org.exist.security.Permission;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class AclTableModel extends DefaultTableModel {

    private final static String[] COLUMN_NAMES = new String [] {"Target", "Subject", "Access", "Read", "Write", "Execute"};

    public AclTableModel(final Permission permission) {
        super();
        
        final Object[][] aces;    
        if(permission instanceof ACLPermission) {
            final ACLPermission aclPermission = (ACLPermission)permission;
            aces = new Object[aclPermission.getACECount()][6];
            for(int i = 0; i < aclPermission.getACECount(); i++) {
                aces[i] = new Object[]{
                    aclPermission.getACETarget(i).toString(),
                    aclPermission.getACEWho(i),
                    aclPermission.getACEAccessType(i).toString(),
                    (aclPermission.getACEMode(i) & Permission.READ) == Permission.READ,
                    (aclPermission.getACEMode(i) & Permission.WRITE) == Permission.WRITE,
                    (aclPermission.getACEMode(i) & Permission.EXECUTE) == Permission.EXECUTE,
                };
            }
        } else {
            aces = new Object[0][6];
        }
        setDataVector(aces, COLUMN_NAMES);
    }
    
    final Class[] types = new Class [] {
        java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.Boolean.class, java.lang.Boolean.class, java.lang.Boolean.class
    };

    boolean[] canEdit = new boolean [] {
        false, false, false, true, true, true
    };

    @Override
    public Class getColumnClass(int columnIndex) {
        return types [columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return canEdit [columnIndex];
    }
}
