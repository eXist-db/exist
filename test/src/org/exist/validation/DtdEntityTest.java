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

import org.exist.test.EmbeddedExistTester;
import static org.junit.Assert.*;
import org.junit.*;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * Small test to show than entities are required to be resolved.
 * 
 * @author wessels
 */
public class DtdEntityTest extends EmbeddedExistTester {

    @Test
    public void loadWithEntities() {

        String input = "<a>first empty: &empty; then trade: &trade; </a>";
        try {
            Collection col = createCollection(rootCollection, "entitiy");
            storeResource(col, "docname.xml", input.getBytes());

            String result = getXMLResource(col, "docname.xml");
            fail("Exception expected");

        } catch (XMLDBException ex) {
            assertTrue(ex.getMessage().contains("The entity \"empty\" was referenced, but not declared"));


        } catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

    }

    @Test @Ignore("Enitiy resolve bug")
    public void bugloadWithEntities() {

        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" 
                + "<!DOCTYPE procedure PUBLIC \"-//AAAA//DTD Procedure 0.4//EN\" \"aaaa.dtd\" >"
                + "<a>first empty: &empty; then trade: &trade; </a>";
        try {
            Collection col = createCollection(rootCollection, "entitiy");
            storeResource(col, "docname.xml", input.getBytes());

            String result = getXMLResource(col, "docname.xml");
            fail("Exception expected, document should be rejected");

        } catch (XMLDBException ex) {
            assertTrue(ex.getMessage().contains("The entity \"empty\" was referenced, but not declared"));


        } catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

    }
}
