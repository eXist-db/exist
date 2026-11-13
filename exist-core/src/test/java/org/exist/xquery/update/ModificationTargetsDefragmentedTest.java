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
package org.exist.xquery.update;

import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An update selects its targets first and locks their documents afterwards. If the document is defragmented in between,
 * its nodes have other ids, and the ids the update holds name other nodes. The update must apply to the nodes it selected.
 *
 * <p>The gap between selecting and locking is held open with {@code util:wait} after the targets are bound, and the
 * document is defragmented while the update waits. Before the nodes of the document were given new ids by the
 * defragmentation, an insert inserted after, and a delete deleted, the node before the one that was selected.</p>
 */
public class ModificationTargetsDefragmentedTest {

    @RegisterExtension
    static final ExistEmbeddedServer SERVER = new ExistEmbeddedServer(true, true);

    private static String run(final DBBroker broker, final String query) throws Exception {
        final XQuery xquery = SERVER.getBrokerPool().getXQueryService();
        final Sequence result = xquery.execute(broker, query, null);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < result.getItemCount(); i++) {
            out.append(i == 0 ? "" : ",").append(result.itemAt(i).getStringValue());
        }
        return out.toString();
    }

    private static DBBroker broker() throws Exception {
        final BrokerPool pool = SERVER.getBrokerPool();
        return pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
    }

    /** Stores ten elements and inserts one more before the target, which gives the nodes after it ids that a defragmentation changes. */
    private static void store(final String collection) throws Exception {
        try (final DBBroker broker = broker()) {
            run(broker, "xmldb:create-collection('/db', '" + collection + "')");
            run(broker, "xmldb:store('/db/" + collection + "', 'd.xml', parse-xml('<r><x p=\"0\"/><x p=\"1\"/><x p=\"2\"/><x p=\"3\"/><x p=\"4\"/><x p=\"5\"/><x p=\"6\"/><x p=\"7\"/><x p=\"8\"/><x p=\"9\"/></r>'))");
            run(broker, "update insert <x p=\"gap\"/> following doc('/db/" + collection + "/d.xml')/r/x[@p = '0']");
        }
    }

    /**
     * Runs an update on the node with p=5 and defragments the document while the update has selected it and not yet locked.
     * Checks that the premises hold: the update was still waiting when the document was defragmented, and the defragmentation
     * gave the node another id.
     *
     * @param inOuterTransaction whether the update continues a transaction that its caller began
     */
    private static void updateWhileDefragmenting(final String collection, final String update, final boolean inOuterTransaction) throws Exception {
        final String document = "/db/" + collection + "/d.xml";
        final BrokerPool pool = SERVER.getBrokerPool();
        final String idBefore;
        try (final DBBroker broker = broker()) {
            idBefore = run(broker, "util:node-id(doc('" + document + "')//x[@p = '5'])");
        }
        final CompletableFuture<Void> updating = CompletableFuture.runAsync(() -> {
            try (final DBBroker broker = broker()) {
                final String statement = update.replace("TARGET", "(let $t := doc('" + document + "')//x[@p = '5'] return (util:wait(600), $t))");
                if (inOuterTransaction) {
                    try (final Txn outer = pool.getTransactionManager().beginTransaction()) {
                        run(broker, statement);
                        assertEquals(Txn.State.STARTED, outer.getState(), "selecting the targets again did not close the transaction of the caller");
                        outer.commit();
                    }
                } else {
                    run(broker, statement);
                }
            } catch (final Exception e) {
                throw new CompletionException(e);
            }
        });
        Thread.sleep(200);
        assertFalse(updating.isDone(), "the update was still waiting between selecting and locking when the document was defragmented");
        try (final DBBroker broker = broker();
             final LockedDocument locked = broker.getXMLResource(XmldbURI.create(document), LockMode.WRITE_LOCK);
             final Txn txn = pool.getTransactionManager().beginTransaction()) {
            broker.defragXMLResource(txn, locked.getDocument());
            txn.commit();
        }
        try (final DBBroker broker = broker()) {
            assertNotEquals(idBefore, run(broker, "util:node-id(doc('" + document + "')//x[@p = '5'])"), "the defragmentation gave the node another id");
        }
        updating.get();
    }

    @Test
    void anInsertAppliesToTheSelectedNodeWhenTheDocumentIsDefragmentedMeanwhile() throws Exception {
        store("stale-insert");
        final String loggerName = Modification.class.getName();
        final List<String> infos = new CopyOnWriteArrayList<>();
        final AbstractAppender capture = new AbstractAppender("stale-targets-capture", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(final LogEvent event) {
                if (event.getLevel() == Level.INFO) {
                    infos.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        capture.start();
        final org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(Modification.class);
        final Level before = logger.getLevel();
        logger.addAppender(capture);
        Configurator.setLevel(loggerName, Level.INFO);
        try {
            updateWhileDefragmenting("stale-insert", "update insert <x p=\"new\"/> following TARGET", false);
        } finally {
            Configurator.setLevel(loggerName, before);
            logger.removeAppender(capture);
            capture.stop();
        }
        try (final DBBroker broker = broker()) {
            assertEquals("5", run(broker, "doc('/db/stale-insert/d.xml')//x[@p = 'new']/preceding-sibling::x[1]/@p/string()"),
                    "the new element follows the element that was selected");
        }
        assertTrue(infos.stream().anyMatch(message -> message.contains("selected 2 times")), "selecting the targets again was logged: " + infos);
    }

    @Test
    void anInsertInAnOuterTransactionAppliesToTheSelectedNodeAndLeavesTheTransactionUsable() throws Exception {
        store("stale-outer");
        updateWhileDefragmenting("stale-outer", "update insert <x p=\"new\"/> following TARGET", true);
        try (final DBBroker broker = broker()) {
            assertEquals("5", run(broker, "doc('/db/stale-outer/d.xml')//x[@p = 'new']/preceding-sibling::x[1]/@p/string()"),
                    "the new element follows the element that was selected, and the outer transaction was committed");
        }
    }

    @Test
    void aDeleteAppliesToTheSelectedNodeWhenTheDocumentIsDefragmentedMeanwhile() throws Exception {
        store("stale-delete");
        updateWhileDefragmenting("stale-delete", "update delete TARGET", false);
        try (final DBBroker broker = broker()) {
            assertEquals("0,gap,1,2,3,4,6,7,8,9", run(broker, "doc('/db/stale-delete/d.xml')//x/@p/string()"),
                    "the element that was selected is gone and no other");
        }
    }
}
