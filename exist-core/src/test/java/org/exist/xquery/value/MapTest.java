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
package org.exist.xquery.value;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MapTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(true, true, true);

    @Test
    public void effectiveBooleanValue() {
        try {
            final XQueryService queryService = (XQueryService) server.getRoot().getService("XQueryService", "1.0");
            queryService.query("fn:boolean(map{})");
        } catch(final XMLDBException e) {
           final Throwable cause = e.getCause();
           if(cause instanceof XPathException) {
               final XPathException xpe = (XPathException)cause;
               assertEquals(ErrorCodes.FORG0006, xpe.getErrorCode());
               return;
           }
        }
        fail("effectiveBooleanValue of a map should cause the error FORG0006");
    }
}
