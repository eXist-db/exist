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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * When an update selects its targets again because their document was defragmented between selecting and locking, the
 * select expression is evaluated again, as the ids it gave are stale; the content expression is not, so side effects in
 * it happen once. The transaction of a caller must survive the retry.
 */
public class ModificationRetrySideEffectsTest {

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

    /** A document whose nodes after p=0 get other ids from a defragmentation, plus a collection and a log for side effects. */
    private static void store(final String collection) throws Exception {
        try (final DBBroker broker = broker()) {
            run(broker, "xmldb:create-collection('/db', '" + collection + "')");
            run(broker, "xmldb:create-collection('/db/" + collection + "', 'side')");
            run(broker, "xmldb:store('/db/" + collection + "', 'd.xml', parse-xml('<r><x p=\"0\"/><x p=\"1\"/><x p=\"2\"/><x p=\"3\"/><x p=\"4\"/><x p=\"5\"/><x p=\"6\"/><x p=\"7\"/></r>'))");
            run(broker, "xmldb:store('/db/" + collection + "', 'log.xml', <log/>)");
            run(broker, "update insert <x p=\"gap\"/> following doc('/db/" + collection + "/d.xml')/r/x[@p = '0']");
        }
    }

    /**
     * Runs the statement, in which {@code TARGET} selects x[@p=5] and then waits, and defragments the document during the wait,
     * so that the update has to select its targets again.
     */
    private static void runWhileDefragmenting(final String collection, final String statement, final boolean inOuterTransaction) throws Exception {
        final String document = "/db/" + collection + "/d.xml";
        final BrokerPool pool = SERVER.getBrokerPool();
        final String query = statement.replace("TARGET", "(let $t := doc('" + document + "')//x[@p = '5'] return (util:wait(600), $t))");
        final CompletableFuture<Void> updating = CompletableFuture.runAsync(() -> {
            try (final DBBroker broker = broker()) {
                if (inOuterTransaction) {
                    try (final Txn outer = pool.getTransactionManager().beginTransaction()) {
                        run(broker, query);
                        assertEquals(Txn.State.STARTED, outer.getState(), "the transaction of the caller is still open");
                        outer.commit();
                    }
                } else {
                    run(broker, query);
                }
            } catch (final Exception e) {
                throw new CompletionException(e);
            }
        });
        Thread.sleep(200);
        assertFalse(updating.isDone(), "the update was still waiting when the document was defragmented");
        try (final DBBroker broker = broker();
             final LockedDocument locked = broker.getXMLResource(XmldbURI.create(document), LockMode.WRITE_LOCK);
             final Txn txn = pool.getTransactionManager().beginTransaction()) {
            broker.defragXMLResource(txn, locked.getDocument());
            txn.commit();
        }
        updating.get();
    }

    private static String count(final String query) throws Exception {
        try (final DBBroker broker = broker()) {
            return run(broker, query);
        }
    }

    @Test
    void aStoreInTheContentExpressionHappensOnce() throws Exception {
        store("retry-content-store");
        runWhileDefragmenting("retry-content-store",
                "update insert (xmldb:store('/db/retry-content-store/side', util:uuid() || '.xml', <s/>), <x p=\"new\"/>)[2] following TARGET",
                false);
        assertEquals("5", count("doc('/db/retry-content-store/d.xml')//x[@p = 'new']/preceding-sibling::x[1]/@p/string()"),
                "premise: the insert landed after the selected node");
        assertEquals("1", count("count(xmldb:get-child-resources('/db/retry-content-store/side'))"),
                "the store in the content expression ran once");
    }

    /** The select expression has to run again, as its ids are stale: a side effect in it happens once per attempt. */
    @Test
    void aStoreInTheSelectExpressionRunsAgainWhenTheTargetsAreSelectedAgain() throws Exception {
        store("retry-select-store");
        runWhileDefragmenting("retry-select-store",
                "update insert <x p=\"new\"/> following (xmldb:store('/db/retry-select-store/side', util:uuid() || '.xml', <s/>)[0], TARGET)",
                false);
        assertEquals("5", count("doc('/db/retry-select-store/d.xml')//x[@p = 'new']/preceding-sibling::x[1]/@p/string()"),
                "premise: the insert landed after the selected node");
        assertEquals("2", count("count(xmldb:get-child-resources('/db/retry-select-store/side'))"),
                "the store in the select expression ran for each of the two attempts");
    }

