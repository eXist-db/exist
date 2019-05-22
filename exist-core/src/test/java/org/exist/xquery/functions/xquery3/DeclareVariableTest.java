/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2017 The eXist Project
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
 */
package org.exist.xquery.functions.xquery3;

import static org.junit.Assert.assertEquals;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

public class DeclareVariableTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void defaultNamespaceTest() throws XMLDBException {
        final String query = "xquery version \"3.1\";\n"
            + "\n"
            + "    declare default element namespace \"http://www.w3.org/1999/xhtml\";\n"
            + "\n"
            + "    declare variable $docName := 'test.xml';\n"
            + "    declare variable $docPath := concat('/db/', $docName);\n"
            + "\n"
            + "    $docPath";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        final String r = (String) results.getResource(0).getContent();

        assertEquals("/db/test.xml", r);
    }
}
