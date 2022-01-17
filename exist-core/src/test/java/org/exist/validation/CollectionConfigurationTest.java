/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 * $Id: CollectionConfigurationValidationModeTest.java 6709 2007-10-12 20:58:52Z dizzzz $
 */
package org.exist.validation;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.junit.Test;

import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.CollectionManagementService;

import static org.junit.Assert.*;

/**
 *  Some tests regarding invalid collection.xconf documents.
 * 
 * @author wessels
 */
public class CollectionConfigurationTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String invalidConfig = "<invalid/>";

    private void createCollection(String collection) throws XMLDBException {
        final CollectionManagementService cmservice = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        Collection testCollection = cmservice.createCollection(collection);
        assertNotNull(testCollection);

        testCollection = cmservice.createCollection("/db/system/config" + collection);
        assertNotNull(testCollection);
    }

    private void storeCollectionXconf(String collection, String document) throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("xmldb:store(\"" + collection + "\", \"collection.xconf\", " + document + ")");
        String r = (String) result.getResource(0).getContent();
        assertEquals("Store xconf", collection + "/collection.xconf", r);
    }


    @Test
    public void insertInvalidCollectionXconf() throws XMLDBException {
        createCollection("/db/system/config/db/foobar");
        storeCollectionXconf("/db/system/config/db/foobar", invalidConfig);


        createCollection("/db/system/config/db/foobar");
        storeCollectionXconf("/db/system/config/db/foobar", invalidConfig);

    }
}
