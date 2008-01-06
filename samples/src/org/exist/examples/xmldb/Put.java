package org.exist.examples.xmldb;

import java.io.File;

import org.exist.storage.DBBroker;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

/**
 * Add a document to the database.
 * 
 * Call with java -jar start.jar org.exist.examples.xmldb.Put collection docName
 *
 */
public class Put {

	public final static String URI = "xmldb:exist://localhost:8080/exist/xmlrpc";
	
	protected static void usage() {
		System.out.println("usage: org.exist.examples.xmldb.Put collection docName");
		System.exit(0);
	}

	public static void main(String args[]) throws Exception {
		if(args.length < 2)
			usage();

		String collection = args[0], file = args[1];

        // initialize driver
		String driver = "org.exist.xmldb.DatabaseImpl";
		Class cl = Class.forName(driver);			
		Database database = (Database)cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);
		
        // try to get collection
		Collection col = 
			DatabaseManager.getCollection(URI + collection);
		if(col == null) {
            // collection does not exist: get root collection and create.
            // for simplicity, we assume that the new collection is a
            // direct child of the root collection, e.g. /db/test.
            // the example will fail otherwise.
            Collection root = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION);
            CollectionManagementService mgtService = 
                (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
            col = mgtService.createCollection(collection.substring((DBBroker.ROOT_COLLECTION + "/").length()));
        }
		File f = new File(file);
        // create new XMLResource
		XMLResource document = (XMLResource)col.createResource(f.getName(), "XMLResource");
		document.setContent(f);
		System.out.print("storing document " + document.getId() + "...");
		col.storeResource(document);
		System.out.println("ok.");
	}
}
