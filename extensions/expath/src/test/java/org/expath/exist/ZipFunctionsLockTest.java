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
package org.expath.exist;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.LockTable;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each function of the EXPath Zip module that reads a zip stored in the database must release the
 * document's lock before it returns. A lock left behind is never released, and a later write to the
 * document from another thread waits for it forever.
 */
class ZipFunctionsLockTest {

    @RegisterExtension
    static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String COLLECTION = "/db/zip-lock-test";
    private static final String[] ZIP_NAMES = {"entries.zip", "update.zip", "entry.zip"};
    private static final String PROLOG = "declare namespace zip = 'http://expath.org/ns/zip';\n";

    @BeforeAll
    static void storeZip() throws EXistException, PermissionDeniedException, XPathException, IOException {
        final String zip = Base64.getEncoder().encodeToString(zipOf("a.txt", "alpha", "b.xml", "<b/>"));
        executeQuery("xmldb:create-collection('/db', 'zip-lock-test')");
        // one zip per test, so a lock left behind by one test can't show up in another
        for (final String name : ZIP_NAMES) {
            executeQuery("xmldb:store('" + COLLECTION + "', '" + name + "', xs:base64Binary('" + zip + "'), 'application/zip')");
        }
    }

    @Test
    void entriesReleasesTheDocumentLock() throws EXistException, PermissionDeniedException, XPathException {
        final String zipPath = COLLECTION + "/entries.zip";
        final Sequence result = executeQuery(PROLOG
                + "count(zip:entries(xs:anyURI('" + zipPath + "'))/zip:entry)");
        assertEquals("2", result.getStringValue());
        assertNoLockOn(zipPath);
    }

    @Test
    void updateReleasesTheDocumentLock() throws EXistException, PermissionDeniedException, XPathException {
        final String zipPath = COLLECTION + "/update.zip";
        final Sequence result = executeQuery(PROLOG
                + "zip:update(xs:anyURI('" + zipPath + "'), 'a.txt', util:string-to-binary('omega')) instance of xs:base64Binary");
        assertEquals("true", result.getStringValue());
        assertNoLockOn(zipPath);
    }

    @Test
    void entryFunctionsReleaseTheDocumentLock() throws EXistException, PermissionDeniedException, XPathException {
        final String zipPath = COLLECTION + "/entry.zip";
        final Sequence result = executeQuery(PROLOG
                + "(zip:text-entry(xs:anyURI('" + zipPath + "'), 'a.txt'), "
                + "string-length(util:binary-to-string(zip:binary-entry(xs:anyURI('" + zipPath + "'), 'a.txt'))))");
        assertEquals("alpha", result.itemAt(0).getStringValue());
        assertEquals("5", result.itemAt(1).getStringValue());
        assertNoLockOn(zipPath);
    }

    private static void assertNoLockOn(final String path) {
        final LockTable lockTable = existEmbeddedServer.getBrokerPool().getLockManager().getLockTable();
        final List<String> held = lockTable.getAcquired().keySet().stream()
                .filter(id -> id.endsWith(path))
                .toList();
        assertTrue(held.isEmpty(), "Locks still held after the query returned: " + held);
    }

    private static byte[] zipOf(final String... namesAndContents) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (final ZipOutputStream zos = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zos.putNextEntry(new ZipEntry(namesAndContents[i]));
                zos.write(namesAndContents[i + 1].getBytes(UTF_8));
                zos.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static Sequence executeQuery(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {
            final Sequence result = brokerPool.getXQueryService().execute(broker, query, null);
            transaction.commit();
            return result;
        }
    }
}
