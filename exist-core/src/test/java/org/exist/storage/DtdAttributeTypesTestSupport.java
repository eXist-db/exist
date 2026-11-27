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
package org.exist.storage;

import org.exist.TestUtils;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.DatabaseImpl;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.xmldb.api.DatabaseManager;

import java.util.Optional;

/**
 * What the tests of the attribute types of a DTD share: running a query as the system user, and the content of a stored
 * document as the XML:DB API serializes it, which is what a backup writes.
 */
final class DtdAttributeTypesTestSupport {

    private DtdAttributeTypesTestSupport() {
    }

    /** Runs the query, with the xmldb module imported, and joins the items of the result with a space. */
    static String run(final ExistEmbeddedServer server, final String query) throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final String prolog = "import module namespace xmldb = 'http://exist-db.org/xquery/xmldb'; ";
            final Sequence seq = xquery.execute(broker, xquery.compile(new XQueryContext(pool), prolog + query), null);
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < seq.getItemCount(); i++) {
                sb.append(i == 0 ? "" : " ").append(seq.itemAt(i).getStringValue());
            }
            return sb.toString();
        }
    }

    /** The content of a stored document, with its DOCTYPE, as a backup writes it. */
    static String content(final String collection, final String name) throws Exception {
        DatabaseManager.registerDatabase(new DatabaseImpl());
        final org.xmldb.api.base.Collection col = DatabaseManager.getCollection(
                XmldbURI.LOCAL_DB + collection.substring("/db".length()), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        try {
            return (String) col.getResource(name).getContent();
        } finally {
            col.close();
        }
    }
}
