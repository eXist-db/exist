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
package org.exist.xmldb.concurrent;

import java.io.File;

import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.RemoveAppendAction;
import org.junit.After;
import org.junit.Before;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertNotNull;


/**
 * Test concurrent XUpdates on the same document.
 * 
 * @author wolf
 */
public class ConcurrentXUpdateTest extends ConcurrentTestBase {

	private final static String URI = XmldbURI.LOCAL_DB;

	private final static String CONFIG =
    	"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" + 
    	"	<index>" + 
    	"		<create path=\"//ELEMENT-1/@attribute-3\" type=\"xs:string\"/>" +
    	"		<create path=\"//ELEMENT-1/@attribute-1\" type=\"xs:string\"/>" +
    	"	</index>" + 
    	"</collection>";

	private File tempFile;
	
	public ConcurrentXUpdateTest() {
		super(URI, "C1");
	}

	@Before
	@Override
	public void setUp() throws Exception {
        super.setUp();
        IndexQueryService idxConf = (IndexQueryService)
            getTestCollection().getService("IndexQueryService", "1.0");
        assertNotNull(idxConf);
        idxConf.configureCollection(CONFIG);
        String[] wordList = DBUtils.wordList(rootCol);
        assertNotNull(wordList);
        tempFile = DBUtils.generateXMLFile(500, 10, wordList);
        DBUtils.addXMLResource(getTestCollection(), "R1.xml", tempFile);

        //String query0 = "xmldb:document('" + DBBroker.ROOT_COLLECTION + "/C1/R1.xml')/ROOT-ELEMENT//ELEMENT-1[@attribute-3]";
        //String query1 = "xmldb:document()/ROOT-ELEMENT//ELEMENT-2[@attribute-2]";

        addAction(new RemoveAppendAction(URI + "/C1", "R1.xml", wordList), 50, 0, 200);
        //addAction(new RemoveAppendAction(URI + "/C1", "R1.xml", wordList), 50, 100, 200);
        //addAction(new MultiResourcesAction("samples/mods", URI + "/C1"), 1, 0, 300);
        //addAction(new RetrieveResourceAction(URI + "/C1", "R1.xml"), 10, 1000, 2000);
        //addAction(new XQueryAction(URI + "/C1", "R1.xml", query0), 100, 100, 100);
        //addAction(new XQueryAction(URI + "/C1", "R1.xml", query1), 100, 200, 100);
	}

	@After
    @Override
	public void tearDown() throws XMLDBException {
        //super.tearDown();
        DBUtils.shutdownDB(URI);
        tempFile.delete();
	}
}
