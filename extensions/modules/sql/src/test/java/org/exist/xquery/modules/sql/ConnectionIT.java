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
package org.exist.xquery.modules.sql;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.Sequence;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.xquery.modules.sql.Util.executeQuery;
import static org.exist.xquery.modules.sql.Util.withCompiledQuery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * SQL Connection Integration Tests.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ConnectionIT {

    @Rule
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Rule
    public H2DatabaseResource h2Database = new H2DatabaseResource();

    @Test
    public void getConnectionIsAutomaticallyClosed() throws EXistException, XPathException, PermissionDeniedException, IOException {
        final String query =
                "import module namespace sql = \"http://exist-db.org/xquery/sql\";\n" +
                "sql:get-connection(\"" + h2Database.getDriverClass().getName() + "\", \"" + h2Database.getUrl() + "\", \"" + h2Database.getUser() + "\", \"" + h2Database.getPassword() + "\")";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.getBroker();
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // execute query
            final Tuple2<XQueryContext, Boolean> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                final Sequence result = executeQuery(broker, compiledXQuery);
                return Tuple(compiledXQuery.getContext(), !result.isEmpty());
            });

            // check that the handle for the sql connection that was created is valid
            assertTrue(contextAndResult._2);

            // check the connections were closed
            final int connectionsCount = ModuleUtils.readContextMap(contextAndResult._1, SQLModule.CONNECTIONS_CONTEXTVAR, Map::size);
            assertEquals(0, connectionsCount);

            transaction.commit();
        }
    }

    @Test
    public void getConnectionFromModuleIsAutomaticallyClosed() throws EXistException, XPathException, PermissionDeniedException, IOException, LockException, TriggerException {
        final String module =
                "module namespace mymodule = \"http://mymodule.com\";\n" +
                "import module namespace sql = \"http://exist-db.org/xquery/sql\";\n" +
                "declare function mymodule:get-handle() {\n" +
                "    sql:get-connection(\"" + h2Database.getDriverClass().getName() + "\", \"" + h2Database.getUrl() + "\", \"" + h2Database.getUser() + "\", \"" + h2Database.getPassword() + "\")\n" +
                "};\n";

        final String query =
                "import module namespace mymodule = \"http://mymodule.com\" at \"xmldb:exist:///db/mymodule.xqm\";\n" +
                "mymodule:get-handle()";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store module
            final byte[] moduleData = module.getBytes(UTF_8);
            try (final ByteArrayInputStream bais = new ByteArrayInputStream(moduleData)) {
                try (final Collection collection = broker.openCollection(XmldbURI.create("/db"), Lock.LockMode.WRITE_LOCK)) {
                    collection.addBinaryResource(transaction, broker, XmldbURI.create("mymodule.xqm"), bais, "application/xquery", moduleData.length);
                }
            }

            // execute query
            final Tuple2<XQueryContext, Boolean> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                final Sequence result = executeQuery(broker, compiledXQuery);
                return Tuple(compiledXQuery.getContext(), !result.isEmpty());
            });

            // check that the handle for the sql connection that was created is valid
            assertTrue(contextAndResult._2);

            // check the connections were closed
            final int connectionsCount = ModuleUtils.readContextMap(contextAndResult._1, SQLModule.CONNECTIONS_CONTEXTVAR, Map::size);
            assertEquals(0, connectionsCount);

            transaction.commit();
        }
    }

    @Test
    public void getConnectionCanBeExplicitlyClosed() throws EXistException, XPathException, PermissionDeniedException, IOException {
        final String query =
                "import module namespace sql = \"http://exist-db.org/xquery/sql\";\n" +
                        "let $conn := sql:get-connection(\"" + h2Database.getDriverClass().getName() + "\", \"" + h2Database.getUrl() + "\", \"" + h2Database.getUser() + "\", \"" + h2Database.getPassword() + "\")\n" +
                        "return sql:close-connection($conn)";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.getBroker();
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // execute query
            final Tuple2<XQueryContext, Boolean> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                final Sequence result = executeQuery(broker, compiledXQuery);
                return Tuple(compiledXQuery.getContext(), result.itemAt(0).toJavaObject(boolean.class));
            });

            // check that the handle for the sql connection was closed
            assertTrue(contextAndResult._2);

            // check the connections were closed
            final int connectionsCount = ModuleUtils.readContextMap(contextAndResult._1, SQLModule.CONNECTIONS_CONTEXTVAR, Map::size);
            assertEquals(0, connectionsCount);

            transaction.commit();
        }
    }
}
