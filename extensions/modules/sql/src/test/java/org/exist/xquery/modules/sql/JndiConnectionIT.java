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
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.jdbcx.JdbcDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osjava.sj.loader.JndiLoader;
import org.xml.sax.SAXException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.xquery.modules.sql.Util.executeQuery;
import static org.exist.xquery.modules.sql.Util.withCompiledQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * SQL Connection Integration Tests.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class JndiConnectionIT {

    private static final String JNDI_DS_NAME = "com.fusiondb.xquery.modules.sql.H2DataSource";

    @RegisterExtension
    public ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @RegisterExtension
    public H2DatabaseResource h2Database = new H2DatabaseResource();

    private Context ctx = null;

    @BeforeEach
    void setupJndiEnvironment() throws NamingException {
        final Properties properties = new Properties();
        properties.setProperty(JNDI_DS_NAME + ".type", JdbcDataSource.class.getName());
        properties.setProperty(JNDI_DS_NAME + ".javaxNamingSpiObjectFactory", JdbcDataSourceFactory.class.getName());
        properties.setProperty(JNDI_DS_NAME + ".url", h2Database.getUrl());
        properties.setProperty(JNDI_DS_NAME + ".user", h2Database.getUser());
        properties.setProperty(JNDI_DS_NAME + ".password", h2Database.getPassword());
        properties.setProperty(JNDI_DS_NAME + ".description", "H2 Database JNDI DataSource");
        properties.setProperty(JNDI_DS_NAME + ".loginTimeout", "3");

        ctx = new InitialContext();
        final JndiLoader loader = new JndiLoader(ctx.getEnvironment());
        loader.load(properties, ctx);
    }

    @AfterEach
    void teardownJndiEnvironment() throws NamingException {
        ctx.unbind(JNDI_DS_NAME);
        ctx.close();
    }

    @Test
    void getJndiConnectionIsAutomaticallyClosed() throws EXistException, XPathException, PermissionDeniedException, IOException {
        final String mainQuery =
                "import module namespace sql = \"http://exist-db.org/xquery/sql\";\n" +
                "sql:get-jndi-connection(\"" + JNDI_DS_NAME + "\", \"" + h2Database.getUser() + "\", \"" + h2Database.getPassword() + "\")";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source mainQuerySource = new StringSource(mainQuery);
        try (final DBBroker broker = pool.getBroker();
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            final XQueryContext escapedMainQueryContext = withCompiledQuery(broker, mainQuerySource, mainCompiledQuery -> {
                final XQueryContext mainQueryContext = mainCompiledQuery.getContext();

                // execute the query
                final Sequence result = executeQuery(broker, mainCompiledQuery);

                // check that the handle for the sql connection that was created was valid
                assertEquals(1, result.getItemCount());
                assertInstanceOf(IntegerValue.class, result.itemAt(0));
                assertEquals(Type.LONG, result.itemAt(0).getType());
                final long connectionHandle = result.itemAt(0).toJavaObject(long.class);
                assertFalse(connectionHandle == 0);

                return mainQueryContext;
            });

            // check the connections map is empty
            final int connectionsCount = ModuleUtils.readContextMap(escapedMainQueryContext, SQLModule.CONNECTIONS_CONTEXTVAR, Map::size);
            assertEquals(0, connectionsCount);

            transaction.commit();
        }
    }

    @Test
    void getJndiConnectionFromModuleIsAutomaticallyClosed() throws EXistException, XPathException, PermissionDeniedException, IOException, LockException, SAXException {
        final String moduleQuery =
                "module namespace mymodule = \"http://mymodule.com\";\n" +
                "import module namespace sql = \"http://exist-db.org/xquery/sql\";\n" +
                "declare function mymodule:get-handle() {\n" +
                "    sql:get-jndi-connection(\"" + JNDI_DS_NAME + "\", \"" + h2Database.getUser() + "\", \"" + h2Database.getPassword() + "\")\n" +
                "};\n";

        final String mainQuery =
                """
                import module namespace mymodule = "http://mymodule.com" at "xmldb:exist:///db/mymodule.xqm";
                mymodule:get-handle()""";
        final Source mainQuerySource = new StringSource(mainQuery);

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store module
            try (final Collection collection = broker.openCollection(XmldbURI.create("/db"), Lock.LockMode.WRITE_LOCK)) {
                broker.storeDocument(transaction, XmldbURI.create("mymodule.xqm"), new StringInputSource(moduleQuery.getBytes(UTF_8)), MimeType.XQUERY_TYPE, collection);
            }

            final Tuple2<XQueryContext, ModuleContext> escapedContexts = withCompiledQuery(broker, mainQuerySource, mainCompiledQuery -> {
                final XQueryContext mainQueryContext = mainCompiledQuery.getContext();

                // get the context of the library module
                final org.exist.xquery.Module[] libraryModules = mainQueryContext.getModules("http://mymodule.com");
                assertEquals(1, libraryModules.length);
                assertInstanceOf(ExternalModule.class, libraryModules[0]);
                final ExternalModule libraryModule = (ExternalModule) libraryModules[0];
                final XQueryContext libraryQueryContext = libraryModule.getContext();
                assertInstanceOf(ModuleContext.class, libraryQueryContext);

                // execute the query
                final Sequence result = executeQuery(broker, mainCompiledQuery);

                // check that the handle for the sql connection that was created was valid
                assertEquals(1, result.getItemCount());
                assertInstanceOf(IntegerValue.class, result.itemAt(0));
                assertEquals(Type.LONG, result.itemAt(0).getType());
                final long connectionHandle = result.itemAt(0).toJavaObject(long.class);
                assertFalse(connectionHandle == 0);

                // intentionally escape the contexts from the lambda
                return Tuple(mainQueryContext, (ModuleContext) libraryQueryContext);
            });

            final XQueryContext escapedMainQueryContext = escapedContexts._1;
            final ModuleContext escapedLibraryQueryContext = escapedContexts._2;
            assertNotSame(escapedMainQueryContext, escapedLibraryQueryContext);

            // check the connections were closed in the main module
            final int mainConnectionsCount = ModuleUtils.readContextMap(escapedMainQueryContext, SQLModule.CONNECTIONS_CONTEXTVAR, Map::size);
            assertEquals(0, mainConnectionsCount);

            // check the connections were closed in the library module
            final int libraryConnectionsCount = ModuleUtils.readContextMap(escapedLibraryQueryContext, SQLModule.CONNECTIONS_CONTEXTVAR, Map::size);
            assertEquals(0, libraryConnectionsCount);

            transaction.commit();
        }
    }
}
