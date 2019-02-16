/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
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
package org.exist.xupdate;

import java.io.IOException;
import java.util.Random;
import java.util.stream.IntStream;

import org.exist.EXistException;
import org.exist.TestUtils;

import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.DBUtils;
import org.junit.After;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * @author wolf
 */
public class StressTest {

    private static final String XML = "<root><a/><b/><c/></root>";

    private final static int RUNS = 1000;

    private Collection testCol;
    private final Random rand = new Random();

    private String[] tags;

    @ClassRule
    public static final ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void stressTest() throws XMLDBException {
        insertTags();
        removeTags();
        fetchDb();
    }

    private void insertTags() throws XMLDBException {
        final XUpdateQueryService service = (XUpdateQueryService)
                testCol.getService("XUpdateQueryService", "1.0");
        final XPathQueryService xquery = (XPathQueryService)
                testCol.getService("XPathQueryService", "1.0");

        final String[] tagsWritten = new String[RUNS];
        for (int i = 0; i < RUNS; i++) {
            final String tag = tags[i];
            final String parent;
            if (i > 0 && rand.nextInt(100) < 70) {
                parent = "//" + tagsWritten[rand.nextInt(i) / 2];
            } else {
                parent = "/root";
            }
            final String xupdate =
                    "<xupdate:modifications version=\"1.0\" xmlns:xupdate=\"http://www.xmldb.org/xupdate\">" +
                            "<xupdate:append select=\"" + parent + "\">" +
                            "<xupdate:element name=\"" + tag + "\"/>" +
                            "</xupdate:append>" +
                            "</xupdate:modifications>";

            final long mods = service.updateResource("test.xml", xupdate);
            assertEquals(mods, 1);

            tagsWritten[i] = tag;

            final String query = "//" + tagsWritten[rand.nextInt(i + 1)];
            final ResourceSet result = xquery.query(query);
            assertEquals(result.getSize(), 1);
        }

        final XMLResource res = (XMLResource) testCol.getResource("test.xml");
        assertNotNull(res);
    }

    private void removeTags() throws XMLDBException {
        final XUpdateQueryService service = (XUpdateQueryService)
                testCol.getService("XUpdateQueryService", "1.0");
        final int start = rand.nextInt(RUNS / 4);
        for (int i = start; i < RUNS; i++) {
            final String xupdate =
                    "<xupdate:modifications version=\"1.0\" xmlns:xupdate=\"http://www.xmldb.org/xupdate\">" +
                            "<xupdate:remove select=\"//" + tags[i] + "\"/>" +
                            "</xupdate:modifications>";

            @SuppressWarnings("unused")
            long mods = service.updateResource("test.xml", xupdate);

            i += rand.nextInt(3);
        }
    }

    private void fetchDb() throws XMLDBException {
        final XPathQueryService xquery = (XPathQueryService)
                testCol.getService("XPathQueryService", "1.0");
        final ResourceSet result = xquery.query("for $n in collection('" + XmldbURI.ROOT_COLLECTION + "/test')//* return local-name($n)");

        for (int i = 0; i < result.getSize(); i++) {
            final Resource r = result.getResource(i);
            final String tag = r.getContent().toString();

            final ResourceSet result2 = xquery.query("//" + tag);
            assertEquals(result2.getSize(), 1);
        }
    }

    @Before
    public void setUp() throws XMLDBException {
        final Collection rootCol = existXmldbEmbeddedServer.getRoot();
        testCol = rootCol.getChildCollection(XmldbURI.ROOT_COLLECTION + "/test");
        if (testCol != null) {
            final CollectionManagementService mgr = DBUtils.getCollectionManagementService(rootCol);
            mgr.removeCollection(XmldbURI.ROOT_COLLECTION + "/test");
        }

        testCol = DBUtils.addCollection(rootCol, "test");
        assertNotNull(testCol);

        tags = IntStream
                .range(0, RUNS)
                .mapToObj(i -> "TAG" + i)
                .toArray(String[]::new);

        DBUtils.addXMLResource(testCol, "test.xml", XML);
    }

    @After
    public void tearDown() throws XMLDBException, LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
        TestUtils.cleanupDB();
    }
}
