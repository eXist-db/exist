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
package org.exist.xmldb.concurrent;

import java.io.File;

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.XQueryAction;
import org.junit.After;
import org.junit.Before;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 */
public class ConcurrentQueryTest extends ConcurrentTestBase {

	private final static String URI = XmldbURI.LOCAL_DB;
	
	private File tempFile;
	
	private XQueryAction action0, action1, action2;

	public ConcurrentQueryTest() {
		super(URI, "C1");
	}

	@Before
	@Override
	public void setUp() throws Exception {
        super.setUp();

        String[] wordList = DBUtils.wordList(rootCol);
        tempFile = DBUtils.generateXMLFile(500, 7, wordList);
        DBUtils.addXMLResource(getTestCollection(), "R1.xml", tempFile);

        String query0 = "/ROOT-ELEMENT/ELEMENT/ELEMENT-1/ELEMENT-2[@attribute-3]";
        String query1 = "distinct-values(//ELEMENT/@attribute-2)";
        String query2 = "/ROOT-ELEMENT//ELEMENT-1[@attribute-3]";

        action0 = new XQueryAction(URI + "/C1", "R1.xml", query0);
        action1 = new XQueryAction(URI + "/C1", "R1.xml", query1);
        action2 = new XQueryAction(URI + "/C1", "R1.xml", query2);
	//		action3 = new XQueryAction(URI + "/C1", "R1.xml", query0);
	//		action4 = new XQueryAction(URI + "/C1", "R1.xml", query0);
	//		action5 = new XQueryAction(URI + "/C1", "R1.xml", query0);
			
        addAction(action0, 50, 500, 0);
        addAction(action1, 50, 250, 0);
        addAction(action2, 50, 0, 0);
	//		addAction(action3, 50, 0, 0);
	//		addAction(action4, 50, 0, 0);
	//		addAction(action5, 50, 0, 0);
	}

    @After
    @Override
    public void tearDown() throws XMLDBException {
        super.tearDown();
    }
}
