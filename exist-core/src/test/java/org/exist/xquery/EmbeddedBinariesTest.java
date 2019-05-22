/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery;

import org.exist.collections.Collection;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.*;
import org.junit.ClassRule;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class EmbeddedBinariesTest extends AbstractBinariesTest<Sequence, Item, IOException> {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Override
    protected void storeBinaryFile(final XmldbURI filePath, final byte[] content) throws Exception {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
            final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            try(final ManagedCollectionLock collectionLock = brokerPool.getLockManager().acquireCollectionWriteLock(filePath.removeLastSegment())) {
                final Collection collection = broker.getOrCreateCollection(transaction, filePath.removeLastSegment());
                try(final InputStream is = new FastByteArrayInputStream(content)) {

                    collection.addBinaryResource(transaction, broker, filePath.lastSegment(), is, "application/octet-stream", content.length);

                    broker.saveCollection(transaction, collection);

                }
            }

            transaction.commit();
        }
    }

    @Override
    protected void removeCollection(final XmldbURI collectionUri) throws Exception {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
            final Txn transaction = brokerPool.getTransactionManager().beginTransaction();
            final Collection collection = broker.openCollection(collectionUri, Lock.LockMode.WRITE_LOCK)) {
            if(collection != null) {
                broker.removeCollection(transaction, collection);
            }

            transaction.commit();
        }
    }

    @Override
    protected QueryResultAccessor<Sequence, IOException> executeXQuery(final String query) throws Exception {
        final Source source = new StringSource(query);
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final XQueryPool pool = brokerPool.getXQueryPool();
        final XQuery xquery = brokerPool.getXQueryService();

        try(final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final CompiledXQuery existingCompiled = pool.borrowCompiledXQuery(broker, source);

            final XQueryContext context;
            final CompiledXQuery compiled;
            if (existingCompiled == null) {
                context = new XQueryContext(brokerPool);
                compiled = xquery.compile(broker, context, source);
            } else {
                context = existingCompiled.getContext();
                context.prepareForReuse();
                compiled = existingCompiled;
            }

            final Sequence results = xquery.execute(broker, compiled, null);

            return consumer2E -> {
                try {
//                    context.runCleanupTasks();  //TODO(AR) shows the ordering issue with binary values (see comment below)

                    consumer2E.accept(results);
                } finally {
                    //TODO(AR) performing #runCleanupTasks causes the stream to be closed, so if we do so before we are finished with the results, serialization fails.
                    context.runCleanupTasks();
                    pool.returnCompiledXQuery(source, compiled);
                }
            };
        }
    }

    @Override
    protected long size(final Sequence results) {
        return results.getItemCount();
    }

    @Override
    protected Item item(final Sequence results, final int index) {
        return results.itemAt(index);
    }

    @Override
    protected boolean isBinaryType(final Item item) {
        return Type.BASE64_BINARY == item.getType() || Type.HEX_BINARY == item.getType();
    }

    @Override
    protected boolean isBooleanType(final Item item) throws IOException {
        return Type.BOOLEAN == item.getType();
    }

    @Override
    protected byte[] getBytes(final Item item) throws IOException {
        if (item instanceof Base64BinaryDocument) {
            final Base64BinaryDocument doc = (Base64BinaryDocument) item;
            try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
                doc.streamBinaryTo(baos);
                return baos.toByteArray();
            }
        } else {
            final BinaryValueFromFile file = (BinaryValueFromFile) item;
            try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
                file.streamBinaryTo(baos);
                return baos.toByteArray();
            }
        }
    }

    @Override
    protected boolean getBoolean(final Item item) throws IOException {
        return ((BooleanValue)item).getValue();
    }
}
