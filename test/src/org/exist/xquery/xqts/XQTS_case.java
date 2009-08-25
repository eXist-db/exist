/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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
package org.exist.xquery.xqts;


import java.io.File;
import java.io.IOException;

import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.security.SecurityManager;
import org.exist.security.xacml.AccessContext;
import org.exist.source.FileSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XQTS_case extends XMLTestCase {

    public static Database database;
	
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
        try {
            // initialize driver
            Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);

            Collection root =
                    DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
            CollectionManagementService service =
                    (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            Collection testCollection = service.createCollection("test");
            assertNotNull(testCollection);
        } catch (Exception e) {
            e.printStackTrace();
        }
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
        // testCollection.removeResource( testCollection .getResource(file_name));
        TestUtils.cleanupDB();
        DatabaseInstanceManager dim =
                (DatabaseInstanceManager) DatabaseManager.getCollection("xmldb:exist:///db", "admin", null).getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
        DatabaseManager.deregisterDatabase(database);
        database = null;

        System.out.println("tearDown PASSED");
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}
	
	protected void groupCase(String testGroup, String testCase) {
		BrokerPool pool;
		try {
			pool = BrokerPool.getInstance();
		
			DBBroker broker = pool.get(SecurityManager.SYSTEM_USER);
        
			//execute an xquery
			XQuery service = broker.getXQueryService();
			assertNotNull(service);
        
			File buildFile = new File("webapp/xqts/xqts_test.xql");

			CompiledXQuery compiled = service.compile(service.newContext(AccessContext.TEST), new FileSource(buildFile, "UTF-8", true));
			assertNotNull(compiled);
        
//        compiled.
			Sequence result = service.execute(compiled, null);
			assertNotNull(result);
       
			assertEquals(0, result.getItemCount());
        
			System.out.println(result);
		} catch (EXistException e) {
			fail(e.toString());
		} catch (IOException e) {
			fail(e.toString());
		} catch (XPathException e) {
			fail(e.toString());
		}
	}


}
