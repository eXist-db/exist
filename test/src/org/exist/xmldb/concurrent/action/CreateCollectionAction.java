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

import org.exist.util.MimeTable;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.exist.xmldb.concurrent.DBUtils;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

public class CreateCollectionAction extends Action {

    private final static String DIR_PATH = "samples" + File.separatorChar + "shakespeare";
    
    private int collectionCnt = 0;
    
    public CreateCollectionAction(String collectionPath, String resourceName) {
        super(collectionPath, resourceName);
    }
    
    public boolean execute() throws Exception {
        Collection col = DatabaseManager.getCollection(collectionPath, "admin", null);
        Collection target = DBUtils.addCollection(col, "C" + ++collectionCnt);
        addFiles(target);
        System.out.println("Resources in collection " + target.getName());
        String resources[] = target.listResources();
        for (int i = 0; i < resources.length; i++) {
            System.out.println(resources[i]);
        }
        
        CollectionManagementServiceImpl mgt = (CollectionManagementServiceImpl)
            col.getService("CollectionManagementService", "1.0");
        Collection copy = DBUtils.addCollection(col, "CC" + collectionCnt);
        for (int i = 0; i < resources.length; i++) {
           mgt.copyResource(target.getName() + '/' + resources[i], 
                   copy.getName(), null);
        }
        
        System.out.println("Resources in collection " + copy.getName());
        resources = copy.listResources();
        for (int i = 0; i < resources.length; i++) {
            System.out.println(resources[i]);
        }
        return false;
    }

    private void addFiles(Collection col) throws XMLDBException {
        File d = new File(DIR_PATH);
        if(!(d.canRead() && d.isDirectory()))
            throw new RuntimeException("Cannot read directory: " + DIR_PATH);
        File[] files = d.listFiles();
        for(int i = 0; i < files.length; i++) {
            if(files[i].isFile()) {
                if (MimeTable.getInstance().isXMLContent(files[i].getName()))
                    DBUtils.addXMLResource(col, files[i].getName(), files[i]);
            }
        }
    }
}
