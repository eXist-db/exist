/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2011 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id: SwitchTest.java 13849 2011-02-26 16:29:17Z dizzzz $
 */
package org.exist.xquery.functions.xquery3;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.xmldb.api.base.ResourceSet;

import org.junit.Test;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.*;

/**
 *
 * @author ljo
 */
public class SwitchTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void oneCaseCaseMatch() throws XMLDBException {
        final String query = "xquery version '3.0';"
                + "let $animal := 'Cat' return "
                + "switch ($animal)"
                + "case 'Cow' return 'Moo'"
                + "case 'Cat' return 'Meow'"
                + "case 'Duck' return 'Quack'"
                + "default return 'Odd noise!'";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        final String r = (String) results.getResource(0).getContent();
        assertEquals("Meow", r);
    }

    @Test
    public void twoCaseDefault() throws XMLDBException {
        final String query = "xquery version '3.0';"
                + "let $animal := 'Cat' return "
                + "switch ($animal)"
                + "case 'Cow' case 'Calf' return 'Moo'"
                + "default return 'No Bull?'";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        final String r = (String) results.getResource(0).getContent();
        assertEquals("No Bull?", r);
    }

    @Test
    public void twoCaseCaseMatch() throws XMLDBException {
        final String query = "xquery version '3.0';"
                + "let $animal := 'Calf' return "
                + "switch ($animal)"
                + "case 'Cow' case 'Calf' return 'Moo'"
                + "case 'Cat' return 'Meow'"
                + "case 'Duck' return 'Quack'"
                + "default return 'Odd noise!'";

        final ResourceSet results = existEmbeddedServer.executeQuery(query);
        final String r = (String) results.getResource(0).getContent();
        assertEquals("Moo", r);
    }
}
