/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2009 The eXist Project
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
 *  $Id: BaseConverterTest.java 10599 2009-11-26 05:23:12Z shabanovd $
 */
package org.exist.xquery.functions.util;

import org.exist.test.ExistXmldbEmbeddedServer;

import static org.junit.Assert.*;

import org.junit.ClassRule;
import org.junit.Test;

import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;


/**
 * DOCUMENT ME!
 *
 * @author Andrzej Taramina (andrzej@chaeron.com)
 */
public class Base64FunctionsTest {

    @ClassRule
    public static ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void testBase64Encode() throws XMLDBException {
        final String query = "util:base64-encode( 'This is a test!' )";
        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("VGhpcyBpcyBhIHRlc3Qh", r);
    }

    @Test
    public void testBase64Decode() throws XMLDBException {
        final String query = "util:base64-decode( 'VGhpcyBpcyBhIHRlc3Qh' )";
        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("This is a test!", r);
    }

    @Test
    public void testBase64EncodeDecode() throws XMLDBException {
        final String query = "util:base64-decode( util:base64-encode( 'This is a test!' ) )";
        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("This is a test!", r);
    }


}
