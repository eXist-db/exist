/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 */
package org.exist.xmldb;

import org.exist.test.ExistWebServer;
import org.junit.ClassRule;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/** An abstract wrapper for remote DB tests
 * @author <a href="mailto:pierrick.brihaye@free.fr">Sebastian Bossung, Technische Universitaet Hamburg-Harburg
 * @author Pierrick Brihaye</a>
 */
//TODO : manage content from here, not from the derived classes
public abstract class RemoteDBTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private final static String CHILD_COLLECTION = "unit-testing-collection-Citt\u00E0";
    public final static String DB_DRIVER = "org.exist.xmldb.DatabaseImpl";

    private RemoteCollection collection = null;

    public static String getUri() {
        return "xmldb:exist://localhost:" + existWebServer.getPort() + "/xmlrpc";
    }

    protected void setUpRemoteDatabase() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        //Connect to the DB
        Class<?> cl = Class.forName(DB_DRIVER);
        Database database = (Database) cl.newInstance();
        assertNotNull(database);
        DatabaseManager.registerDatabase(database);
        //Get the root collection...
        Collection rootCollection = DatabaseManager.getCollection(getUri() + XmldbURI.ROOT_COLLECTION, "admin", "");
        assertNotNull(rootCollection);
        CollectionManagementService cms = (CollectionManagementService) rootCollection.getService(
                "CollectionManagementService", "1.0");
        //Creates the child collection
        Collection childCollection = cms.createCollection(CHILD_COLLECTION);
        assertNotNull(childCollection);
        //... and work from it
        setCollection((RemoteCollection) childCollection);
        assertNotNull(childCollection);
    }

    protected void removeCollection() {
    	try {
	        Collection rootCollection = DatabaseManager.getCollection(getUri() + XmldbURI.ROOT_COLLECTION, "admin", "");
	        assertNotNull(rootCollection);
	        CollectionManagementService cms = (CollectionManagementService) rootCollection.getService(
	                "CollectionManagementService", "1.0");
	        cms.removeCollection(CHILD_COLLECTION);
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }
    }

    public RemoteCollection getCollection() {
        return collection;
    }

    public void setCollection(RemoteCollection collection) {
        this.collection = collection;
    }

    protected String getTestCollectionName() {
        return CHILD_COLLECTION;
    }
}
