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
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.Sequence;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.xquery.modules.sql.Util.executeQuery;
import static org.exist.xquery.modules.sql.Util.withCompiledQuery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConnectionPoolIT {

    @Rule
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void getConnectionFromPoolIsAutomaticallyClosed() throws EXistException, XPathException, PermissionDeniedException, IOException {
        // NOTE: pool-1 is configured in src/test/resources-filtered/conf.xml
        final String query =
                "import module namespace sql = \"http://exist-db.org/xquery/sql\";\n" +
                        "sql:get-connection-from-pool(\"pool-1\")";

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
}
