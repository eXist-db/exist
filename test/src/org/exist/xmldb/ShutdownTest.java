/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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

import java.io.File;

import org.exist.xmldb.concurrent.DBUtils;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Check if database shutdownDB/restart works properly. The test opens
 * the database, stores a few files and queries them, then shuts down the
 * db.
 *  
 * @author wolf
 */
public class ShutdownTest extends TestCase {

	private final static String URI = XmldbURI.LOCAL_DB;
	
	protected final static String XML =
		"<config>" +
		"<user id=\"george\">" +
		"<phone>+49 69 888478</phone>" +
		"<email>george@email.com</email>" +
		"<customer-id>64534233</customer-id>" +
		"<bank-account>7466356</bank-account>" +
		"</user>" +
		"<user id=\"sam\">" +
		"<phone>+49 69 774345</phone>" +
		"<email>sam@email.com</email>" +
		"<customer-id>993834</customer-id>" +
		"<bank-account>364553</bank-account>" +
		"</user>" +
		"</config>";
	
	private final static String TEST_QUERY1 = "//user[@id = 'george']/phone[contains(., '69')]/text()";
	private final static String TEST_QUERY2 = "//user[@id = 'sam']/customer-id[. = '993834']";
	private final static String TEST_QUERY3 = "//user[email = 'sam@email.com']";
	private final static String TEST_QUERY4 = "/ROOT-ELEMENT/ELEMENT/ELEMENT-1";
	
	private String[] wordList;
	
	public ShutdownTest(String name) {
		super(name);
	}
	
	public void testShutdown() {
		try {
			for (int i = 0; i < 50; i++) {
				System.out.println("Starting the database ...");
				Collection rootCol = DBUtils.setupDB(URI);
				
				// after restarting the db, we first try a bunch of queries
				Collection testCol = rootCol.getChildCollection("C1");
				
				ResourceSet result = DBUtils.query(testCol, TEST_QUERY1);
				Assert.assertEquals(1, result.getSize());
				Assert.assertEquals("+49 69 888478", result.getResource(0).getContent());
				
				result = DBUtils.query(testCol, TEST_QUERY2);
				Assert.assertEquals(1, result.getSize());
				
				result = DBUtils.query(testCol, TEST_QUERY3);
				Assert.assertEquals(1, result.getSize());
				
				result = DBUtils.query(testCol, TEST_QUERY4);
				Assert.assertEquals(5000, result.getSize());
				
				// now replace the data files
				String xml =
					"<data now=\"" + System.currentTimeMillis() + "\" count=\"" +
					i + "\">" + XML + "</data>";
				System.out.println("Storing resource ...");
				DBUtils.addXMLResource(testCol, "R1.xml", xml);
				
				System.out.println("Storing large file ...");
				File tempFile = DBUtils.generateXMLFile(5000, 7, wordList);
				DBUtils.addXMLResource(testCol, "R2.xml", tempFile);
				
				System.out.println("Shut down the database ...");
				DBUtils.shutdownDB(URI);
			}
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }			
	}
	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() {
		try {
			Collection rootCol = DBUtils.setupDB(URI);
			Collection testCol = rootCol.getChildCollection("C1");
			if(testCol == null) {
				testCol = DBUtils.addCollection(rootCol, "C1");
				assertNotNull(testCol);
			}
                        String existHome = System.getProperty("exist.home");
                        File existDir = existHome==null ? new File(".") : new File(existHome);
			DBUtils.addXMLResource(rootCol, "biblio.rdf", new File(existDir,"samples/biblio.rdf"));
			wordList = DBUtils.wordList(rootCol);
			
			// store the data files
			String xml =
				"<data now=\"" + System.currentTimeMillis() + "\" count=\"1\">" + XML + "</data>";
			DBUtils.addXMLResource(testCol, "R1.xml", xml);
			
			File tempFile = DBUtils.generateXMLFile(5000, 7, wordList);
			DBUtils.addXMLResource(testCol, "R2.xml", tempFile);
			
			DBUtils.shutdownDB(URI);
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }			
	}
	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() {
		try {
			Collection rootCol = DBUtils.setupDB(URI);
			DBUtils.removeCollection(rootCol, "C1");
			Resource res = rootCol.getResource("biblio.rdf");
			rootCol.removeResource(res);
			DBUtils.shutdownDB(URI);
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }			
	}
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(ShutdownTest.class);
	}

}
