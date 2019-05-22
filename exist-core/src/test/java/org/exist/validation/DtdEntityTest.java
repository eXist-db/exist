/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.validation;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.*;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * Small test to show than entities are required to be resolved.
 * 
 * @author wessels
 */
public class DtdEntityTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void loadWithEntities() throws XMLDBException {
        final String input = "<a>first empty: &empty; then trade: &trade; </a>";

        Collection col = null;
        try {
            col = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "entity");
            ExistXmldbEmbeddedServer.storeResource(col, "docname.xml", input.getBytes());

            // should throw XMLDBException
            ExistXmldbEmbeddedServer.getXMLResource(col, "docname.xml");

        } catch (final XMLDBException e) {
            assertTrue(e.getMessage().contains("The entity \"empty\" was referenced, but not declared"));
            return;
        } finally {
            if(col != null) {
                col.close();
            }
        }

        fail("Should have thrown XMLDBException");
    }

    @Test @Ignore("Entity resolve bug")
    public void bugloadWithEntities() throws XMLDBException {
        final String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE procedure PUBLIC \"-//AAAA//DTD Procedure 0.4//EN\" \"aaaa.dtd\" >"
                + "<a>first empty: &empty; then trade: &trade; </a>";

        Collection col = null;
        try {
            col = existEmbeddedServer.createCollection(existEmbeddedServer.getRoot(), "entity");
            ExistXmldbEmbeddedServer.storeResource(col, "docname.xml", input.getBytes(UTF_8));

            // should throw XMLDBException
            ExistXmldbEmbeddedServer.getXMLResource(col, "docname.xml");

        } catch (final XMLDBException e) {
            assertTrue(e.getMessage().contains("The entity \"empty\" was referenced, but not declared"));
            return;
        } finally {
            if(col != null) {
                col.close();
            }
        }

        fail("Should have thrown XMLDBException");
    }
}
