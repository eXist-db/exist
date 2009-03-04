/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2006 The eXist team
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
 *  
 *  $Id: BackupContentsFilter.java 6320 2007-08-01 18:01:06Z ellefj $
 */

package org.exist.client;

import java.io.File;

import javax.swing.filechooser.FileFilter;

public class ZipFilter extends FileFilter {
    public boolean accept(File f) {
        if (f.getName().toLowerCase().endsWith(".zip"))
            return true;
        if (f.isDirectory())
        	return true;
        return false;
    }
    
    public String getDescription() {
        return Messages.getString("ClientFrame.166"); 
    }
}
