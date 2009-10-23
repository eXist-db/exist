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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id:$
 */
package org.exist.debugger;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.tools.ant.taskdefs.Sleep;
import org.exist.storage.DBBroker;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.DatabaseInstanceManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DebuggerTest {
	
	static Collection test = null;
	
	@Test
	public void testDebugger() {
		assertNotNull("Database wasn't initilised.", database);
		
		Debugger debugger;
		
		try {
			debugger = new DebuggerImpl();

			DebuggingSource source = debugger.init("http://127.0.0.1:8080/exist/admin/admin.xql");

			assertNotNull("Debugging source can't be NULL.", source);
			
			try { //why???
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			
			source.run();

		} catch (IOException e) {
			assertNotNull("IO exception: "+e.getMessage(), null);
		} catch (ExceptionTimeout e) {
			assertNotNull("Timeout exception: "+e.getMessage(), null);
		}
	}

	static org.exist.start.Main database;

	@BeforeClass
    public static void initDB() {
//        // initialize XML:DB driver
//        try {
//            Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
//            Database database = (Database) cl.newInstance();
//            database.setProperty("create-database", "true");
//            DatabaseManager.registerDatabase(database);
//
//            org.xmldb.api.base.Collection root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
//            CollectionManagementService mgmt = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
//            test = mgmt.createCollection("test");
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            fail(e.getMessage());
//        }
		database = new org.exist.start.Main("jetty");
		database.run(new String[]{"jetty"});
    }

    @AfterClass
    public static void closeDB() {
//        try {
//            Collection root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
//            CollectionManagementService cmgr = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
//            cmgr.removeCollection("test");
//
//            DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
//            mgr.shutdown();
//        } catch (XMLDBException e) {
//            e.printStackTrace();
//            fail(e.getMessage());
//        }
       	database.shutdown();
    }
}
