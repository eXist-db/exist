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

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
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
	
	@Ignore
	@Test
	public void testExport() throws SVNException {
		assertNotNull("Database wasn't initilised.", test);

		XmldbURI collectionURL = ((CollectionImpl) test).getPathURI();

		VersioningRepositoryImpl repository = new VersioningRepositoryImpl(collectionURL, url);

		for (Integer rev : Arrays.asList(currectRevList)) {
			if (rev < 4758) continue;
			
			System.out.println("update to rev "+rev);
			assertTrue("Updating failed to rev "+rev, repository.update(rev));
		}
	}

	static Collection testXML = null;

	// svn url
	private String urlXML = "https://exist.svn.sourceforge.net/svnroot/exist/trunk/eXist/webapp/xqts/";

    // revisions array
	private Integer[] currectRevListXML = {
			2763, 2764, 2765, 2771, 2772, 2773, 2774, 2775, 2780, 2784, 2785, 2805,
			2810, 2811, 3000, 3033, 3034, 3123, 3143, 3144, 3153, 3164, 3256, 3639,
			3651, 3652, 3654, 3699, 3700, 3759, 3778, 3958, 3968, 4034, 4035, 4036,
			4037, 4038, 4049, 4094, 4095, 4096, 4097, 4098, 4099, 4101, 4102, 4105,
			4108, 4119, 4120, 4211, 4255, 4308, 4309, 4346, 4386, 4412, 4414, 4415,
			4438, 4456, 4462, 4464, 4519, 4523, 4533, 4552, 4604, 4615, 4617, 4639,
			4671, 4693, 4698, 4748, 4749, 4758, 4779, 4860, 5441, 6348, 6352, 6367,
			6380, 6406, 6407, 6411, 6412, 6415, 6434, 6438, 6442, 6453, 6456, 6459,
			6460, 6463, 6465, 6468, 6469, 6482, 6494, 6495, 6535, 6539, 6540, 6546,
			6551, 6750, 6758, 6779, 6802, 6803, 6804, 6805, 6806, 6835, 6905, 6942,
			7160, 7237, 7453, 7575, 7594, 7596, 7620, 7623, 7721, 7943, 8359, 8766,
			9907, 10524
	};

	@Ignore
	@Test
	public void testExportXML() throws SVNException {
		assertNotNull("Database wasn't initilised.", testXML);

		XmldbURI collectionURL = ((CollectionImpl) testXML).getPathURI();

		VersioningRepositoryImpl repository = new VersioningRepositoryImpl(collectionURL, urlXML);

		for (Integer rev : Arrays.asList(currectRevListXML)) {
			if (rev < 4758) continue;
			
			System.out.println("update to rev "+rev);
			assertTrue("Updating failed to rev "+rev, repository.update(rev));
		}
	}
	
	private String createRepository() {
		HttpClient client = new HttpClient();

		PostMethod method = new PostMethod("http://support.syntactica.com/cgi-bin/3A075407-AC4E-3308-9616FD4EB832EDBB.pl");

		try {
			int statusCode = client.executeMethod(method);

			if (statusCode != HttpStatus.SC_OK) {
		        return null;
		    }
			
			return method.getResponseBodyAsString();
		} catch (Exception e) {
			return null;
		}
	}

	private void deleteRepository(String id) {
		HttpClient client = new HttpClient();

		PostMethod method = new PostMethod("http://support.syntactica.com/cgi-bin/938A1512-5156-11DF-A4C4-D82A2BCCFF1C.pl?d="+id);

		try {
			client.executeMethod(method);
		} catch (Exception e) {
		}
	}

	@Test
	public void testCommitXML() throws SVNException {
		assertNotNull("Database wasn't initilised.", testXML);

		String repositoryID = createRepository();
		assertNotNull(repositoryID);
		
		String repositoryURL = "https://support.syntactica.com/exist_svn/"+repositoryID+"/";

		XmldbURI collectionURL = ((CollectionImpl) testXML).getPathURI();

		VersioningRepositoryImpl repository = new VersioningRepositoryImpl(collectionURL, repositoryURL, "existtest", "existtest");

		deleteRepository(repositoryID);
	}

	@Ignore
	@Test
	public void testLog() throws SVNException {
		assertNotNull("Database wasn't initilised.", test);

		XmldbURI collectionURL = ((CollectionImpl) test).getPathURI();

		VersioningRepositoryImpl repository = new VersioningRepositoryImpl(collectionURL, url);

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

			try {
				mgmt.removeCollection("testXML");
			} catch (XMLDBException e) {
			}

			testXML = mgmt.createCollection("testXML");

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
