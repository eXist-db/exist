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

import junit.framework.TestCase;
import org.exist.jetty.JettyStart;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.modules.CollectionManagementService;

/** An abstract wrapper for remote DB tests
 * @author Sebastian Bossung, Technische Universitaet Hamburg-Harburg
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
//TODO : manage content from here, not from the derived classes
public abstract class RemoteDBTest extends TestCase {
	
	protected static JettyStart server = null;
    // jetty.port.standalone
    protected final static String URI = "xmldb:exist://localhost:" + System.getProperty("jetty.port") + "/xmlrpc";
    private final static String CHILD_COLLECTION = "unit-testing-collection-Citt\u00E0";
    public final static String DB_DRIVER = "org.exist.xmldb.DatabaseImpl";

    private RemoteCollection collection = null;

    public RemoteDBTest(String name) {
        super(name);
    }

	protected void initServer() {
		try {
			if (server == null) {
				server = new JettyStart();
				if (!server.isStarted()) {
                    System.out.println("Starting standalone server...");
                    server.run();
                }
			}
        } catch (Exception e) {
        	e.printStackTrace();
            fail(e.getMessage()); 
        }
	}  		

    protected void setUpRemoteDatabase() {             
    	try {
	    	//Connect to the DB
	        Class<?> cl = Class.forName(DB_DRIVER);
	        Database database = (Database) cl.newInstance();
	        assertNotNull(database);
	        DatabaseManager.registerDatabase(database);
	        //Get the root collection...
	        Collection rootCollection = DatabaseManager.getCollection(URI + XmldbURI.ROOT_COLLECTION, "admin", "");
	        assertNotNull(rootCollection);  
            CollectionManagementService cms = (CollectionManagementService) rootCollection.getService(
                    "CollectionManagementService", "1.0");
            //Creates the child collection
            Collection childCollection = cms.createCollection(CHILD_COLLECTION);
            assertNotNull(childCollection);
            //... and work from it
            setCollection((RemoteCollection) childCollection);
            assertNotNull(childCollection);
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }	        
    }

    protected void removeCollection() {
    	try {
	        Collection rootCollection = DatabaseManager.getCollection(URI + XmldbURI.ROOT_COLLECTION, "admin", "");
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
