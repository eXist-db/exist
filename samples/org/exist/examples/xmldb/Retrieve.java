package org.exist.examples.xmldb;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
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
public class Retrieve {

	protected static String driver = "org.exist.xmldb.DatabaseImpl";

	protected static String URI = "xmldb:exist://localhost:8080/exist/xmlrpc";

	protected static void usage() {
		System.out.println("usage: org.exist.examples.xmldb.Retrieve collection docName");
		System.exit(0);
	}

	public static void main(String args[]) throws Exception {
		if (args.length < 2) {
			usage();
		}

		String collection = args[0];

		// initialize database drivers
		Class cl = Class.forName(driver);
		Database database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);

		// get the collection
		Collection col = DatabaseManager.getCollection(URI + collection);
		col.setProperty("encoding", "ISO-8859-1");
		XMLResource res = (XMLResource)col.getResource(args[1]);
		if(res == null)
			System.out.println("document not found!");
		else
			System.out.println(res.getContent());
	}
}
