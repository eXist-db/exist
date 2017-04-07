/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  $Id$
 */
package org.exist.xmldb;

import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.exist.util.FileUtils;
import org.exist.xmldb.concurrent.DBUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class StorageStressTest {

	@ClassRule
	public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true);

	//protected final static String getUri = "xmldb:exist://";
    public final static String DB_DRIVER = "org.exist.xmldb.DatabaseImpl";
    private final static String COLLECTION_NAME = "unit-testing-collection";
    
    @SuppressWarnings("unused")
	private final static String CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" + 
        "   <index xmlns:x=\"http://www.foo.com\" xmlns:xx=\"http://test.com\">" + 
        "       <create path=\"//ELEMENT-1/@attribute-1\" type=\"xs:string\"/>" +
        "   </index>" + 
        "</collection>";
    
    private Collection collection = null;

	protected static String getUri() {
		return "xmldb:exist://localhost:" + existWebServer.getPort() + "/xmlrpc";
	}

	@Test
    public void store() throws Exception {
		String[] wordList = DBUtils.wordList(collection);
		for (int i = 0; i < 30000; i++) {
			Path f = DBUtils.generateXMLFile(6, 3, wordList, false);
			Resource res = collection.createResource("test_" + i, "XMLResource");
			res.setContent(f);
			collection.storeResource(res);
			FileUtils.deleteQuietly(f);
		}
    }

	@Before
    public void setUp() throws ClassNotFoundException, InstantiationException, XMLDBException, IllegalAccessException {
        setUpRemoteDatabase();
    }
    
    protected void setUpRemoteDatabase() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
		Class<?> cl = Class.forName(DB_DRIVER);
		Database database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);

		Collection rootCollection = DatabaseManager.getCollection(getUri() + XmldbURI.ROOT_COLLECTION, "admin", "");

		Collection childCollection = rootCollection.getChildCollection(COLLECTION_NAME);
		if (childCollection == null) {
			CollectionManagementService cms = (CollectionManagementService) rootCollection.getService(
					"CollectionManagementService", "1.0");
			this.collection = cms.createCollection(COLLECTION_NAME);
		} else {
			this.collection = childCollection;
		}

		final Path f = TestUtils.resolveShakespeareSample("hamlet.xml");
		Resource res = collection.createResource("test1.xml", "XMLResource");
		res.setContent(f);
		collection.storeResource(res);

		@SuppressWarnings("unused")
		IndexQueryService idxConf = (IndexQueryService)
			collection.getService("IndexQueryService", "1.0");
//	        idxConf.configureCollection(CONFIG);
    }
}
