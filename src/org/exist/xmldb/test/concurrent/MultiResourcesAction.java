/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xmldb.test.concurrent;

import java.io.File;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;

/**
 * @author wolf
 */
public class MultiResourcesAction extends Action {

    private final static String dirPath = "samples/mods";
    
    /**
     * @param collectionPath
     * @param resourceName
     */
    public MultiResourcesAction(String collectionPath) {
        super(collectionPath, "");
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.test.concurrent.Action#execute()
     */
    public boolean execute() throws Exception {
        File d = new File(dirPath);
        if(!(d.canRead() && d.isDirectory()))
            throw new RuntimeException("Cannot read directory: " + dirPath);
        File[] files = d.listFiles();
        
        Collection col = DatabaseManager.getCollection(collectionPath, "admin", null);
        for(int i = 0; i < files.length; i++) {
            if(files[i].isFile())
                DBUtils.addXMLResource(col, files[i].getName(), files[i]);
        }
        return false;
    }

}
