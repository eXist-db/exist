package org.exist.examples.xmldb;

import javax.xml.transform.OutputKeys;

import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.xmldb.EXistResource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.modules.XMLResource;

/**
 *  Retrieve a document from the database. To run this example enter:
 * 
 *  java -jar start.jar org.exist.examples.xmldb.Retrieve collection document 
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
		DatabaseManager.registerDatabase(database);

		// get the collection
		Collection col = DatabaseManager.getCollection(URI + collection);
		col.setProperty(OutputKeys.INDENT, "yes");
		col.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
        col.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
		XMLResource res = (XMLResource)col.getResource(args[1]);
		if(res == null)
			System.out.println("document not found!");
		else {
			System.out.println(res.getContent());
			System.out.println("Size: " + ((EXistResource)res).getContentLength());
		}
	}
}
