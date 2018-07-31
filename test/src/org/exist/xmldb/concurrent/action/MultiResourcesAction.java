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
import java.nio.file.Paths;
import java.util.List;

import org.exist.util.FileUtils;
import org.exist.xmldb.concurrent.DBUtils;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 */
public class MultiResourcesAction extends Action {
    
    private final String dirPath;

    public MultiResourcesAction(final String dirPath, final String collectionPath) {
        super(collectionPath, "");
        this.dirPath = dirPath;
    }

    @Override
    public boolean execute() throws XMLDBException, IOException {
        final Collection col = DatabaseManager.getCollection(collectionPath, "admin", "");
        addFiles(col);
        return true;
    }

    private void addFiles(final Collection col) throws XMLDBException, IOException {
        final Path d = Paths.get(dirPath);
        if(!(Files.isReadable(d) && Files.isDirectory(d))) {
            throw new RuntimeException("Cannot read directory: " + dirPath);
        }

        final List<Path> files = FileUtils.list(d);
        for(final Path file : files) {
            if(Files.isRegularFile(file)) {
                DBUtils.addXMLResource(col, FileUtils.fileName(file), file);
            }
        }
    }
}
