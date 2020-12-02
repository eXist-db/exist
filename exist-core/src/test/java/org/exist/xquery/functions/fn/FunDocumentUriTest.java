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
package org.exist.xquery.functions.fn;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xquery.XPathException;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;

/**
 * @author Dannes Wessels
 */
@RunWith(ParallelRunner.class)
public class FunDocumentUriTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(true, true, true);

    @Test
    public void testFnFunDocumentUri() {

        try {
            final ResourceSet resourceSet = existEmbeddedServer.executeQuery(
                    "declare context item := 'a'; document-uri()");

        } catch (XMLDBException ex) {
            XPathException cause = (XPathException) ex.getCause();
            assertEquals("XPTY0004", cause.getErrorCode().getErrorQName().getLocalPart());
        }
    }
}
