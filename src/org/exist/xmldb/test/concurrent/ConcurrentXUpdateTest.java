/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xmldb.test.concurrent;

import java.io.File;


/**
 * Test concurrent XUpdates on the same document.
 * 
 * @author wolf
 */
public class ConcurrentXUpdateTest extends ConcurrentTestBase {

	private final static String URI = "xmldb:exist:///db";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(ConcurrentXUpdateTest.class);
	}

	private File tempFile;
	
	public ConcurrentXUpdateTest(String name) {
		super(name, URI, "C1");
	}
	
	protected void setUp() throws Exception {
		super.setUp();
		
		String[] wordList = DBUtils.wordList(rootCol);
		tempFile = DBUtils.generateXMLFile(500, 10, wordList);
		DBUtils.addXMLResource(getTestCollection(), "R1.xml", tempFile);
		
		addAction(new RemoveAppendAction(URI + "/C1", "R1.xml", wordList), 10, 500);
		addAction(new RemoveAppendAction(URI + "/C1", "R1.xml", wordList), 10, 500);
		addAction(new RetrieveResourceAction(URI + "/C1", "R1.xml"), 10, 2000);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.ConcurrentTestBase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		tempFile.delete();
	}
}
