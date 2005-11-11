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
package org.exist.xmldb.test;

import junit.framework.TestCase;

import org.exist.storage.DBBroker;
import org.exist.xmldb.RemoteCollection;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/** An abstract wrapper for remote DB tests
 * @author Sebastian Bossung, Technische Universitaet Hamburg-Harburg
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
//TODO : manage content from here, not from the derived classes
public abstract class RemoteDBTest extends TestCase {
	
    protected final static String URI = "xmldb:exist://localhost:8088/xmlrpc";
    private final static String COLLECTION_NAME = "unit-testing-collection-Citt\u00E0";
    public final static String DB_DRIVER = "org.exist.xmldb.DatabaseImpl";

    private RemoteCollection collection = null;

    public RemoteDBTest(String name) {
        super(name);
    }

    protected void setUpRemoteDatabase() {             
    	try {
	    	//Connect to the DB
	        Class cl = Class.forName(DB_DRIVER);
	        Database database = (Database) cl.newInstance();
	        assertNotNull(database);
	        DatabaseManager.registerDatabase(database);
	        //Get the root collection...
	        Collection rootCollection = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION, "admin", null);
	        assertNotNull(rootCollection);
	        //... and work from it
	        Collection childCollection = rootCollection.getChildCollection(COLLECTION_NAME);
	        assertNotNull(childCollection);
            CollectionManagementService cms = (CollectionManagementService) rootCollection.getService(
                    "CollectionManagementService", "1.0");
            setCollection((RemoteCollection) cms.createCollection(COLLECTION_NAME));
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }	        
    }

    protected void removeCollection() {
    	try {
	        Collection rootCollection = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION, "admin", null);
	        assertNotNull(rootCollection);
	        CollectionManagementService cms = (CollectionManagementService) rootCollection.getService(
	                "CollectionManagementService", "1.0");
	        cms.removeCollection(COLLECTION_NAME);
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
        return COLLECTION_NAME;
    }
}