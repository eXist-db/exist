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
package org.exist.xmldb;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

public class TreeLevelOrderTest extends TestCase {
	
	/** eXist database url */
	static final String eXistUrl ="xmldb:exist://";

	/** eXist configuration file */
	static final String eXistConf = "conf.xml";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(TreeLevelOrderTest.class);
	}

      static Method getTextContentMethod = null;
      static {
         try {
            getTextContentMethod = Node.class.getMethod("getTextContent",null);
         } catch (Exception ex) {
         }
      }
      public static String textContent(Node n) {
         if (getTextContentMethod!=null) {
            try {
               return (String)getTextContentMethod.invoke(n,null);
            } catch (IllegalArgumentException ex) {
               ex.printStackTrace();
               return null;
            } catch (InvocationTargetException ex) {
               ex.printStackTrace();
               return null;
            } catch (IllegalAccessException ex) {
               ex.printStackTrace();
               return null;
            }
         } 
         if (n.getNodeType()==Node.ELEMENT_NODE) {
            StringBuffer builder = new StringBuffer();
            Node current = n.getFirstChild();
            while (current!=null) {
               int type = current.getNodeType();
               if (type==Node.CDATA_SECTION_NODE || type==Node.TEXT_NODE) {
                  builder.append(current.getNodeValue());
               }
               current = current.getNextSibling();
            }
            return builder.toString();
         } else {
            return n.getNodeValue();
         }
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
	public final void testTreeLevelOrder()
	{		
		Database eXist = null;
		String document = "survey.xml";
		XQueryService service = null;

		try
		{
			eXist = registerDatabase();
			assertNotNull(eXist);
			// Obtain XQuery service			
			service = getXQueryService(eXist);
			assertNotNull(service);
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
			store(xmlDocument.toString(), service, document);
			// read document back from database
			Node root = load(service, document);
			assertNotNull(root);
			
			//get node using DOM
			String strTo = null;
			
			NodeList rootChildren = root.getChildNodes();
			for(int r=0; r < rootChildren.getLength(); r++)
			{
				if(rootChildren.item(r).getLocalName().equals("to"))
				{
					Node to = rootChildren.item(r);
					
					//strTo = to.getTextContent();
                                        strTo = textContent(to);
				}
			}
			
			assertNotNull(strTo);
        }
		catch(Exception e)
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
		try {
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
        } catch (Exception e) {            
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
		try {
			StringBuffer query = new StringBuffer();
			query.append("xquery version \"1.0\";");
			query.append("let $survey := xmldb:document(concat('" + DBBroker.ROOT_COLLECTION + "', $document))");
			query.append("return ($survey)");
				
			service.declareVariable("document", document);
			CompiledExpression cQuery = service.compile(query.toString());
			ResourceSet set = service.execute(cQuery);
			if (set != null && set.getSize() > 0) {
				return ((XMLResource)set.getIterator().nextResource()).getContentAsDOM();
			}			
        } catch (Exception e) {            
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
//			database.setProperty("configuration", eXistConf);
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
			Collection collection = DatabaseManager.getCollection(eXistUrl + DBBroker.ROOT_COLLECTION, "admin", "");
			if (collection != null) {
				XQueryService service = (XQueryService)collection.getService("XQueryService", "1.0");
				collection.close();
				return service;
			}
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }				
		return null;
	}
	
}