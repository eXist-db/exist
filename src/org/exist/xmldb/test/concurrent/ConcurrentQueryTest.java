/*
*  eXist Open Source Native XML Database
*  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
*  and others (see http://exist-db.org)
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
package org.exist.xmldb.test.concurrent;

import java.io.File;

/**
 * @author wolf
 */
public class ConcurrentQueryTest extends ConcurrentTestBase {

	private final static String URI = "xmldb:exist:///db";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(ConcurrentQueryTest.class);
	}
	
	private File tempFile;
	
	/**
	 * @param name
	 * @param uri
	 * @param testCollection
	 */
	public ConcurrentQueryTest(String name) {
		super(name, URI, "C1");
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.ConcurrentTestBase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		
		String[] wordList = DBUtils.wordList(rootCol);
		tempFile = DBUtils.generateXMLFile(5000, 7, wordList);
		DBUtils.addXMLResource(getTestCollection(), "R1.xml", tempFile);
		
		String query0 = "/ROOT-ELEMENT/ELEMENT/ELEMENT-1";
		String query1 = "distinct-values(//ELEMENT/@attribute-2)";
		String query2 = "/ROOT-ELEMENT//ELEMENT-1[@attribute-3]";
		
		addAction(new XQueryAction(URI + "/C1", "R1.xml", query0), 20, 500);
		addAction(new XQueryAction(URI + "/C1", "R1.xml", query1), 20, 0);
		addAction(new XQueryAction(URI + "/C1", "R1.xml", query2), 20, 0);
	}
}
