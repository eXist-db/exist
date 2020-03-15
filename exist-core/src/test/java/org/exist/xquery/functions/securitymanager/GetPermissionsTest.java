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
package org.exist.xquery.functions.securitymanager;

import org.exist.EXistException;
import org.exist.dom.memtree.ElementImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.storage.BrokerPool;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class GetPermissionsTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    /**
     * See https://github.com/eXist-db/exist/issues/3231
     */
    @Test
    public void getPermissionsNestedXml() throws EXistException, PermissionDeniedException, XPathException {
        final String query = "<outer><inner perm=\"{sm:get-permissions(xs:anyURI(\"/db\"))/sm:permission/@owner}\"/></outer>";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {

            final XQuery xquery = existEmbeddedServer.getBrokerPool().getXQueryService();
            final Sequence result = xquery.execute(broker, query, null);

            assertEquals(1, result.getItemCount());

            final Source expected = Input.fromString("<outer><inner perm=\"" + SecurityManagerImpl.SYSTEM + "\"/></outer>").build();
            final Source actual = Input.fromDocument(((ElementImpl) result.itemAt(0)).getOwnerDocument()).build();

            final Diff diff = DiffBuilder
                    .compare(expected)
                    .withTest(actual)
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());
        }
    }
}
