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
package org.exist.versioning.svn;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.exist.storage.DBBroker;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class SVNExportTest {

	static Collection test = null;

    // svn url
	private String url = "https://exist.svn.sourceforge.net/svnroot/exist/trunk/eXist/webapp/admin/";

    // revisions array
	private Integer[] currectRevList = {
			699, 764, 768, 782, 788, 797, 805, 832, 896, 936, 1008, 1104, 
			1105, 1211, 1317, 1335, 1338, 1685, 2460, 2489, 2558, 2878,
			3123, 3489, 3505, 3524, 3525, 3532, 3535, 3764, 4121, 4255,
			4497, 4598, 4671, 4758, 5052, 5054, 5066, 5084, 5085, 5086,
			5088, 5093, 6005, 6434, 6533, 6739, 6750, 6751, 6752, 7159,
			7209, 7224, 7879, 7893, 8074, 8207, 8236, 8237, 8453, 8471,
			8482, 8490, 8512, 8539, 8551, 8553, 8554, 8560, 8639, 8839,
			8973, 9019, 9020, 9055, 9127, 9167, 9222, 9234, 9235, 9238,
			9242, 9247, 9323, 9358, 9561, 9859, 9929, 9937, 9942, 9943,
			9944, 9985, 9992, 10012, 10289, 10297, 10302, 10304, 10341,
			10401, 10433, 10532, 10551
	};
	
	
	@Test
	public void testExport() throws SVNException {
		assertNotNull("Database wasn't initilised.", test);

		VersioningRepositoryImpl repository = new VersioningRepositoryImpl();

		XmldbURI collectionURL = ((CollectionImpl) test).getPathURI();

		assertTrue("Can't connect to svn repository.", 
				repository.connect(collectionURL, url));

		for (Integer rev : Arrays.asList(currectRevList)) {
			if (rev < 4758) continue;
			
			System.out.println("update to rev "+rev);
			assertTrue("Updating failed to rev "+rev, repository.update(rev));
		}
	}

	@Ignore
	@Test
	public void testLog() throws SVNException {
		assertNotNull("Database wasn't initilised.", test);

		VersioningRepositoryImpl repository = new VersioningRepositoryImpl();

		XmldbURI collectionURL = ((CollectionImpl) test).getPathURI();

		assertTrue("Can't connect to svn repository.", 
				repository.connect(collectionURL, url));

		java.util.Collection<SVNLogEntry> logEntries = repository.log(new String[] { "" },
				null, 0, -1, false, false);

		List<Integer> revList = new LinkedList<Integer>();
		
		for (Iterator<SVNLogEntry> entries = logEntries.iterator(); entries.hasNext();) {
			SVNLogEntry logEntry = entries.next();
//			System.out.println("---------------------------------------------");
//			System.out.println("revision: " + logEntry.getRevision());
//			System.out.println("author: " + logEntry.getAuthor());
//			System.out.println("date: " + logEntry.getDate());
//			System.out.println("log message: " + logEntry.getMessage());

			revList.add(Integer.valueOf(String.valueOf(logEntry.getRevision())));
		}

		assertArrayEquals(currectRevList, revList.toArray());
	}

	@BeforeClass
	public static void initDB() {
		// initialize XML:DB driver
		try {
			Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);

			org.xmldb.api.base.Collection root = DatabaseManager.getCollection(
					"xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
			CollectionManagementService mgmt = (CollectionManagementService) root
					.getService("CollectionManagementService", "1.0");
			try {
				mgmt.removeCollection("test");
			} catch (XMLDBException e) {
			}

			test = mgmt.createCollection("test");

		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@AfterClass
	public static void closeDB() {
		try {
			Collection root = DatabaseManager.getCollection("xmldb:exist://"
					+ DBBroker.ROOT_COLLECTION, "admin", null);
			// CollectionManagementService cmgr = (CollectionManagementService)
			// root.getService("CollectionManagementService", "1.0");
			// cmgr.removeCollection("test");

			DatabaseInstanceManager mgr = (DatabaseInstanceManager) root
					.getService("DatabaseInstanceManager", "1.0");
			mgr.shutdown(2);
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
}
