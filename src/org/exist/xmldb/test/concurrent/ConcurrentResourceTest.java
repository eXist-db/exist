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
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class ConcurrentResourceTest extends ConcurrentTestBase {

	private final static String URI = "xmldb:exist:///db";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(ConcurrentResourceTest.class);
	}
	
	private File tempFile;
	
	/**
	 * @param name
	 * @param uri
	 * @param testCollection
	 */
	public ConcurrentResourceTest(String name) {
		super(name, URI, "C1");
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.ConcurrentTestBase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
		
		String[] wordList = DBUtils.wordList(rootCol);
		tempFile = DBUtils.generateXMLFile(1000, 10, wordList);
		DBUtils.addXMLResource(getTestCollection(), "R1.xml", tempFile);
		addAction(new ReplaceResourceAction(URI + "/C1", "R1.xml", wordList), 10, 200);
		addAction(new RetrieveResourceAction(URI + "/C1", "R1.xml"), 10, 500);
		
		// TODO: using an addition replace thread generates a deadlock condition !!!	
//		addAction(new ReplaceResourceAction(URI + "/C1", "R1.xml", wordList), 10, 300);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.ConcurrentTestBase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		tempFile.delete();
	}
}
