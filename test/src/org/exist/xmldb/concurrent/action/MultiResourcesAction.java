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
package org.exist.xmldb.concurrent.action;

import java.io.File;

import org.exist.xmldb.concurrent.DBUtils;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 */
public class MultiResourcesAction extends Action {
    
    private String dirPath;
    
    /**
     * 
     * 
     * @param dirPath 
     * @param collectionPath 
     */
    public MultiResourcesAction(String dirPath, String collectionPath) {
        super(collectionPath, "");
        this.dirPath = dirPath;
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.test.concurrent.Action#execute()
     */
    public boolean execute() throws Exception {
        Collection col = DatabaseManager.getCollection(collectionPath, "admin", null);
        addFiles(col);
        return false;
    }

	/**
	 * @param files
	 * @param col
	 * @throws XMLDBException
	 */
	private void addFiles(Collection col) throws XMLDBException {
		File d = new File(dirPath);
        if(!(d.canRead() && d.isDirectory()))
            throw new RuntimeException("Cannot read directory: " + dirPath);
        File[] files = d.listFiles();
		for(int i = 0; i < files.length; i++) {
            if(files[i].isFile()) {
            	DBUtils.addXMLResource(col, files[i].getName(), files[i]);
            }
        }
	}

}
