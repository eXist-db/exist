/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.xmldb;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.concurrent.DBUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import static org.junit.Assert.*;

/**
 * Due to limitation of ExistXmldbEmbeddedServer we need to split this test to two files.
 * It's not possible to have two instances of ExistXmldbEmbeddedServer at the same time.
 */
public class DbStoreTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private final static String TEST_COLLECTION = "testAnyUri";

    @Test
    public final void simpleTest() throws XMLDBException {
        final Collection rootCol = existEmbeddedServer.getRoot();
        Collection testCol = rootCol.getChildCollection(TEST_COLLECTION);
        if (testCol == null) {
            testCol = DBUtils.addCollection(rootCol, TEST_COLLECTION);
            assertNotNull(testCol);
        }

        final XPathQueryService xpqs =
                (XPathQueryService) testCol.getService("XPathQueryService", "1.0");
        assertThrows(XMLDBException.class, () -> {
                    xpqs.query(
                            "xmldb:store(\n" +
                                    "        '/db',\n" +
                                    "        'image.jpg',\n" +
                                    "        xs:anyURI('https://www.example.com/image.jpg'),\n" +
                                    "        'image/png'\n" +
                                    "    )");
        });


    }

}
