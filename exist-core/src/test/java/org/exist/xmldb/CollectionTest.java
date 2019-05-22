/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist-db Project
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id: BinaryResourceUpdateTest.java 11148 2010-02-07 14:37:35Z dizzzz $
 */
package org.exist.xmldb;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.test.TestConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

public class CollectionTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Before
    public void setup() throws XMLDBException {
        final CollectionManagementService service = (CollectionManagementService)existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        service.createCollection(TestConstants.SPECIAL_NAME);
    }

    @After
    public void cleanup() throws XMLDBException {
        final CollectionManagementService service = (CollectionManagementService)existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        service.removeCollection(TestConstants.SPECIAL_NAME);
    }

    @Test
    public void testRead() throws XMLDBException {
        final Collection test = existEmbeddedServer.getRoot().getChildCollection(TestConstants.SPECIAL_NAME);
        assertNotNull(test);
    }
}
