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
package org.exist.contentextraction.xquery;

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
import org.exist.xquery.value.Sequence;
import org.junit.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.contentextraction.xquery.Util.executeQuery;
import static org.exist.contentextraction.xquery.Util.withCompiledQuery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class ContentFunctionsTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try (final Collection collection = broker.getOrCreateCollection(transaction, XmldbURI.create("/db/content-functions-test"))) {

                try (final InputStream is = ContentFunctionsTest.class.getResourceAsStream("minimal.pdf")) {
                    assertNotNull(is);
                    collection.addBinaryResource(transaction, broker, XmldbURI.create("minimal.pdf"), is, "application/pdf", -1);
                }

                try (final InputStream is = ContentFunctionsTest.class.getResourceAsStream("test.xlsx")) {
                    assertNotNull(is);
                    collection.addBinaryResource(transaction, broker, XmldbURI.create("test.xlsx"), is, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", -1);
                }

            }

            transaction.commit();
        }
    }

    @AfterClass
    public static void teardown() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try (final Collection collection = broker.openCollection(XmldbURI.create("/db/content-functions-test"), Lock.LockMode.WRITE_LOCK)) {
                if (collection != null) {
                    broker.removeCollection(transaction, collection);
                }
            }
        }
    }

    @Test
    public void getMetadataFromPdf() throws EXistException, XPathException, PermissionDeniedException, IOException {
        final String mainQuery =
                "declare namespace html = \"http://www.w3.org/1999/xhtml\";\n" +
                "declare namespace contentextraction = \"http://exist-db.org/xquery/contentextraction\";\n" +
                "declare namespace util = \"http://exist-db.org/xquery/util\";\n" +
                "let $bin := util:binary-doc(\"/db/content-functions-test/minimal.pdf\")\n" +
                "  return\n" +
                "    contentextraction:get-metadata($bin)//html:meta[@name = (\"xmpTPg:NPages\", \"Content-Type\")]/@content";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source mainQuerySource = new StringSource(mainQuery);
        try (final DBBroker broker = pool.getBroker();
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Tuple2<Integer, String> metadata = withCompiledQuery(broker, mainQuerySource, mainCompiledQuery -> {
                final Sequence result = executeQuery(broker, mainCompiledQuery);
                assertEquals(2, result.getItemCount());

                return Tuple(result.itemAt(0).toJavaObject(int.class), result.itemAt(1).getStringValue());
            });

            transaction.commit();

            assertEquals(1, metadata._1.intValue());
            assertEquals("application/pdf", metadata._2);
        }
    }

    @Test
    public void getMetadataAndContentFromPdf() throws EXistException, XPathException, PermissionDeniedException, IOException {
        final String mainQuery =
                "declare namespace html = \"http://www.w3.org/1999/xhtml\";\n" +
                "declare namespace contentextraction = \"http://exist-db.org/xquery/contentextraction\";\n" +
                "declare namespace util = \"http://exist-db.org/xquery/util\";\n" +
                "let $bin := util:binary-doc(\"/db/content-functions-test/minimal.pdf\")\n" +
                "  return\n" +
                "    contentextraction:get-metadata-and-content($bin)//html:p[2]/string()";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source mainQuerySource = new StringSource(mainQuery);
        try (final DBBroker broker = pool.getBroker();
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final String content = withCompiledQuery(broker, mainQuerySource, mainCompiledQuery -> {
                final Sequence result = executeQuery(broker, mainCompiledQuery);
                assertEquals(1, result.getItemCount());

                return result.itemAt(0).getStringValue();
            });

            transaction.commit();

            assertEquals("Hello World", content);
        }
    }

    @Ignore("see https://github.com/eXist-db/exist/issues/3835")
    @Test
    public void getMetadataFromXlsx() throws EXistException, XPathException, PermissionDeniedException, IOException {
        final String mainQuery =
                "declare namespace html = \"http://www.w3.org/1999/xhtml\";\n" +
                        "declare namespace contentextraction = \"http://exist-db.org/xquery/contentextraction\";\n" +
                        "declare namespace util = \"http://exist-db.org/xquery/util\";\n" +
                        "let $bin := util:binary-doc(\"/db/content-functions-test/test.xlsx\")\n" +
                        "  return\n" +
                        "    contentextraction:get-metadata($bin)//html:meta[@name = (\"xmpTPg:NPages\", \"Content-Type\")]/@content";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source mainQuerySource = new StringSource(mainQuery);
        try (final DBBroker broker = pool.getBroker();
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final Tuple2<Integer, String> metadata = withCompiledQuery(broker, mainQuerySource, mainCompiledQuery -> {
                final Sequence result = executeQuery(broker, mainCompiledQuery);
                assertEquals(2, result.getItemCount());

                return Tuple(result.itemAt(0).toJavaObject(int.class), result.itemAt(1).getStringValue());
            });

            transaction.commit();

            assertEquals(1, metadata._1.intValue());
            assertEquals("application/pdf", metadata._2);
        }
    }
}
