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

import org.exist.Server;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.RemoteCollection;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * @author Sebastian Bossung, Technische Universitaet Hamburg-Harburg
 */
public abstract class RemoteDBTest extends TestCase {
    protected final static String URI = "xmldb:exist://localhost:8081/exist/xmlrpc";

    private final static String COLLECTION_NAME = "unit-testing-collection";

    public final static String DB_DRIVER = "org.exist.xmldb.DatabaseImpl";

    private RemoteCollection collection = null;

    /**
     * @param name
     */
    public RemoteDBTest(String name) {
        super(name);
    }

    /**
     * @throws Exception
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws XMLDBException
     */
    protected void setUpRemoteDatabase() throws Exception, ClassNotFoundException, InstantiationException,
            IllegalAccessException, XMLDBException {
        startServer();

        Class cl = Class.forName(DB_DRIVER);
        Database database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);

        Collection rootCollection = DatabaseManager.getCollection(URI + "/db", "admin", null);

        Collection childCollection = rootCollection.getChildCollection(COLLECTION_NAME);
        if (childCollection == null) {
            CollectionManagementService cms = (CollectionManagementService) rootCollection.getService(
                    "CollectionManagementService", "1.0");
            setCollection((RemoteCollection) cms.createCollection(COLLECTION_NAME));
        } else {
            throw new Exception("Cannot run test because the collection /db/" + COLLECTION_NAME + " already "
                    + "exists. If it is a left-over of a previous test run, please remove it manually.");
        }
    }

    /**
     * @throws Exception
     */
    protected void startServer() throws Exception {
        String[] args = { "standalone" };
        //Server.main(args);
        // Thread ??
        Server.main(new String[] {});
        synchronized(this) {
        	wait(500);
        }
    }

    /**
     * @throws XMLDBException
     * @throws Exception
     */
    protected void removeCollection() throws XMLDBException, Exception {
        Collection rootCollection = DatabaseManager.getCollection(URI + "/db", "admin", null);
        CollectionManagementService cms = (CollectionManagementService) rootCollection.getService(
                "CollectionManagementService", "1.0");
        cms.removeCollection(COLLECTION_NAME);
    }

    protected void stopServer(Collection current) throws XMLDBException {
        DatabaseInstanceManager mgr = (DatabaseInstanceManager) current.getService("DatabaseInstanceManager", "1.0");
        if (mgr != null)
            mgr.shutdown();
        Server.shutdown();
        synchronized(this) {
        	try {
				wait(1000);
			} catch (InterruptedException e) {
			}
        }
    }

    /**
     * @return Returns the collection.
     */
    public RemoteCollection getCollection() {
        return collection;
    }

    /**
     * @param collection
     *                   The collection to set.
     */
    public void setCollection(RemoteCollection collection) {
        this.collection = collection;
    }

    protected String getTestCollectionName() {
        return COLLECTION_NAME;
    }
}