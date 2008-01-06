package org.exist.examples.xmldb;

import java.io.File;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XQueryService;
import org.exist.xupdate.XUpdateProcessor;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 *  Retrieve a document from the database. To run this example enter:
 * 
 *  bin/run.sh org.exist.examples.xmldb.MultipleDatabases 
 * 
 *  in the root directory of the distribution.
 *
 *	@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class MultipleDatabases {

	protected static String driver = "org.exist.xmldb.DatabaseImpl";

	protected static String URI_DB1 = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
	protected static String URI_DB2 = "xmldb:test://" + DBBroker.ROOT_COLLECTION;

	protected static String XUPDATE_1 =
		"<xu:modifications version=\"1.0\" xmlns:xu=\"" + XUpdateProcessor.XUPDATE_NS + "\">" +
		"<xu:insert-after select=\"//SPEECH/LINE[. &amp;= 'loving']\">" +
		"<TEST>New line inserted</TEST>" +
		"</xu:insert-after>" +
		"</xu:modifications>";
	
	public static void main(String args[]) throws Exception {
		// initialize database drivers
		Class cl = Class.forName(driver);
		// create the default database
		Database database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);

		// create a second database called "test", configured by
		// configuration file "testConf.xml"
		Database database2 = (Database) cl.newInstance();
		database2.setProperty("create-database", "true");
		database2.setProperty("configuration", "path/to/testConf.xml");
		database2.setProperty("database-id", "test");

		DatabaseManager.registerDatabase(database2);

		Collection collection1 = DatabaseManager.getCollection(URI_DB1, "admin", null);
		loadFile(collection1, "samples/shakespeare/hamlet.xml");
		Collection collection2 = DatabaseManager.getCollection(URI_DB2, "admin", null);
		loadFile(collection2, "samples/shakespeare/r_and_j.xml");
		
		doQuery(collection1, "xmldb:document()//SPEECH[LINE &= 'cursed spite']");
		doQuery(collection2, "xmldb:document()//SPEECH[LINE&= 'love' and SPEAKER = 'JULIET']");
		
		doXUpdate(collection1, XUPDATE_1);
		doXUpdate(collection2, XUPDATE_1);
		
		shutdown(collection1);
		shutdown(collection2);
	}

	private static void doQuery(Collection collection, String query) throws XMLDBException {
		XQueryService service = (XQueryService)
			collection.getService("XQueryService", "1.0");
		ResourceSet result = service.query(query);
		System.out.println("Found " + result.getSize() + " results.");
		for(ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
			System.out.println(i.nextResource().getContent());
		}
	}

	private static void doXUpdate(Collection collection, String xupdate) throws XMLDBException {
		XUpdateQueryService service = (XUpdateQueryService)
			collection.getService("XUpdateQueryService", "1.0");
		long mods = service.update(xupdate);
		System.out.println(mods + " modifications processed.");
	}
	
	private static void loadFile(Collection collection, String path) throws XMLDBException {
		// create new XMLResource; an id will be assigned to the new resource
		XMLResource document = (XMLResource) 
			collection.createResource(path.substring(path.lastIndexOf('/')), 
				"XMLResource");
		document.setContent(new File(path));
		collection.storeResource(document);
	}

	private static void shutdown(Collection collection) throws XMLDBException {
		//		shutdown the database gracefully
		DatabaseInstanceManager manager =
			(DatabaseInstanceManager) collection.getService("DatabaseInstanceManager", "1.0");
		manager.shutdown();
	}
}
