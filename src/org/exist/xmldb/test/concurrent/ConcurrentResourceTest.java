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

import org.xmldb.api.base.Collection;

/**
 * Test concurrent acess to resources.
 * 
 * @author wolf
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
		
		Collection c1 = DBUtils.addCollection(getTestCollection(), "C1-C2");
		DBUtils.addXMLResource(c1, "R1.xml", ReplaceResourceAction.XML);
		
        String query0 = "//user[email = 'sam@email.com']";
        String query1 = "distinct-values(//user/@id)";
        
		addAction(new ReplaceResourceAction(URI + "/C1/C1-C2", "R1.xml"), 1000, 0, 500);
		addAction(new RetrieveResourceAction(URI + "/C1/C1-C2", "R1.xml"), 1500, 1000, 500);
        addAction(new XQueryAction(URI + "/C1", "R1.xml", query0), 500, 1000, 500);
        addAction(new XQueryAction(URI + "/C1", "R1.xml", query1), 500, 1000, 500);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.ConcurrentTestBase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}
}
