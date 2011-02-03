/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
 *  $Id$
 */
package xquery;

import static org.junit.Assert.fail;

import java.io.File;

import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XQueryService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XMLResource;

public class TestRunnerMain {

	private static Collection rootCollection;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		init();
		
		runTests(args);
		
		shutdown();
	}

	private static void runTests(String[] files) {
		try {
			StringBuilder results = new StringBuilder();
			XQueryService xqs = (XQueryService) rootCollection.getService("XQueryService", "1.0");
			Source query = new FileSource(new File("test/src/xquery/runTests.xql"), "UTF-8", false);
			for (String fileName : files) {
				File file = new File(fileName);
				if (!file.canRead()) {
					System.console().printf("Test file not found: %s\n", fileName);
					return;
				}
				
				Document doc = TestRunner.parse(file);
				
				xqs.declareVariable("doc", doc);
				ResourceSet result = xqs.execute(query);
				XMLResource resource = (XMLResource) result.getResource(0);
                results.append(resource.getContent()).append('\n');
				Element root = (Element) resource.getContentAsDOM();
				NodeList tests = root.getElementsByTagName("test");
				for (int i = 0; i < tests.getLength(); i++) {
					Element test = (Element) tests.item(i);
					String passed = test.getAttribute("pass");
					if (passed.equals("false")) {
						System.err.println(resource.getContent());
						return;
					}
				}
			}
			System.out.println(results);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void init() {
		// initialize driver
		try {
			Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			
			rootCollection = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void shutdown() {
		if (rootCollection != null) {
			try {
				DatabaseInstanceManager dim =
				    (DatabaseInstanceManager) rootCollection.getService("DatabaseInstanceManager", "1.0");
				dim.shutdown();
			} catch (Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
	}
}
