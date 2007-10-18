package org.exist.xquery;

import org.exist.storage.DBBroker;
import org.exist.xmldb.XQueryService;
import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import junit.framework.TestCase;

/**
 * RemoveAndReloadTest.java
 *
 * O2 IT Engineering
 * Zurich,  Switzerland (CH)
 */

/**
 * This test provokes a parameter type error (how?).
 * 
 * @author Tobias Wunden
 * @version 1.0
 */

public class NodeTypeTest extends TestCase {

	/** eXist database url */
	static final String eXistUrl ="xmldb:exist://";

	/** eXist home directory */
	static final String existHome = "C:\\Documents and Settings\\Tobias Wunden\\My Documents\\Projects\\Varia\\Test";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(NodeTypeTest.class);
	}

	/**
	 * This test passes nodes containing xml entities to eXist and tries
	 * to read it back in:
	 * <ul>
	 * <li>Register a database instance</li>
	 * <li>Write a "live" document to the database using the XQueryService</li>
	 * <li>Create a "work" version of it</li>
	 * </ul>
	 */
	public final void testRemoveAndReload() {
		XQueryService service = setupDatabase();
		
		// write "live" document to the database
		try {
			store(createDocument(), service, "live.xml");
		} catch (Exception e) {
			fail("Failed to write document to database: " + e.getMessage());
		}
		
		// copy content from work.xml to live.xml using XUpdate
		try {
			prepareWorkVersion(service);
		} catch (Exception e) {
            e.printStackTrace();
			fail("Failed to update document in database: " + e.getMessage());
		}

	}

	/**
	 * Stores the given xml fragment into the database.
	 * 
	 * @param xml the xml document
	 * @param service the xquery service
	 * @param document the document name	 
	 */
	private final void store(String xml, XQueryService service, String document) {
		StringBuffer query = new StringBuffer();
		query.append("xquery version \"1.0\";");
		query.append("declare namespace xdb=\"http://exist-db.org/xquery/xmldb\";");
		query.append("let $isLoggedIn := xdb:login('" + eXistUrl + DBBroker.ROOT_COLLECTION + "', \"admin\", \"admin\"),");
		query.append("$doc := xdb:store(\"" + eXistUrl + DBBroker.ROOT_COLLECTION + "\", $document, $data)");
		query.append("return <result/>");
		try {
			service.declareVariable("document", document);
			service.declareVariable("data", xml);
			CompiledExpression cQuery = service.compile(query.toString());
			service.execute(cQuery);
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}			
	}

	/**
	 * Updates the given xml fragment in the database using XUpdate.
	 * 
	 * @param service the xquery service	
	 */
	private final void prepareWorkVersion(XQueryService service) {
		StringBuffer query = new StringBuffer();
		query.append("xquery version \"1.0\";\n");
		query.append("declare namespace xdb=\"http://exist-db.org/xquery/xmldb\";\n");
		query.append("declare namespace f=\"urn:weblounge\";\n");

		// Returns a new with a given body and a new header
		query.append("declare function f:create($live as node(), $target as xs:string) as node() { \n");
		query.append("    <page partition=\"{$live/@partition}\" path=\"{$live/@path}\" version=\"{$target}\"> \n");
		query.append("        {$live/*} \n");
		query.append("    </page> \n");
		query.append("}; \n");

		// Function "prepare". Checks if the work version already exists. If this is not the
		// case, it calls the "create" function to have a new page created with the live body
		// but with a "work" or "$target" header.
		query.append("declare function f:prepare($data as node(), $target as xs:string) as xs:string? { \n");
		query.append("    if (empty(xmldb:xcollection($collection)/page[@version=$target])) then \n");
		query.append("        let $isLoggedIn := xdb:login(concat(\"xmldb:exist://\", $collection), 'admin', 'admin') \n");
		query.append("        return xdb:store(concat(\"xmldb:exist://\", $collection), concat($target, \".xml\"), f:create($data, $target)) \n");
		query.append("    else \n");
		query.append("    () \n");
		query.append("}; \n");
		
		// Main clause, tries to create a work from an existing live version
		query.append("let $live := xmldb:xcollection($collection)/page[@version=\"live\"],\n");
        query.append("     $log := util:log('DEBUG', $live),\n");
		query.append("     $w := f:prepare($live, \"work\")\n");
		query.append("    return\n");
		query.append("		              ()\n");
		
		try {
			service.declareVariable("collection", DBBroker.ROOT_COLLECTION);
			CompiledExpression cQuery = service.compile(query.toString());
			service.execute(cQuery);
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}			
	}

	/**
	 * Updates the given xml fragment in the database using XUpdate.
	 * 
	 * @param service the xquery service	 
	 */
	private final void xupdateRemove(String doc, XQueryService service) {
		StringBuffer query = new StringBuffer();
		query.append("xquery version \"1.0\";");
		query.append("declare namespace xdb=\"http://exist-db.org/xquery/xmldb\";");
		query.append("let $isLoggedIn := xdb:login('" + eXistUrl + DBBroker.ROOT_COLLECTION + "', \"admin\", \"admin\"),");
		query.append("$mods := xdb:remove(\"" + eXistUrl + DBBroker.ROOT_COLLECTION + "\", \"" + doc + "\")");
		query.append("return <modifications>{$mods}</modifications>");

		try {
			CompiledExpression cQuery = service.compile(query.toString());
			service.execute(cQuery);
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}			
	}

	/**
	 * Loads the xml document identified by <code>document</code> from the database.
	 * 
	 * @param service the xquery service
	 * @param document the document to load	 
	 */
	private final Node load(XQueryService service, String document) {
		StringBuffer query = new StringBuffer();
		query.append("xquery version \"1.0\";");
		query.append("let $result := xmldb:document(concat('" + DBBroker.ROOT_COLLECTION + "', $document))");
		query.append("return ($result)");

		try {
			service.declareVariable("document", document);
			CompiledExpression cQuery = service.compile(query.toString());
			ResourceSet set = service.execute(cQuery);
			if (set != null && set.getSize() > 0) {
				return ((XMLResource)set.getIterator().nextResource()).getContentAsDOM();
			}			
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}
		return null;
	}

	/**
	 * Registers a new database instance and returns it.
	 */
	private final Database registerDatabase() {
		Class driver = null;
		String driverName = "org.exist.xmldb.DatabaseImpl";
		try {
			driver = Class.forName(driverName);
			Database database = (Database)driver.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			return database;
		} catch (Exception e) {
			fail(e.getMessage());
		}
		return null;
	}
	
	/**
	 * Retrieves the base collection and thereof returns a reference to the collection's
	 * xquery service.
	 * 
	 * @param db the database
	 * @return the xquery service	
	 */
	private final XQueryService getXQueryService(Database db) {
		try {
			Collection collection = DatabaseManager.getCollection(eXistUrl + DBBroker.ROOT_COLLECTION, "admin", "admin");
			if (collection != null) {
				XQueryService service = (XQueryService)collection.getService("XQueryService", "1.0");
				collection.close();
				return service;
			}
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}
		return null;
	}
	
	private final String createDocument() {
		StringBuffer xmlDocument = new StringBuffer();
		xmlDocument.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		xmlDocument.append("<page partition=\"home\" path=\"/\" version=\"live\">");
		xmlDocument.append("    <header>");
		xmlDocument.append("        <renderer>home_dreispaltig</renderer>");
		xmlDocument.append("        <layout>default</layout>");
		xmlDocument.append("        <type>default</type>");
		xmlDocument.append("        <publish>");
		xmlDocument.append("            <from>2005/06/06 10:53:40 GMT</from>");
		xmlDocument.append("            <to>292278994/08/17 07:12:55 GMT</to>");
		xmlDocument.append("        </publish>");
		xmlDocument.append("        <security>");
		xmlDocument.append("            <owner>www</owner>");
		xmlDocument.append("            <permission id=\"system:manage\" type=\"role\">system:editor</permission>");
		xmlDocument.append("            <permission id=\"system:read\" type=\"role\">system:guest</permission>");
		xmlDocument.append("            <permission id=\"system:translate\" type=\"role\">system:translator</permission>");
		xmlDocument.append("            <permission id=\"system:publish\" type=\"role\">system:publisher</permission>");
		xmlDocument.append("            <permission id=\"system:write\" type=\"role\">system:editor</permission>");
		xmlDocument.append("        </security>");
		xmlDocument.append("        <keywords/>");
		xmlDocument.append("        <title language=\"de\">Home</title>");
		xmlDocument.append("        <title language=\"fr\">Home</title>");
		xmlDocument.append("        <title language=\"it\">Home</title>");
		xmlDocument.append("        <modified>");
		xmlDocument.append("            <date>2005/06/06 10:53:40 GMT</date>");
		xmlDocument.append("            <user>markus.jauss</user>");
		xmlDocument.append("        </modified>");
		xmlDocument.append("    </header>");
		xmlDocument.append("    <body/>");
		xmlDocument.append("</page>");
		return xmlDocument.toString();
	}

	/**
	 * Creates the database connection.
	 * 
	 * @return the xquery service
	 */
	private XQueryService setupDatabase() {		
		try {
			Database  eXist = registerDatabase();
			// Obtain XQuery service
			XQueryService service = null;		
			service = getXQueryService(eXist);
			return service;
		} catch (Exception e) {
			fail("Unable to register database: "  + e.getMessage());
		}
		return null;
	}
	
}