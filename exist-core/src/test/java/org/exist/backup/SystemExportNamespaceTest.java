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
package org.exist.backup;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A backup export writes an element in the namespace it has: an element without a namespace that was inserted into a
 * document with a default namespace, and has an attribute with a prefix, stays without it (#7080, and #1724).
 */
public class SystemExportNamespaceTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @TempDir
    Path backups;

    private static void execute(final String query) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            final CompiledXQuery compiled = xquery.compile(new XQueryContext(pool), query);
            xquery.execute(broker, compiled, null);
        }
    }

    private String export() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Path backup;
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            backup = new SystemExport(broker, transaction, null, null, false)
                    .export(backups.toAbsolutePath().toString(), false, -1, false, null);
            transaction.commit();
        }
        assertNotNull(backup);
        try (final Stream<Path> files = Files.walk(backup)) {
            final Path exported = files.filter(p -> p.getFileName().toString().equals("export-namespace.xml")).findFirst().orElseThrow();
            return Files.readString(exported);
        }
    }

    @Test
    void anElementWithoutNamespaceInsertedIntoADefaultNamespaceStaysWithoutIt() throws Exception {
        execute("xmldb:store('/db', 'export-namespace.xml', '<root xmlns=\"foo\"><x/></root>')[false()]");
        execute("declare namespace foo = 'foo'; update insert <x xml:id=\"aargh\"/> into doc('/db/export-namespace.xml')/foo:root");

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final Element root = factory.newDocumentBuilder().parse(new InputSource(new StringReader(export()))).getDocumentElement();

        final Element first = (Element) root.getFirstChild();
        final Element inserted = (Element) root.getLastChild();
        assertEquals("foo", first.getNamespaceURI());
        assertNull(inserted.getNamespaceURI(), "the inserted element has no namespace");
        assertEquals("aargh", inserted.getAttributeNS("http://www.w3.org/XML/1998/namespace", "id"));
    }
}
