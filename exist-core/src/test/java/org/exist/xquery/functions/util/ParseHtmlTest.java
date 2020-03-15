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

import org.exist.EXistException;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;

import static org.junit.Assert.*;

public class ParseHtmlTest {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    @Test
    public void parseHtml() throws EXistException, PermissionDeniedException, XPathException {
        final String query = "util:parse-html(\"<p>hello <img src='1.jpg'></p>\")";

        final XQuery xquery = server.getBrokerPool().getXQueryService();
        try (final DBBroker broker = server.getBrokerPool().getBroker()) {
            final Sequence result = xquery.execute(broker, query, null);
            assertEquals(1, result.getItemCount());
            assertTrue(result.itemAt(0) instanceof DocumentImpl);

            final Source expected = Input.fromString("<?xml version=\"1.0\" encoding=\"UTF-8\"?><HTML><head xmlns=\"http://www.w3.org/1999/xhtml\"/><BODY><p>hello <img src=\"1.jpg\"/></p></BODY></HTML>").build();
            final Source actual = Input.fromDocument((DocumentImpl) result.itemAt(0)).build();

            final Diff diff = DiffBuilder
                    .compare(expected)
                    .withTest(actual)
                    .checkForIdentical()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());
        }
    }
}
