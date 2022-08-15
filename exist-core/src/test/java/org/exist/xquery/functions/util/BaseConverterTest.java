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
package org.exist.xquery.functions.util;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * @author ljo
 */
public class BaseConverterTest {

    @ClassRule
    public static ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void testBaseConverterOctalToInt() throws XMLDBException {
        final String query = "util:base-to-integer(0755, 8)";
        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("493", r);
    }

    @Test
    public void testBaseConverterIntToHex() throws XMLDBException {
        final String query = "util:integer-to-base(10, 16)";
        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("a", r);
    }

    @Test
    public void testBaseConverterIntToBinary() throws XMLDBException {
        final String query = "util:integer-to-base(4, 2)";
        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("100", r);
    }

}