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
package org.exist.xquery.modules.sql;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.dom.memtree.ElementImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Element;

import java.io.IOException;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.xquery.modules.sql.Util.executeQuery;
import static org.exist.xquery.modules.sql.Util.withCompiledQuery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * SQL Execute Integration Tests.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ExecuteIT {

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Rule
    public final H2DatabaseResource h2Database = new H2DatabaseResource();

    @Test
    public void executeResultsInSqlNS() throws EXistException, XPathException, PermissionDeniedException, IOException {
        executeForNS(SQLModule.NAMESPACE_URI, SQLModule.PREFIX);
    }

    @Test
    public void executeResultsInCustomNS() throws EXistException, XPathException, PermissionDeniedException, IOException {
        executeForNS("http://custom/ns", "custom");
    }

    private void executeForNS(final String namespace, final String prefix) throws EXistException, XPathException, PermissionDeniedException, IOException {
        final String mainQuery =
            "import module namespace sql = \"http://exist-db.org/xquery/sql\";\n" +
                "let $conn := sql:get-connection(\"" + h2Database.getDriverClass().getName() + "\", \"" + h2Database.getUrl() + "\", \"" + h2Database.getUser() + "\", \"" + h2Database.getPassword() + "\")\n" +
                "return\n" +
                "    sql:execute($conn, \"SELECT 'Hello World' FROM DUAL;\", true(), \"" + namespace + "\", \"" + prefix + "\")";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source mainQuerySource = new StringSource(mainQuery);
        try (final DBBroker broker = pool.getBroker();
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Tuple2<String, String> namespaceAndPrefix = withCompiledQuery(broker, mainQuerySource, mainCompiledQuery -> {
                final XQueryContext mainQueryContext = mainCompiledQuery.getContext();

                // execute the query
                final Sequence result = executeQuery(broker, mainCompiledQuery);

                // check that the namespace of the result element is in the 'sql' namespace
                assertEquals(1, result.getItemCount());
                assertTrue(result.itemAt(0) instanceof Element);
                assertEquals(Type.ELEMENT, result.itemAt(0).getType());
                final Element element = (ElementImpl) result.itemAt(0);

                return Tuple(element.getNamespaceURI(), element.getPrefix());
            });

            assertEquals(namespace, namespaceAndPrefix._1);
            assertEquals(prefix, namespaceAndPrefix._2);

            transaction.commit();
        }
    }
}
