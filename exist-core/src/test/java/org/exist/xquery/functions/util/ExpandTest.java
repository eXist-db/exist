/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  $Id$
 */
package org.exist.xquery.functions.util;

import static org.junit.Assert.*;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xquery.XPathException;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Casey Jordan
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ExpandTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void testExpandWithDefaultNS() throws XPathException, XMLDBException {
    	final String expected = "<ok xmlns=\"some\">\n    <concept xmlns=\"\"/>\n</ok>";

        String query = "" +
                "let $doc-path := xmldb:store('/db', 'test.xml', <concept/>)\n" +
                "let $doc := doc($doc-path)\n" +
                "return\n" +
                "<ok xmlns='some'>\n" +
                "{util:expand($doc)}\n" +
                "</ok>";
        ResourceSet result = existEmbeddedServer.executeQuery(query);
        String r = (String) result.getResource(0).getContent();
        assertEquals(expected, r);

        query = "" +
                "let $doc-path := xmldb:store('/db', 'test.xml', <concept/>)\n" +
                "let $doc := doc($doc-path)\n" +
                "return\n" +
                "<ok xmlns='some'>\n" +
                "{$doc}\n" +
                "</ok>";
        result = existEmbeddedServer.executeQuery(query);
        r = (String) result.getResource(0).getContent();
        assertEquals(expected, r);
    }
}
