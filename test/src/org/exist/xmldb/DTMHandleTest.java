/**
 * DTMHandleTest.java
 *
 * 2004 by O2 IT Engineering
 * Zurich,  Switzerland (CH)
 */
package org.exist.xmldb;

import org.xmldb.api.base.CompiledExpression;
import org.exist.storage.DBBroker;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XMLResource;

import junit.framework.TestCase;

/**
 * Tests the TreeLevelOrder function.
 * 
 * @author Tobias Wunden
 * @version 1.0
 */

public class DTMHandleTest extends TestCase {
	
	/** eXist database url */
	static final String eXistUrl ="xmldb:exist://";

	/** eXist configuration file */
	static final String eXistConf = "C:\\Documents and Settings\\Tobias Wunden\\My Documents\\Projects\\Varia\\Test\\conf.xml";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(DTMHandleTest.class);
	}

	/**
	 * Test for the TreeLevelOrder function. This test
	 * <ul>
	 * <li>Registers a database instance</li>
	 * <li>Writes a document to the database using the XQueryService</li>
	 * <li>Reads the document from the database using XmlDB</li>
	 * <li>Accesses the document using DOM</li>
	 * </ul>
	 */
	public final void testTreeLevelOrder() {
		Database eXist = null;
		String document = "survey.xml";

		StringBuffer xmlDocument = new StringBuffer();
		xmlDocument.append("<survey>");
		xmlDocument.append("<date>2004/11/24 17:42:31 GMT</date>");
		xmlDocument.append("<from>tobias.wunden@o2it.ch</from>");
		xmlDocument.append("<to>tobias.wunden@o2it.ch</to>");
		xmlDocument.append("<subject>Test</subject>");
		xmlDocument.append("<field>");
		xmlDocument.append("<name>homepage</name>");
		xmlDocument.append("<value>-</value>");
		xmlDocument.append("</field>");
		xmlDocument.append("</survey>");		

		try
		{
			eXist = registerDatabase();		
			// Obtain XQuery service
			XQueryService service = getXQueryService(eXist);
			assertNotNull("Failed to obtain xquery service instance!", service);
			// write document to the database		
			store(xmlDocument.toString(), service, document);
			// read document back from database
			Node root = load(service, document);
			assertNotNull("Document " + document + " was not found in the database!", root);
			
			boolean foundFieldText = false;
			
			NodeList rootChildren = root.getChildNodes();
			for(int r=0; r < rootChildren.getLength(); r++)
			{
				if(rootChildren.item(r).getLocalName().equals("field"))
				{
					foundFieldText = false;
					
					Node field = rootChildren.item(r);
					System.out.println("Found field node[" + 1 + "]");
					
					NodeList fieldChildren = field.getChildNodes();
					for(int f=0; f < fieldChildren.getLength(); f++)
					{
						if(fieldChildren.item(f).getLocalName().equals("name"))
						{
							foundFieldText = true;
							
							Node name = fieldChildren.item(f);
							//String nameText = name.getTextContent();
                                                        String nameText = TreeLevelOrderTest.textContent(name);
							assertNotNull("Failed to read existing field[" + 1 + "]/name/text()", nameText);
						}
					}
					
					assertTrue("Failed to read existing field[" + 1 + "]/name/text()", foundFieldText);
				}
			}
			
			System.out.println("Test succeeded");			
		}
		catch (Exception e)
		{		    
			fail(e.getMessage());
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
		query.append("let $isLoggedIn := xdb:login(\"" + eXistUrl + DBBroker.ROOT_COLLECTION + "\", \"admin\", \"admin\"),");
		query.append("$doc := xdb:store(\"" + eXistUrl + DBBroker.ROOT_COLLECTION + "\", $document, $survey)");
		query.append("return <result/>");

		try {
			service.declareVariable("survey", xml);
			service.declareVariable("document", document);
			CompiledExpression cQuery = service.compile(query.toString());
			service.execute(cQuery);
		} catch(Exception e) {			
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
		query.append("let $survey := xmldb:document(concat('" + DBBroker.ROOT_COLLECTION + "', '/', $document))");
		query.append("return ($survey)");		
		try {
			service.declareVariable("document", document);
			CompiledExpression cQuery = service.compile(query.toString());
			ResourceSet set = service.execute(cQuery);
			assertNotNull(set);
			assertTrue(set.getSize() > 0);
			return ((XMLResource)set.getIterator().nextResource()).getContentAsDOM();			
		} catch(Exception e) {			
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
			//database.setProperty("configuration", eXistConf);
			DatabaseManager.registerDatabase(database);
			return database;
		} catch(Exception e) {			
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
			assertNotNull(collection);
			XQueryService service = (XQueryService)collection.getService("XQueryService", "1.0");
			collection.close();
			return service;			
		} catch(Exception e) {			
			fail(e.getMessage());
		}			
		return null;
	}
	
}