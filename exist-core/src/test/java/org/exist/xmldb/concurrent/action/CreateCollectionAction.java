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
import java.io.InputStream;

import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.EXistCollectionManagementService;
import org.exist.xmldb.concurrent.DBUtils;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.samples.Samples.SAMPLES;

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
        for (final String sampleName : SAMPLES.getShakespeareXmlSampleNames()) {
            final String sample;
            try (final InputStream is = SAMPLES.getShakespeareSample(sampleName)) {
                sample = InputStreamUtil.readString(is, UTF_8);
            }
            DBUtils.addXMLResource(col, sampleName, sample);
        }
    }
}
