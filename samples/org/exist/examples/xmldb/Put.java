package org.exist.examples.xmldb;

import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;
import java.io.*;

/**
 * Add a document to the database.
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
            // collection does not exist: get root collection and create
            // for simplicity, we assume that the new collection is a
            // direct child of the root collection, e.g. /db/test.
            // the example will fail otherwise.
            Collection root = DatabaseManager.getCollection(URI + "/db");
            CollectionManagementService mgtService = 
                (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
            col = mgtService.createCollection(collection.substring("/db".length()));
        }
        // create new XMLResource; an id will be assigned to the new resource
		XMLResource document = (XMLResource)col.createResource(null, "XMLResource");
		String xml = readFile(file);
		document.setContent(xml);
		System.out.print("storing document " + document.getId() + "...");
		col.storeResource(document);
		System.out.println("ok.");
	}

	protected static String readFile(String file) throws IOException {
        // read the file into a string
        BufferedReader f = new BufferedReader(new FileReader(file));
        String line;
        StringBuffer xml = new StringBuffer();
        while((line = f.readLine()) != null)
            xml.append(line);
        f.close();
        return xml.toString();
    }
}
