package org.exist.examples.xmldb;

import java.io.File;

import org.exist.xmldb.DatabaseInstanceManager;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 *  Retrieve a document from the database. To run this example enter:
 * 
 *  bin/run.sh org.exist.examples.xmldb.Retrieve collection document 
 * 
 *  in the root directory of the distribution.
 *
 *	@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class MultipleDatabases {

	protected static String driver = "org.exist.xmldb.DatabaseImpl";

	protected static String URI_DB1 = "xmldb:exist:///db";
	protected static String URI_DB2 = "xmldb:test:///db";

	public static void main(String args[]) throws Exception {
		// initialize database drivers
		Class cl = Class.forName(driver);
		// create the default database
		//Database database = (Database) cl.newInstance();
		//database.setProperty("create-database", "true");
		//DatabaseManager.registerDatabase(database);

		// create a second database called "test", configured by
		// configuration file "testConf.xml"
		Database database2 = (Database) cl.newInstance();
		database2.setProperty("create-database", "true");
		database2.setProperty("configuration", "/home/wolf/test/conf.xml");
		database2.setProperty("database-id", "test");

		DatabaseManager.registerDatabase(database2);

		//Collection collection1 = DatabaseManager.getCollection(URI_DB1, "admin", null);
		//loadFile(collection1, "samples/shakespeare/hamlet.xml");
		Collection collection2 = DatabaseManager.getCollection(URI_DB2, "admin", null);
		loadFile(collection2, "samples/shakespeare/r_and_j.xml");
		
		//shutdown(collection1);
		shutdown(collection2);
	}

	private static void loadFile(Collection collection, String path) throws XMLDBException {
		// create new XMLResource; an id will be assigned to the new resource
		XMLResource document = (XMLResource) 
			collection.createResource(path.substring(path.lastIndexOf(File.separatorChar)), 
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
