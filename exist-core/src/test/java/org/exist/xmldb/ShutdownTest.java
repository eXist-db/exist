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

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.io.InputStreamUtil;
import org.exist.xmldb.concurrent.DBUtils;
import org.junit.*;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.exist.samples.Samples.SAMPLES;

/**
 * Check if database shutdownDB/restart works properly. The test opens
 * the database, stores a few files and queries them, then shuts down the
 * db.
 *  
 * @author wolf
 */
public class ShutdownTest {

	private final static int ITERATIONS = 50;
	
	protected final static String XML =
		"<config>" +
		"<user id=\"george\">" +
		"<phone>+49 69 888478</phone>" +
		"<email>george@email.com</email>" +
		"<customer-id>64534233</customer-id>" +
		"<reference>7466356</reference>" +
		"</user>" +
		"<user id=\"sam\">" +
		"<phone>+49 69 774345</phone>" +
		"<email>sam@email.com</email>" +
		"<customer-id>993834</customer-id>" +
		"<reference>364553</reference>" +
		"</user>" +
		"</config>";
	
	private static final String TEST_QUERY1 = "//user[@id = 'george']/phone[contains(., '69')]/text()";
	private static final String TEST_QUERY2 = "//user[@id = 'sam']/customer-id[. = '993834']";
	private static final String TEST_QUERY3 = "//user[email = 'sam@email.com']";

    @ClassRule
    public static final ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Before
    public void setUp() throws XMLDBException, IOException {
        final Collection rootCol = existXmldbEmbeddedServer.getRoot();
        Collection testCol = rootCol.getChildCollection("C1");
        if(testCol == null) {
            testCol = DBUtils.addCollection(rootCol, "C1");
            assertNotNull(testCol);
        }

        try (final InputStream is = SAMPLES.getBiblioSample()) {
			DBUtils.addXMLResource(rootCol, "biblio.rdf", InputStreamUtil.readString(is, UTF_8));
		}

        // store the data files
        final String xml =
                "<data now=\"" + System.currentTimeMillis() + "\" count=\"1\">" + XML + "</data>";
        DBUtils.addXMLResource(testCol, "R1.xml", xml);
    }

    @After
    public void tearDown() throws Exception {
        Collection rootCol = existXmldbEmbeddedServer.getRoot();
        DBUtils.removeCollection(rootCol, "C1");
        Resource res = rootCol.getResource("biblio.rdf");
        rootCol.removeResource(res);
    }

	@Test
	public void shutdown() throws Exception {
		for (int i = 0; i < ITERATIONS; i++) {
			existXmldbEmbeddedServer.restart();
			final Collection rootCol = existXmldbEmbeddedServer.getRoot();

			// after restarting the db, we first try a bunch of queries
			final Collection testCol = rootCol.getChildCollection("C1");

			ResourceSet result = DBUtils.query(testCol, TEST_QUERY1);
			assertEquals(1, result.getSize());
			assertEquals("+49 69 888478", result.getResource(0).getContent());

			result = DBUtils.query(testCol, TEST_QUERY2);
			assertEquals(1, result.getSize());

			result = DBUtils.query(testCol, TEST_QUERY3);
			assertEquals(1, result.getSize());
		}
	}
}
