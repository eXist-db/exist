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
package org.exist.collections;

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Measures removing a Collection of {@code documents} small XML documents, the way a REST or XML:DB removal
 * does it: open the Collection with a WRITE_LOCK, {@link DBBroker#removeCollection}, commit.
 *
 * <p>Each measured call removes a Collection that the iteration's setup has just stored, so the mode is
 * single shot: one removal per iteration.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10)
@Measurement(iterations = 30)
@Fork(value = 1, jvmArgsAppend = {"-Xmx2g"})
public class RemoveCollectionBenchmark {

    private static final XmldbURI COLLECTION = XmldbURI.create("/db/remove-collection-benchmark");

    @Param({"10", "100", "1000"})
    public int documents;

    private LifecycleEmbeddedServer server;
    private BrokerPool pool;

    @Setup(Level.Trial)
    public void startDatabase() throws Exception {
        server = new LifecycleEmbeddedServer();
        server.startDb();
        pool = server.getBrokerPool();
    }

    @TearDown(Level.Trial)
    public void stopDatabase() {
        if (server != null) {
            server.stopDb();
            server = null;
        }
    }

    @Setup(Level.Iteration)
    public void storeCollection() throws Exception {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION);
            broker.saveCollection(transaction, collection);
            for (int i = 1; i <= documents; i++) {
                final String content = """
                        <doc n="%d"><title>Document %d</title><p>Some text for document %d.</p></doc>
                        """.formatted(i, i, i);
                broker.storeDocument(transaction, XmldbURI.create("d" + i + ".xml"), new StringInputSource(content),
                        MimeType.XML_TYPE, collection);
            }
            transaction.commit();
        }
    }

    @Benchmark
    public void removeCollection() throws Exception {
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            try (final Collection collection = broker.openCollection(COLLECTION, LockMode.WRITE_LOCK)) {
                if (collection == null) {
                    throw new IllegalStateException("The iteration's Collection is missing: " + COLLECTION);
                }
                broker.removeCollection(transaction, collection);
            }
            transaction.commit();
        }
    }

    private static final class LifecycleEmbeddedServer extends ExistEmbeddedServer {
        LifecycleEmbeddedServer() {
            super(true, true);
        }
    }
}
