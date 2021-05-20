/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.xquery;

import com.evolvedbinary.j8fu.function.Function2E;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class XQueryContextAttributesTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void attributesOfMainModuleContextCleared() throws EXistException, LockException, TriggerException, PermissionDeniedException, IOException, XPathException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
            final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            final XmldbURI mainQueryUri = XmldbURI.create("/db/query1.xq");
            final Source mainQuery = new StringSource("<not-important/>");
            final DBSource mainQuerySource = storeQuery(broker, transaction, mainQueryUri, mainQuery);

            final XQueryContext escapedMainQueryContext = withCompiledQuery(broker, mainQuerySource, mainCompiledQuery -> {
                final XQueryContext mainQueryContext = mainCompiledQuery.getContext();

                mainQueryContext.setAttribute("attr1", "value1");
                mainQueryContext.setAttribute("attr2", "value2");

                // execute the query
                final Sequence result = executeQuery(broker, mainCompiledQuery);
                assertEquals(1, result.getItemCount());

                // intentionally escape the context from the lambda
                return mainQueryContext;
            });

            assertNull(escapedMainQueryContext.getAttribute("attr1"));
            assertNull(escapedMainQueryContext.getAttribute("attr2"));
            assertTrue(escapedMainQueryContext.attributes.isEmpty());

            transaction.commit();
        }
    }

    @Test
    public void attributesOfLibraryModuleContextCleared() throws EXistException, LockException, TriggerException, PermissionDeniedException, IOException, XPathException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            final XmldbURI libraryQueryUri = XmldbURI.create("/db/mod1.xqm");
            final Source libraryQuery = new StringSource(
                    "module namespace mod1 = 'http://mod1';\n" +
                            "declare function mod1:f1() { <not-important/> };"
            );
            storeQuery(broker, transaction, libraryQueryUri, libraryQuery);

            final XmldbURI mainQueryUri = XmldbURI.create("/db/query1.xq");
            final Source mainQuery = new StringSource(
                    "import module namespace mod1 = 'http://mod1' at 'xmldb:exist://" + libraryQueryUri + "';\n" +
                            "mod1:f1()"
            );
            final DBSource mainQuerySource = storeQuery(broker, transaction, mainQueryUri, mainQuery);

            final Tuple2<XQueryContext, ModuleContext> escapedContexts = withCompiledQuery(broker, mainQuerySource, mainCompiledQuery -> {
                final XQueryContext mainQueryContext = mainCompiledQuery.getContext();

                // get the context of the library module
                final Module[] libraryModules = mainQueryContext.getModules("http://mod1");
                assertEquals(1, libraryModules.length);
                assertTrue(libraryModules[0] instanceof ExternalModule);
                final ExternalModule libraryModule = (ExternalModule) libraryModules[0];
                final XQueryContext libraryQueryContext = libraryModule.getContext();
                assertTrue(libraryQueryContext instanceof ModuleContext);

                libraryQueryContext.setAttribute("attr1", "value1");
                libraryQueryContext.setAttribute("attr2", "value2");

                // execute the query
                final Sequence result = executeQuery(broker, mainCompiledQuery);
                assertEquals(1, result.getItemCount());

                // intentionally escape the contexts from the lambda
                return Tuple(mainQueryContext, (ModuleContext) libraryQueryContext);
            });

            final XQueryContext escapedMainQueryContext = escapedContexts._1;
            final ModuleContext escapedLibraryQueryContext = escapedContexts._2;
            assertTrue(escapedMainQueryContext != escapedLibraryQueryContext);

            assertNull(escapedMainQueryContext.getAttribute("attr1"));
            assertNull(escapedMainQueryContext.getAttribute("attr2"));
            assertTrue(escapedMainQueryContext.attributes.isEmpty());

            assertNull(escapedLibraryQueryContext.getAttribute("attr1"));
            assertNull(escapedLibraryQueryContext.getAttribute("attr2"));
            assertTrue(escapedLibraryQueryContext.attributes.isEmpty());

            transaction.commit();
        }
    }

    private static DBSource storeQuery(final DBBroker broker, final Txn transaction, final XmldbURI uri, final Source source) throws IOException, PermissionDeniedException, TriggerException, LockException, EXistException {
        try (final InputStream is = source.getInputStream()) {
            try (final Collection collection = broker.openCollection(uri.removeLastSegment(), Lock.LockMode.WRITE_LOCK)) {
                final BinaryDocument doc = collection.addBinaryResource(transaction, broker, uri.lastSegment(), is, "application/xquery", -1);

                return new DBSource(broker, doc, false);
            }
        }
    }

    private static <T> T withCompiledQuery(final DBBroker broker, final Source source, final Function2E<CompiledXQuery, T, XPathException, PermissionDeniedException> op) throws XPathException, PermissionDeniedException, IOException {
        final BrokerPool pool = broker.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        final XQueryPool xqueryPool = pool.getXQueryPool();
        final CompiledXQuery compiledQuery = compileQuery(broker, xqueryService, xqueryPool, source);
        try {
            return op.apply(compiledQuery);
        } finally {
            if (compiledQuery != null) {
                xqueryPool.returnCompiledXQuery(source, compiledQuery);
            }
        }
    }

    private static CompiledXQuery compileQuery(final DBBroker broker, final XQuery xqueryService, final XQueryPool xqueryPool, final Source query) throws PermissionDeniedException, XPathException, IOException {
        CompiledXQuery compiled = xqueryPool.borrowCompiledXQuery(broker, query);
        XQueryContext context;
        if (compiled == null) {
            context = new XQueryContext(broker.getBrokerPool());
        } else {
            context = compiled.getContext();
            context.prepareForReuse();
        }

        if (compiled == null) {
            compiled = xqueryService.compile(broker, context, query);
        } else {
            compiled.getContext().updateContext(context);
            context.getWatchDog().reset();
        }

        return compiled;
    }

    static Sequence executeQuery(final DBBroker broker, final CompiledXQuery compiledXQuery) throws PermissionDeniedException, XPathException {
        final BrokerPool pool = broker.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        return xqueryService.execute(broker, compiledXQuery, null, new Properties());
    }
}