    @Test
    void anUpdateInTheContentExpressionIsAppliedOnce() throws Exception {
        store("retry-content-update");
        runWhileDefragmenting("retry-content-update",
                "update insert (update insert <entry/> into doc('/db/retry-content-update/log.xml')/log, <x p=\"new\"/>) following TARGET",
                false);
        assertEquals("5", count("doc('/db/retry-content-update/d.xml')//x[@p = 'new']/preceding-sibling::x[1]/@p/string()"),
                "premise: the insert landed after the selected node");
        assertEquals("1", count("count(doc('/db/retry-content-update/log.xml')/log/entry)"),
                "the update in the content expression was applied once");
    }

    @Test
    void aDeleteInTheSelectExpressionIsAppliedAgainWhenTheTargetsAreSelectedAgain() throws Exception {
        store("retry-select-delete");
        count("update insert (<entry/>, <entry/>, <entry/>) into doc('/db/retry-select-delete/log.xml')/log");
        runWhileDefragmenting("retry-select-delete",
                "update delete ((update delete doc('/db/retry-select-delete/log.xml')/log/entry[1]), TARGET)",
                false);
        assertEquals("0,gap,1,2,3,4,6,7", count("doc('/db/retry-select-delete/d.xml')//x/@p/string()"),
                "premise: the selected node was deleted");
        assertEquals("1", count("count(doc('/db/retry-select-delete/log.xml')/log/entry)"),
                "the delete in the select expression removed an entry for each of the two attempts");
    }

    /** The ids of a stored node in the content are stale after the retry too, so it is copied when the content is evaluated. */
    @Test
    void contentFromAStoredNodeIsTheSameNodeAfterTheRetry() throws Exception {
        store("retry-content-stored");
        runWhileDefragmenting("retry-content-stored",
                "update insert doc('/db/retry-content-stored/d.xml')//x[@p = '7'] following TARGET",
                false);
        assertEquals("7", count("doc('/db/retry-content-stored/d.xml')//x[@p = '5']/following-sibling::x[1]/@p/string()"),
                "the copy of the node with p=7 follows the selected node");
        assertEquals("2", count("count(doc('/db/retry-content-stored/d.xml')//x[@p = '7'])"),
                "the node with p=7 and its copy");
    }

    /**
     * What the content expression gave is kept per evaluation: an update that the select expression runs, after the content
     * was evaluated, is the same expression and must not see it.
     */
    @Test
    void anUpdateInTheSelectOfItselfKeepsItsOwnContent() throws Exception {
        store("retry-recursive");
        count("declare function local:ins($d, $n) { if ($n le 0) then () else update insert <z n=\"{$n}\"/> into (local:ins($d, $n - 1), $d/r) }; "
                + "local:ins(doc('/db/retry-recursive/d.xml'), 3)");
        assertEquals("1,2,3", count("string-join(doc('/db/retry-recursive/d.xml')//z/@n/string(), ',')"),
                "each level inserted its own content");
    }

    @Test
    void earlierWorkInTheCallersTransactionSurvivesTheRetry() throws Exception {
        store("retry-outer");
        runWhileDefragmenting("retry-outer",
                "(xmldb:store('/db/retry-outer/side', 'before.xml', <b/>), update insert <x p=\"new\"/> following TARGET)",
                true);
        assertEquals("5", count("doc('/db/retry-outer/d.xml')//x[@p = 'new']/preceding-sibling::x[1]/@p/string()"),
                "the insert landed after the selected node");
        assertEquals("true", count("doc-available('/db/retry-outer/side/before.xml')"),
                "the store made earlier in the caller's transaction is there");
    }
}
