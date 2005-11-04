/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *
 *  http://exist.sourceforge.net
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
package org.exist.xmldb.test;

import org.apache.xpath.XPathAPI;
import org.xmldb.api.base.CompiledExpression;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XQueryService;
import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import junit.framework.TestCase;

/**
 * Tests the TreeLevelOrder function.
 * 
 * @author Tobias Wunden
 * @version 1.0
 */

public class TreeLevelOrderTest extends TestCase {
	
	/** eXist database url */
	static final String eXistUrl ="xmldb:exist://";

	/** eXist configuration file */
	static final String eXistConf = "conf.xml";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(TreeLevelOrderTest.class);
	}

	/**
	 * Test for the TreeLevelOrder function. This test
	 * <ul>
	 * <li>Registers a database instance</li>
	 * <li>Writes a document to the database using the XQueryService</li>
	 * <li>Reads the document from the database using XmlDB</li>
	 * <li>Accesses the document using Apache's XPathAPI</li>
	 * </ul>
	 */
	public final void testTreeLevelOrder() {
		Database eXist = null;
		String document = "survey.xml";

		try {
			eXist = registerDatabase();
		} catch (XMLDBException e) {
			fail("Unable to register database: "  + e.getMessage());
		}

		// Obtain XQuery service
		XQueryService service = null;
		try {
			service = getXQueryService(eXist);
			if (service == null) {
				fail("Failed to obtain xquery service instance!");
			}
		} catch (XMLDBException e) {
			fail("Failed to obtain xquery service instance: "  + e.getMessage());
		}

		// create document
		StringBuffer xmlDocument = new StringBuffer();
		xmlDocument.append("<survey>");
		xmlDocument.append("<date>2004/11/24 17:42:31 GMT</date>");
		xmlDocument.append("<from><![CDATA[tobias.wunden@o2it.ch]]></from>");
		xmlDocument.append("<to><![CDATA[tobias.wunden@o2it.ch]]></to>");
		xmlDocument.append("<subject><![CDATA[Test]]></subject>");
		xmlDocument.append("<field>");
		xmlDocument.append("<name><![CDATA[homepage]]></name>");
		xmlDocument.append("<value><![CDATA[-]]></value>");
		xmlDocument.append("</field>");
		xmlDocument.append("</survey>");
		
		// write document to the database
		try {
			store(xmlDocument.toString(), service, document);
		} catch (XMLDBException e) {
			fail("Failed to write document to database: " + e.getMessage());
		}

		// read document back from database
		Node root = null;
		try {
			root = load(service, document);
			if (root == null) {
				fail("Document " + document + " was not found in the database!");
			}
		} catch (XMLDBException e) {
			fail("Failed to write document to database: " + e.getMessage());
		}

		// issue xpath query
		try {
			Node node = XPathAPI.selectSingleNode(root, "/survey/to/text()");
			if (node != null) {
				System.out.println("Found " + node.getNodeValue());
			}
		} catch (Exception e) {
		    e.printStackTrace();
			fail("Failed to issue xpath on root node: " + e.getMessage());
		}
	}

	/**
	 * Stores the given xml fragment into the database.
	 * 
	 * @param xml the xml document
	 * @param service the xquery service
	 * @param document the document name
	 * @throws XMLDBException on database error
	 */
	private final void store(String xml, XQueryService service, String document) throws XMLDBException {
		StringBuffer query = new StringBuffer();
		query.append("xquery version \"1.0\";");
		query.append("declare namespace xdb=\"http://exist-db.org/xquery/xmldb\";");
		query.append("let $root := xdb:collection('" + eXistUrl + DBBroker.ROOT_COLLECTION + "', 'admin', 'admin'),");
		query.append("$doc := xdb:store($root, $document, $survey)");
		query.append("return <result/>");

		service.declareVariable("survey", xml);
		service.declareVariable("document", document);
		CompiledExpression cQuery = service.compile(query.toString());
		service.execute(cQuery);
	}

	/**
	 * Loads the xml document identified by <code>document</code> from the database.
	 * 
	 * @param service the xquery service
	 * @param document the document to load
	 * @throws XMLDBException on database error
	 */
	private final Node load(XQueryService service, String document) throws XMLDBException {
		StringBuffer query = new StringBuffer();
		query.append("xquery version \"1.0\";");
		query.append("let $survey := document(concat(\"/db/\", $document))");
		query.append("return ($survey)");
			
		service.declareVariable("document", document);
		CompiledExpression cQuery = service.compile(query.toString());
		ResourceSet set = service.execute(cQuery);
		if (set != null && set.getSize() > 0) {
			return ((XMLResource)set.getIterator().nextResource()).getContentAsDOM();
		}
		return null;
	}

	/**
	 * Registers a new database instance and returns it.
	 * 
	 * @throws XMLDBException
	 */
	private final Database registerDatabase() throws XMLDBException {
		Class driver = null;
		String driverName = "org.exist.xmldb.DatabaseImpl";
		try {
			driver = Class.forName(driverName);
			Database database = (Database)driver.newInstance();
			database.setProperty("create-database", "true");
//			database.setProperty("configuration", eXistConf);
			DatabaseManager.registerDatabase(database);
			return database;
		} catch (ClassNotFoundException e) {
			System.err.println("Driver class " + driverName + " was not found!");
			throw new XMLDBException();
		} catch (InstantiationException e) {
			System.err.println("Driver class " + driverName + " could not be instantiated!");
			throw new XMLDBException();
		} catch (IllegalAccessException e) {
			System.err.println("Access violation when trying to instantiate XMLDB Driver " + driverName + "!");
			throw new XMLDBException();
		}
	}
	
	/**
	 * Retrieves the base collection and thereof returns a reference to the collection's
	 * xquery service.
	 * 
	 * @param db the database
	 * @return the xquery service
	 * @throws XMLDBException on database error
	 */
	private final XQueryService getXQueryService(Database db) throws XMLDBException {
		Collection collection = DatabaseManager.getCollection(eXistUrl + "/db", "admin", "");
		if (collection != null) {
			XQueryService service = (XQueryService)collection.getService("XQueryService", "1.0");
			collection.close();
			return service;
		}
		return null;
	}
	
}