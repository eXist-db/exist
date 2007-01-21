/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
package org.exist.performance.actions;

import org.w3c.dom.Element;
import org.exist.EXistException;
import org.exist.storage.TextSearchEngine;
import org.exist.xmldb.EXistResource;
import org.exist.util.DirectoryScanner;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.ProgressIndicator;
import org.exist.performance.Connection;
import org.exist.performance.Runner;
import org.exist.performance.AbstractAction;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.modules.CollectionManagementService;

import java.io.File;
import java.util.StringTokenizer;
import java.util.Observer;
import java.util.Observable;

public class StoreFromFile extends AbstractAction {

    private String collectionPath;
    private String dir;
    private String includes;
    private String mimeType = "text/xml";
    private boolean overwrite = true;

    public void configure(Runner runner, Action parent, Element config) throws EXistException {
        super.configure(runner, parent, config);
        if (!config.hasAttribute("collection"))
            throw new EXistException(StoreFromFile.class.getName() + " requires an attribute 'collection'");
        collectionPath = config.getAttribute("collection");
        if (!config.hasAttribute("dir"))
            throw new EXistException(StoreFromFile.class.getName() + " requires an attribute 'dir'");
        dir = config.getAttribute("dir");
        includes = config.getAttribute("includes");
        if (config.hasAttribute("mime-type"))
            mimeType = config.getAttribute("mime-type");
        overwrite = getBooleanValue(config, "overwrite", true);
    }

    public void execute(Connection connection) throws XMLDBException, EXistException {
        Collection collection = connection.getCollection(collectionPath);
        if (collection == null)
            throw new EXistException("collection " + collectionPath + " not found");
        ProgressObserver observer = new ProgressObserver();
        if (collection instanceof Observable)
            ((Observable)collection).addObserver(observer);
        String resourceType = getResourceType();
        File baseDir = new File(dir);
        File[] files = DirectoryScanner.scanDir(baseDir, includes);
        Collection col = collection;
        String relDir, prevDir = null;
        for(int j = 0; j < files.length; j++) {
            String relPath = files[j].toString().substring( baseDir.toString().length() );
            int p = relPath.lastIndexOf( File.separatorChar );
            relDir = relPath.substring( 0, p );
            relDir = relDir.replace(File.separatorChar, '/');
            if (prevDir == null || ( !relDir.equals(prevDir) ) ) {
                col = makeColl(collection, relDir);
                if (col instanceof Observable)
                    ((Observable)col).addObserver(observer);
                prevDir = relDir;
            }
            if (col.getResource(files[j].getName()) == null || overwrite) {
                //TODO  : these probably need to be encoded
                Resource resource =
                        col.createResource(files[j].getName(), resourceType);
                resource.setContent(files[j]);
                if("BinaryResource".equals(resourceType))
                    ((EXistResource)resource).setMimeType(mimeType);
                LOG.debug("Storing " + col.getName() + "/" + resource.getId());
                col.storeResource(resource);
            }
        }
    }

    private String getResourceType() {
        String resourceType = "XMLResource";
        MimeType mime = MimeTable.getInstance().getContentType(mimeType);
        if (mime != null)
            resourceType = mime.isXMLType() ? "XMLResource" : "BinaryResource";
        return resourceType;
    }

    private Collection makeColl(Collection parentColl, String relPath)
    throws XMLDBException {
        CollectionManagementService mgtService;
        Collection current = parentColl, c;
        String token;
        StringTokenizer tok = new StringTokenizer(relPath, "/");
        while (tok.hasMoreTokens()) {
            token = tok.nextToken();
            c = current.getChildCollection(token);
            if (c == null) {
                mgtService = (CollectionManagementService) current.getService("CollectionManagementService", "1.0");
                current = mgtService.createCollection(token);
            } else
                current = c;
        }
        return current;
    }

    private class ProgressObserver implements Observer {

        private long timestamp = System.currentTimeMillis();

        public void update(Observable o, Object arg) {
            if (System.currentTimeMillis() - timestamp > 20000) {
                ProgressIndicator ind = (ProgressIndicator) arg;
                if (!(o instanceof org.exist.storage.ElementIndex || o instanceof TextSearchEngine)) {
                    LOG.debug("Stored: " + (int)((ind.getValue() / ind.getMax()) * 100) + " %");
                }
                timestamp = System.currentTimeMillis();
            }
        }
    }
}
