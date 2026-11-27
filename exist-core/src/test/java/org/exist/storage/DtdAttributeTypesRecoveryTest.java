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

import org.exist.collections.Collection;
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Crash recovery with the declarations of an internal subset, in the pattern of {@link AbstractRecoverTest}: the transactions run on threads of their own,
 * the journal is flushed, the database is restarted with {@link BrokerPool#FORCE_CORRUPTION} set, so that recovery has
 * to replay the journal. Committed changes of the ID entries are replayed, uncommitted ones are undone.
 */
public class DtdAttributeTypesRecoveryTest {

    @RegisterExtension
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String COMMITTED = "<!DOCTYPE r [<!ATTLIST c id ID #IMPLIED><!ATTLIST c ref IDREF #IMPLIED>]><r><c id='x'/><c id='y' ref='x'/></r>";

    private static final String UNCOMMITTED = "<!DOCTYPE r [<!ATTLIST c uid ID #IMPLIED>]><r><c uid='u1'/></r>";

    private static final String DECLARATIONS = "<!ATTLIST c id ID #IMPLIED><!ATTLIST c ref IDREF #IMPLIED>";

    @AfterEach
    void resetCorruption() {
        BrokerPool.FORCE_CORRUPTION = false;
    }

    private interface Task {
        void run(DBBroker broker, Txn transaction) throws Exception;
    }

    private void inThread(final boolean commit, final Task task) throws Exception {
        final Throwable[] error = new Throwable[1];
        final Thread thread = new Thread(() -> {
            final BrokerPool pool = existEmbeddedServer.getBrokerPool();
            try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
                final Txn transaction = pool.getTransactionManager().beginTransaction();
                task.run(broker, transaction);
                if (commit) {
                    transaction.commit();
                    transaction.close();
                } else {
                    broker.removeCurrentTransaction(transaction);
                }
            } catch (final Throwable t) {
                error[0] = t;
            }
        });
        thread.start();
        thread.join();
        if (error[0] != null) {
            throw new IllegalStateException(error[0]);
        }
    }

    private void store(final boolean commit, final String name, final String xml) throws Exception {
        inThread(commit, (broker, transaction) -> {
            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);
            broker.storeDocument(transaction, XmldbURI.create(name), new StringInputSource(xml), MimeType.XML_TYPE, root);
        });
    }

    private String run(final String query) throws Exception {
        return DtdAttributeTypesTestSupport.run(existEmbeddedServer, query);
    }

    private String declarations(final String name) throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument locked = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI.append(name), LockMode.READ_LOCK)) {
            return locked == null ? "absent" : String.valueOf(locked.getDocument().getDoctype().getInternalSubset());
        }
    }

    private String ids() throws Exception {
        return run("string-join(('x=' || count(for $d in collection('/db/test') return id('x', $d)), "
                + "'u1=' || count(for $d in collection('/db/test') return id('u1', $d))), ' ')");
    }

    private String idrefs() throws Exception {
        return run("'refs=' || count(for $d in collection('/db/test') return idref('x', $d))");
    }

    private void crashAndRecover() throws Exception {
        existEmbeddedServer.getBrokerPool().getJournalManager().get().flush(true, false);
        BrokerPool.FORCE_CORRUPTION = true;
        existEmbeddedServer.restart();
        BrokerPool.FORCE_CORRUPTION = false;
    }

    /**
     * The declarations are part of the record of the document that the journal replays: a committed document keeps them
     * across a crash, an uncommitted one is gone, and the types they give are found by {@code fn:id}.
     */
    @Test
    void committedDeclarationsSurviveACrash() throws Exception {
        BrokerPool.FORCE_CORRUPTION = true;
        store(true, "s1.xml", COMMITTED);
        store(false, "s2.xml", UNCOMMITTED);
        assertEquals(DECLARATIONS + " | <!ATTLIST c uid ID #IMPLIED> | x=1 u1=1", declarations("s1.xml") + " | " + declarations("s2.xml") + " | " + ids(),
                "before the crash");

        crashAndRecover();
        assertEquals(DECLARATIONS + " | absent | x=1 u1=0", declarations("s1.xml") + " | " + declarations("s2.xml") + " | " + ids(),
                "after recovery: the committed document, with its declarations, and not the other");

        existEmbeddedServer.restart();
        assertEquals(DECLARATIONS + " | absent | x=1 u1=0", declarations("s1.xml") + " | " + declarations("s2.xml") + " | " + ids(),
                "and after another restart");
    }

    /**
     * After a crash the indexes are rebuilt from the stored nodes, so an IDREF only keeps its entry if the type that is
     * read back from the stored attribute is IDREF. That is not the case until the type is decoded with both of its bits,
     * which is a separate change.
     */
    @Disabled("After a crash an IDREF is read back as CDATA, until the attribute type is decoded with both bits, see #7070")
    @Test
    void idrefsAreFoundAfterACrash() throws Exception {
        BrokerPool.FORCE_CORRUPTION = true;
        store(true, "s1.xml", COMMITTED);
        assertEquals("refs=1", idrefs(), "before the crash");

        crashAndRecover();
        assertEquals("refs=1", idrefs(), "after recovery");

        existEmbeddedServer.restart();
        assertEquals("refs=1", idrefs(), "and after another restart");
    }
}
