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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.exist.TestUtils;
import org.exist.util.FileUtils;
import org.exist.util.MimeTable;
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.concurrent.DBUtils;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

public class CreateCollectionAction extends Action {
    
    private int collectionCnt = 0;
    
    public CreateCollectionAction(final String collectionPath, final String resourceName) {
        super(collectionPath, resourceName);
    }

    @Override
    public boolean execute() throws XMLDBException, IOException {
        final Collection col = DatabaseManager.getCollection(collectionPath, "admin", "");
        final Collection target = DBUtils.addCollection(col, "C" + ++collectionCnt);
        addFiles(target);
        String resources[] = target.listResources();
        
        final EXistCollectionManagementService mgt = (EXistCollectionManagementService)
            col.getService("CollectionManagementService", "1.0");
        final Collection copy = DBUtils.addCollection(col, "CC" + collectionCnt);
        for (int i = 0; i < resources.length; i++) {
           mgt.copyResource(target.getName() + '/' + resources[i], 
                   copy.getName(), null);
        }

        resources = copy.listResources();
        return true;
    }

    private void addFiles(final Collection col) throws XMLDBException, IOException {
        final Path d = TestUtils.shakespeareSamples();
        if(!(Files.isReadable(d) && Files.isDirectory(d))) {
            throw new RuntimeException("Cannot read directory: " + d.toAbsolutePath());
        }

        final List<Path> files = FileUtils.list(d);
        for(final Path file : files) {
            if(Files.isRegularFile(file)) {
                if (MimeTable.getInstance().isXMLContent(FileUtils.fileName(file))) {
                    DBUtils.addXMLResource(col, FileUtils.fileName(file), file);
                }
            }
        }
    }
}
