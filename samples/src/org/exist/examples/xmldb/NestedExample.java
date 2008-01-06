package org.exist.examples.xmldb;

import javax.xml.transform.OutputKeys;

import org.exist.storage.DBBroker;
import org.exist.xmldb.XPathQueryServiceImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XMLResource;

/**
 *  Execute 2 queries after inserting document. To run this example enter:
 * 
 *  java -jar start.jar org.exist.examples.xmldb.NestedExample 
 * 
 *  in the root directory of the distribution.
 */

public class NestedExample {
 
	protected static String URI = "xmldb:exist://localhost:8080/exist/xmlrpc";
	protected static String driver = "org.exist.xmldb.DatabaseImpl";

	protected static String data =
		"<book><chapter><title>Title</title><para>Paragraph 1</para>" +
		"<para>Paragraph 2</para></chapter></book>";
	
	public static void main(String[] args) throws Exception {
		Class cl = Class.forName( driver );
		Database database = (Database) cl.newInstance();
		database.setProperty( "create-database", "true" );
		DatabaseManager.registerDatabase( database );
		Collection col =
			DatabaseManager.getCollection( URI + DBBroker.ROOT_COLLECTION, "admin", "" );
		XMLResource res = (XMLResource)col.createResource("test.xml", "XMLResource");
		res.setContent(data);
		col.storeResource(res);
		
		// get query-service
		XPathQueryServiceImpl service =
			(XPathQueryServiceImpl) col.getService( "XPathQueryService", "1.0" );
		
		// set pretty-printing on
		service.setProperty( OutputKeys.INDENT, "yes" );
		service.setProperty( OutputKeys.ENCODING, "UTF-8" );
		
		// execute queries
		ResourceSet set=null;
		
		System.out.println();
		System.out.println("Query 1");
		System.out.println("=======");
		set = service.query("/book/chapter");
		res = (XMLResource) set.getResource(0);
		System.out.println(res.getContent());
		
		System.out.println();
		System.out.println("Query 2");
		System.out.println("=======");
		set = service.query(res, "title");
		res = (XMLResource) set.getResource(0);
		System.out.println(res.getContent());
	}
}
