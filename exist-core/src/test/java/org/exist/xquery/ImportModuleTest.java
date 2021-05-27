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
import org.exist.security.PermissionDeniedException;
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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ImportModuleTest {

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    /**
     * Checks that the prefix part of an `import module` statement cannot be the value "xml".
     */
    @Test
    public void prefixXml() throws TriggerException, PermissionDeniedException, IOException, LockException, XPathException, EXistException {
        final ErrorCodes.ErrorCode errorCode = prefixNot("xml");
        assertEquals(ErrorCodes.XQST0070, errorCode);
    }

    /**
     * Checks that the prefix part of an `import module` statement cannot be the value "xmlns".
     */
    @Test
    public void prefixXmlNs() throws TriggerException, PermissionDeniedException, IOException, LockException, XPathException, EXistException {
        final ErrorCodes.ErrorCode errorCode = prefixNot("xmlns");
        assertEquals(ErrorCodes.XQST0070, errorCode);
    }

    /**
     * Executes an XQuery that imports a module with a specific prefix.
     *
     * @param prefix the prefix to use in the `import module` statement.
     *
     * @return the error code from executing the query, or null if there was no error.
     */
    private @Nullable ErrorCodes.ErrorCode prefixNot(final String prefix) throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String query =
                "import module namespace " + prefix + " = \"http://example.com/impl\" at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{" + prefix + ":f1(\"to impl1\")}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store module
            storeModules(broker, transaction,"/db", Tuple("impl1.xqm", module));

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                return null;

            } catch (final XPathException e) {
                transaction.commit();

                return e.getErrorCode();
            }
        }
    }

    /**
     * Checks that XQST0033 is raised if the prefix part of an `import module` statement is the same as the prefix
     * of another `import module` statement within the same module.
     */
    @Test
    public void prefixSameAsOtherImport() throws EXistException, IOException, TriggerException, PermissionDeniedException, LockException {
        final String module1 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String module2 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string, $b as xs:string) as xs:string {\n" +
                "    fn:concat($a, ' ', $b)\n" +
                "};\n";

        final String query =
                "import module namespace impl = \"http://example.com/impl\" at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "import module namespace impl = \"http://example.com/impl\" at \"xmldb:exist:///db/impl2.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{impl:f1(\"to impl1\")}</impl1>" +
                "    <impl2>{impl:f1(\"to\", \"impl1\")}</impl2>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store modules
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module1),
                    Tuple("impl2.xqm", module2)
            );

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0033");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0033, e.getErrorCode());
            }
        }
    }

    /**
     * Checks that XQST0033 is raised if the prefix part of an `import module` statement is the same as the prefix
     * of a namespace declaration within the same module.
     */
    @Test
    public void prefixSameAsOtherNamespaceDeclaration() throws EXistException, IOException, TriggerException, PermissionDeniedException, LockException {
        final String module =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String query =
                "declare namespace impl = \"http://example.com/impl\";\n" +
                "import module namespace impl = \"http://example.com/impl\" at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{impl:f1(\"to impl1\")}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store module
            storeModules(broker, transaction,"/db", Tuple("impl1.xqm", module));

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0033");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0033, e.getErrorCode());
            }
        }
    }

    /**
     * Checks that XQST0033 is raised if the prefix part of an `import module` statement is the same as the prefix
     * of the library module in which it resides.
     */
    @Test
    public void prefixSameAsModuleDeclaration() throws EXistException, IOException, TriggerException, PermissionDeniedException, LockException {
        final String module1 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "import module namespace impl = \"http://example.com/impl\" at \"xmldb:exist:///db/impl2.xqm\";\n" +
                "declare function impl:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String module2 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string, $b as xs:string) as xs:string {\n" +
                "    fn:concat($a, ' ', $b)\n" +
                "};\n";

        final String query =
                "declare namespace impl = \"http://example.com/impl\";\n" +
                "import module namespace impl = \"http://example.com/impl\" at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{impl:f1(\"to impl1\")}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store modules
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module1),
                    Tuple("impl2.xqm", module2)
            );

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0033");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0033, e.getErrorCode());
            }
        }
    }

    /**
     * Checks that XQST0088 is raised if the namespace part of an `import module` statement is empty.
     */
    @Test
    public void emptyNamespace() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException {
        final String module =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String query =
                "import module namespace impl = \"\" at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{impl:f1(\"to impl1\")}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store module
            storeModules(broker, transaction,"/db", Tuple("impl1.xqm", module));

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0088");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0088, e.getErrorCode());
            }
        }
    }

    /**
     * Checks that XQST0047 is raised if the namespace part of an `import module` statement is the same as the namespace
     * of another `import module` statement within the same module.
     */
    @Test
    public void namespaceSameAsOtherImport() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module1 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String module2 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string, $b as xs:string) as xs:string {\n" +
                "    fn:concat($a, ' ', $b)\n" +
                "};\n";

        final String query =
                "import module namespace impl1 = \"http://example.com/impl\"" +
                "        at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "import module namespace impl2 = \"http://example.com/impl\"" +
                "        at \"xmldb:exist:///db/impl2.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{impl1:f1(\"to impl1\")}</impl1>" +
                "    <impl2>{impl2:f1(\"to\", \"impl2\")}</impl2>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store modules
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module1),
                    Tuple("impl2.xqm", module2)
            );

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0047");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0047, e.getErrorCode());
            }
        }
    }

    /**
     * Checks that XQST0059 is raised if the module to be imported cannot be found (when there is a location hint).
     */
    @Test
    public void noSuchModuleWithLocationHint() throws EXistException, IOException, PermissionDeniedException {
        final String query =
                "import module namespace impl = \"http://example.com/impl\" at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{impl:f1(\"to impl1\")}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0059");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0059, e.getErrorCode());
            }
        }
    }

    /**
     * Checks that XQST0059 is raised if the module to be imported cannot be found (when there is no location hint).
     */
    @Test
    public void noSuchModuleWithoutLocationHint() throws EXistException, IOException, PermissionDeniedException {
        final String query =
                "import module namespace impl = \"http://example.com/impl\";\n" +
                "<result>\n" +
                "    <impl1>{impl:f1(\"to impl1\")}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0059");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0059, e.getErrorCode());
            }
        }
    }

    /**
     * Checks that XQST0034 is raised if two modules contain a function of the same name and arity.
     */
    @Test
    public void functionSameAsOtherModule() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module1 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String module2 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String query =
                "import module namespace impl = \"http://example.com/impl\"" +
                "        at \"xmldb:exist:///db/impl1.xqm\", \"xmldb:exist:///db/impl2.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{impl:f1(\"to impl1\")}</impl1>" +
                "    <impl2>{impl:f1(\"to impl1\")}</impl2>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store modules
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module1),
                    Tuple("impl2.xqm", module2)
            );

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0034");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0034, e.getErrorCode());
                assertTrue(e.getMessage().contains("{http://example.com/impl}f1#1"));
            }
        }
    }

    /**
     * Checks that XQST0034 is raised if a main module contains two functions of the same name and arity.
     */
    @Test
    public void functionDuplicateInMainModule() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String query =
                        "declare function local:f1($a as xs:string) as xs:string {\n" +
                        "    <first>{$a}</first>\n" +
                        "};\n" +
                        "\n" +
                        "declare function local:f1($a as xs:string) as xs:string {\n" +
                        "    <second>{$a}</second>\n" +
                        "};\n" +
                        "\n" +
                        "<result>\n" +
                        "    <impl1>{local:f1(\"to impl1\")}</impl1>" +
                        "    <impl2>{local:f1(\"to impl1\")}</impl2>" +
                        "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0034");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0034, e.getErrorCode());
                assertTrue(e.getMessage().contains("{http://www.w3.org/2005/xquery-local-functions}f1#1"));
            }
        }
    }

    /**
     * Checks that XQST0034 is raised if a main module contains two functions of the same name and arity.
     */
    @Test
    public void functionDuplicateNsInMainModule() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String query =
                "declare namespace ns1 = 'http://ns1';\n" +
                "declare namespace ns12 = 'http://ns1';\n" +
                "\n" +
                "declare function ns1:f1($a as xs:string) as xs:string {\n" +
                        "    <first>{$a}</first>\n" +
                        "};\n" +
                        "\n" +
                        "declare function ns12:f1($a as xs:string) as xs:string {\n" +
                        "    <second>{$a}</second>\n" +
                        "};\n" +
                        "\n" +
                        "<result>\n" +
                        "    <impl1>{ns1:f1(\"to impl1\")}</impl1>" +
                        "    <impl2>{ns12:f1(\"to impl1\")}</impl2>" +
                        "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0034");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0034, e.getErrorCode());
                assertTrue(e.getMessage().contains("{http://ns1}f1#1"));
            }
        }
    }

    /**
     * Checks that XQST0034 is raised if an imported module and the importing module contain a function of the same name and arity.
     */
    @Test
    public void functionSameAsImportingModule() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String query =
                "import module namespace impl = \"http://example.com/impl\"" +
                "        at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "declare function impl:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n" +
                "<result>\n" +
                "    <impl1>{impl:f1(\"to impl1\")}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store module
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module)
            );

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0034");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0034, e.getErrorCode());
                assertTrue(e.getMessage().contains("{http://example.com/impl}f1#1"));
            }
        }
    }

    /**
     * Checks that XQST0049 is raised if two modules contain a variable of the same name.
     */
    @Test
    public void variableSameAsOtherModule() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module1 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare variable $impl:f1 := \"impl1\";\n";

        final String module2 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare variable $impl:f1 := \"impl2\";\n";

        final String query =
                "import module namespace impl = \"http://example.com/impl\"" +
                "        at \"xmldb:exist:///db/impl1.xqm\", \"xmldb:exist:///db/impl2.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{$impl:f1}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store modules
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module1),
                    Tuple("impl2.xqm", module2)
            );

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0049");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0049, e.getErrorCode());
            }
        }
    }

    /**
     * Checks that XQST0049 is raised if an imported module and the importing module contain a variable of the same name.
     */
    @Test
    public void variableSameAsImportingModule() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare variable $impl:f1 := \"impl1\";\n";

        final String query =
                "import module namespace impl = \"http://example.com/impl\"" +
                "        at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "declare variable $impl:f1 := \"this\";\n" +
                "<result>\n" +
                "    <impl1>{$impl:f1}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store module
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module)
            );

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0049");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0049, e.getErrorCode());
            }
        }
    }

    /**
     * Imports a single XQuery Library Module containing functions into a target namespace.
     */
    @Test
    public void functionsSingleLocationHint() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String query =
                "import module namespace impl = \"http://example.com/impl\" at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{impl:f1(\"to impl1\")}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store module
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module)
            );

            // execute query
            final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                final Sequence result = executeQuery(broker, compiledXQuery);
                return Tuple(compiledXQuery.getContext(), result);
            });

            // check that the result was correct
            assertNotNull(contextAndResult._2);
            assertEquals(1, contextAndResult._2.getItemCount());
            final Element doc = (Element)contextAndResult._2.itemAt(0);
            assertNotNull(doc);

            final javax.xml.transform.Source actual = Input.fromDocument(doc.getOwnerDocument()).build();
            final javax.xml.transform.Source expected = Input.fromString("<result><impl1>to impl1</impl1></result>").build();

            final Diff diff = DiffBuilder
                    .compare(expected)
                    .withTest(actual)
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());

            transaction.commit();
        }
    }

    /**
     * Imports multiple XQuery Library Modules containing functions into the same target namespace.
     */
    @Test
    public void functionsCompositeFromMultipleLocationHints() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module1 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String module2 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f1($a as xs:string, $b as xs:string) as xs:string {\n" +
                "    fn:concat($a, ' ', $b)\n" +
                "};\n";

        final String module3 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare function impl:f2($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String query =
                "import module namespace impl = \"http://example.com/impl\"" +
                "        at \"xmldb:exist:///db/impl1.xqm\", \"xmldb:exist:///db/impl2.xqm\", \"xmldb:exist:///db/impl3.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{impl:f1(\"to impl1\")}</impl1>" +
                "    <impl2>{impl:f1(\"to\", \"impl2\")}</impl2>" +
                "    <impl3>{impl:f2(\"to impl3\")}</impl3>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store modules
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module1),
                    Tuple("impl2.xqm", module2),
                    Tuple("impl3.xqm", module3)
            );

            // execute query
            final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                final Sequence result = executeQuery(broker, compiledXQuery);
                return Tuple(compiledXQuery.getContext(), result);
            });

            // check that the result was correct
            assertNotNull(contextAndResult._2);
            assertEquals(1, contextAndResult._2.getItemCount());
            final Element doc = (Element)contextAndResult._2.itemAt(0);
            assertNotNull(doc);

            final javax.xml.transform.Source actual = Input.fromDocument(doc.getOwnerDocument()).build();
            final javax.xml.transform.Source expected = Input.fromString(
                    "<result>" +
                            "<impl1>to impl1</impl1>" +
                            "<impl2>to impl2</impl2>" +
                            "<impl3>to impl3</impl3>" +
                        "</result>"
            ).build();

            final Diff diff = DiffBuilder
                    .compare(expected)
                    .withTest(actual)
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());

            transaction.commit();
        }
    }

    /**
     * Imports multiple XQuery Library Modules containing functions into the same target namespace.
     */
    @Test
    public void functionsCompositeFromMultipleLocationHintsWithDifferingPrefixes() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module1 =
                "xquery version \"1.0\";\n" +
                        "module namespace impl1 = \"http://example.com/impl\";\n" +
                        "declare function impl1:f1($a as xs:string) as xs:string {\n" +
                        "    $a\n" +
                        "};\n";

        final String module2 =
                "xquery version \"1.0\";\n" +
                        "module namespace impl2 = \"http://example.com/impl\";\n" +
                        "declare function impl2:f1($a as xs:string, $b as xs:string) as xs:string {\n" +
                        "    fn:concat($a, ' ', $b)\n" +
                        "};\n";

        final String module3 =
                "xquery version \"1.0\";\n" +
                        "module namespace impl3 = \"http://example.com/impl\";\n" +
                        "declare function impl3:f2($a as xs:string) as xs:string {\n" +
                        "    $a\n" +
                        "};\n";

        final String query =
                "import module namespace impl = \"http://example.com/impl\"" +
                        "        at \"xmldb:exist:///db/impl1.xqm\", \"xmldb:exist:///db/impl2.xqm\", \"xmldb:exist:///db/impl3.xqm\";\n" +
                        "<result>\n" +
                        "    <impl1>{impl:f1(\"to impl1\")}</impl1>" +
                        "    <impl2>{impl:f1(\"to\", \"impl2\")}</impl2>" +
                        "    <impl3>{impl:f2(\"to impl3\")}</impl3>" +
                        "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store modules
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module1),
                    Tuple("impl2.xqm", module2),
                    Tuple("impl3.xqm", module3)
            );

            // execute query
            final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                final Sequence result = executeQuery(broker, compiledXQuery);
                return Tuple(compiledXQuery.getContext(), result);
            });

            // check that the result was correct
            assertNotNull(contextAndResult._2);
            assertEquals(1, contextAndResult._2.getItemCount());
            final Element doc = (Element)contextAndResult._2.itemAt(0);
            assertNotNull(doc);

            final javax.xml.transform.Source actual = Input.fromDocument(doc.getOwnerDocument()).build();
            final javax.xml.transform.Source expected = Input.fromString(
                    "<result>" +
                            "<impl1>to impl1</impl1>" +
                            "<impl2>to impl2</impl2>" +
                            "<impl3>to impl3</impl3>" +
                        "</result>"
            ).build();

            final Diff diff = DiffBuilder
                    .compare(expected)
                    .withTest(actual)
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());

            transaction.commit();
        }
    }

    /**
     * Imports a single XQuery Library Module containing variables into a target namespace.
     */
    @Test
    public void variablesSingleLocationHint() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare variable $impl:v1 := \"impl1\";\n";

        final String query =
                "import module namespace impl = \"http://example.com/impl\" at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{$impl:v1}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store module
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module)
            );

            // execute query
            final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                final Sequence result = executeQuery(broker, compiledXQuery);
                return Tuple(compiledXQuery.getContext(), result);
            });

            // check that the result was correct
            assertNotNull(contextAndResult._2);
            assertEquals(1, contextAndResult._2.getItemCount());
            final Element doc = (Element)contextAndResult._2.itemAt(0);
            assertNotNull(doc);

            final javax.xml.transform.Source actual = Input.fromDocument(doc.getOwnerDocument()).build();
            final javax.xml.transform.Source expected = Input.fromString("<result><impl1>impl1</impl1></result>").build();

            final Diff diff = DiffBuilder
                    .compare(expected)
                    .withTest(actual)
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());

            transaction.commit();
        }
    }

    /**
     * Imports multiple XQuery Library Modules containing variables into the same target namespace.
     */
    @Test
    public void variablesCompositeFromMultipleLocationHints() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module1 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare variable $impl:v1 := \"impl1\";\n";

        final String module2 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare variable $impl:v2 := \"impl2\";\n";

        final String module3 =
                "xquery version \"1.0\";\n" +
                "module namespace impl = \"http://example.com/impl\";\n" +
                "declare variable $impl:v3 := \"impl3\";\n";

        final String query =
                "import module namespace impl = \"http://example.com/impl\"" +
                        "        at \"xmldb:exist:///db/impl1.xqm\", \"xmldb:exist:///db/impl2.xqm\", \"xmldb:exist:///db/impl3.xqm\";\n" +
                        "<result>\n" +
                        "    <impl1>{$impl:v1}</impl1>" +
                        "    <impl2>{$impl:v2}</impl2>" +
                        "    <impl3>{$impl:v3}</impl3>" +
                        "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store modules
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module1),
                    Tuple("impl2.xqm", module2),
                    Tuple("impl3.xqm", module3)
            );

            // execute query
            final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                final Sequence result = executeQuery(broker, compiledXQuery);
                return Tuple(compiledXQuery.getContext(), result);
            });

            // check that the result was correct
            assertNotNull(contextAndResult._2);
            assertEquals(1, contextAndResult._2.getItemCount());
            final Element doc = (Element)contextAndResult._2.itemAt(0);
            assertNotNull(doc);

            final javax.xml.transform.Source actual = Input.fromDocument(doc.getOwnerDocument()).build();
            final javax.xml.transform.Source expected = Input.fromString(
                    "<result>" +
                            "<impl1>impl1</impl1>" +
                            "<impl2>impl2</impl2>" +
                            "<impl3>impl3</impl3>" +
                        "</result>"
            ).build();

            final Diff diff = DiffBuilder
                    .compare(expected)
                    .withTest(actual)
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());

            transaction.commit();
        }
    }

    /**
     * Imports multiple XQuery Library Modules into the same target namespace.
     */
    @Test
    public void variablesCompositeFromMultipleLocationHintsWithDifferingPrefixes() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module1 =
                "xquery version \"1.0\";\n" +
                "module namespace impl1 = \"http://example.com/impl\";\n" +
                "declare variable $impl1:v1 := \"impl1\";\n";

        final String module2 =
                "xquery version \"1.0\";\n" +
                "module namespace impl2 = \"http://example.com/impl\";\n" +
                "declare variable $impl2:v2 := \"impl2\";\n";

        final String module3 =
                "xquery version \"1.0\";\n" +
                "module namespace impl3 = \"http://example.com/impl\";\n" +
                "declare variable $impl3:v3 := \"impl3\";\n";

        final String query =
                "import module namespace impl = \"http://example.com/impl\"" +
                "        at \"xmldb:exist:///db/impl1.xqm\", \"xmldb:exist:///db/impl2.xqm\", \"xmldb:exist:///db/impl3.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{$impl:v1}</impl1>" +
                "    <impl2>{$impl:v2}</impl2>" +
                "    <impl3>{$impl:v3}</impl3>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store modules
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module1),
                    Tuple("impl2.xqm", module2),
                    Tuple("impl3.xqm", module3)
            );

            // execute query
            final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                final Sequence result = executeQuery(broker, compiledXQuery);
                return Tuple(compiledXQuery.getContext(), result);
            });

            // check that the result was correct
            assertNotNull(contextAndResult._2);
            assertEquals(1, contextAndResult._2.getItemCount());
            final Element doc = (Element)contextAndResult._2.itemAt(0);
            assertNotNull(doc);

            final javax.xml.transform.Source actual = Input.fromDocument(doc.getOwnerDocument()).build();
            final javax.xml.transform.Source expected = Input.fromString(
                    "<result>" +
                            "<impl1>impl1</impl1>" +
                            "<impl2>impl2</impl2>" +
                            "<impl3>impl3</impl3>" +
                        "</result>"
            ).build();

            final Diff diff = DiffBuilder
                    .compare(expected)
                    .withTest(actual)
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());

            transaction.commit();
        }
    }

    @Test
    public void variablesBetweenModules() throws EXistException, PermissionDeniedException, IOException, LockException, TriggerException, XPathException {
        final String module1 =
                "xquery version \"1.0\";\n" +
                "module namespace mod1 = \"http://example.com/mod1\";\n" +
                "declare variable $mod1:var1 := \"mod1 var1\";\n" +
                "declare function mod1:test() {\n" +
                "    <function name=\"mod1:test\">\n" +
                "        <variable name=\"mod1:var1\">{$mod1:var1}</variable>\n" +
                "    </function>\n" +
                "};";

        final String module2 =
                "xquery version \"1.0\";\n" +
                "module namespace mod2 = \"http://example.com/mod2\";\n" +
                "declare variable $mod2:var1 := \"mod2 var1\";\n" +
                "import module namespace mod1 = \"http://example.com/mod1\" at \"xmldb:exist:///db/mod1.xqm\";\n" +
                "declare function mod2:test() {\n" +
                "    <function name=\"mod2:test\">\n" +
                "        <variable name=\"mod2:var1\">{$mod2:var1}</variable>\n" +
                "        { mod1:test() }\n" +
                "        <variable name=\"mod1:var1\">{$mod1:var1}</variable>\n" +
                "    </function>\n" +
                "};";

        final String query =
                "xquery version \"1.0\";\n" +
                " import module namespace mod2 = 'http://example.com/mod2' at 'xmldb:exist:///db/mod2.xqm';\n" +
                "<result>\n" +
                "    {mod2:test()}\n" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store modules
            storeModules(broker, transaction,"/db",
                    Tuple("mod1.xqm", module1),
                    Tuple("mod2.xqm", module2)
            );

            // execute query
            final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                final Sequence result = executeQuery(broker, compiledXQuery);
                return Tuple(compiledXQuery.getContext(), result);
            });

            // check that the result was correct
            assertNotNull(contextAndResult._2);
            assertEquals(1, contextAndResult._2.getItemCount());
            final Element doc = (Element) contextAndResult._2.itemAt(0);
            assertNotNull(doc);

            final javax.xml.transform.Source actual = Input.fromDocument(doc.getOwnerDocument()).build();
            final javax.xml.transform.Source expected = Input.fromString(
                    "<result>" +
                        "<function name=\"mod2:test\">" +
                            "<variable name=\"mod2:var1\">mod2 var1</variable>" +
                            "<function name=\"mod1:test\">" +
                                "<variable name=\"mod1:var1\">mod1 var1</variable>" +
                            "</function>" +
                            "<variable name=\"mod1:var1\">mod1 var1</variable>" +
                       "</function>" +
                    "</result>"
            ).build();

            final Diff diff = DiffBuilder
                    .compare(expected)
                    .withTest(actual)
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());

            transaction.commit();
        }
    }


    /**
     * Checks that XQST0093 is raised if there exists a sequence of modules M1 ... Mi ... M1.
     */
    @Ignore("eXist-db does not have cyclic import checks, but it should!")
    @Test
    public void cyclic1() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module1 =
                "xquery version \"1.0\";\n" +
                "module namespace impl1 = \"http://example.com/impl1\";\n" +
                "import module namespace impl2 = \"http://example.com/impl2\"" +
                "        at \"xmldb:exist:///db/impl2.xqm\";\n" +
                "declare function impl1:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String module2 =
                "xquery version \"1.0\";\n" +
                "module namespace impl2 = \"http://example.com/impl2\";\n" +
                "import module namespace impl1 = \"http://example.com/impl1\"" +
                "        at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "declare function impl2:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String query =
                "import module namespace impl1 = \"http://example.com/impl1\"" +
                "        at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{impl1:f1(\"to impl1\")}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store modules
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module1),
                    Tuple("impl2.xqm", module2)
            );

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0093");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0093, e.getErrorCode());
            }
        }
    }

    /**
     * Checks that XQST0093 is raised if there exists a sequence of modules M1 ... Mi ... M1.
     */
    @Ignore("eXist-db does not have cyclic import checks, but it should!")
    @Test
    public void cyclic2() throws EXistException, IOException, PermissionDeniedException, LockException, TriggerException, XPathException {
        final String module1 =
                "xquery version \"1.0\";\n" +
                "module namespace impl1 = \"http://example.com/impl1\";\n" +
                "import module namespace impl2 = \"http://example.com/impl2\"" +
                "        at \"xmldb:exist:///db/impl2.xqm\";\n" +
                "declare function impl1:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String module2 =
                "xquery version \"1.0\";\n" +
                "module namespace impl2 = \"http://example.com/impl2\";\n" +
                "import module namespace impl3 = \"http://example.com/impl3\"" +
                "        at \"xmldb:exist:///db/impl3.xqm\";\n" +
                "declare function impl2:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String module3 =
                "xquery version \"1.0\";\n" +
                "module namespace impl3 = \"http://example.com/impl3\";\n" +
                "import module namespace impl1 = \"http://example.com/impl1\"" +
                "        at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "declare function impl3:f1($a as xs:string) as xs:string {\n" +
                "    $a\n" +
                "};\n";

        final String query =
                "import module namespace impl1 = \"http://example.com/impl1\"" +
                "        at \"xmldb:exist:///db/impl1.xqm\";\n" +
                "<result>\n" +
                "    <impl1>{impl1:f1(\"to impl1\")}</impl1>" +
                "</result>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // store modules
            storeModules(broker, transaction,"/db",
                    Tuple("impl1.xqm", module1),
                    Tuple("impl2.xqm", module2),
                    Tuple("impl3.xqm", module3)
            );

            // execute query
            try {
                final Tuple2<XQueryContext, Sequence> contextAndResult = withCompiledQuery(broker, source, compiledXQuery -> {
                    final Sequence result = executeQuery(broker, compiledXQuery);
                    return Tuple(compiledXQuery.getContext(), result);
                });

                transaction.commit();

                fail("expected XQST0093");

            } catch (final XPathException e) {
                transaction.commit();

                assertEquals(ErrorCodes.XQST0093, e.getErrorCode());
            }
        }
    }

    private void storeModules(final DBBroker broker, final Txn transaction, final String collectionUri, final Tuple2<String, String>... modules) throws PermissionDeniedException, IOException, TriggerException, LockException, EXistException {
        // store modules
        try (final Collection collection = broker.openCollection(XmldbURI.create(collectionUri), Lock.LockMode.WRITE_LOCK)) {

            for (final Tuple2<String, String> module : modules) {
                final XmldbURI moduleName = XmldbURI.create(module._1);
                final byte[] moduleData = module._2.getBytes(UTF_8);
                try (final ByteArrayInputStream bais = new ByteArrayInputStream(moduleData)) {
                    collection.addBinaryResource(transaction, broker, moduleName, bais, "application/xquery", moduleData.length);
                }
            }
        }
    }

    private Sequence executeQuery(final DBBroker broker, final CompiledXQuery compiledXQuery) throws PermissionDeniedException, XPathException {
        final BrokerPool pool = broker.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        return xqueryService.execute(broker, compiledXQuery, null, new Properties());
    }

    private <T> T withCompiledQuery(final DBBroker broker, final Source source, final Function2E<CompiledXQuery, T, XPathException, PermissionDeniedException> op) throws XPathException, PermissionDeniedException, IOException {
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

    private CompiledXQuery compileQuery(final DBBroker broker, final XQuery xqueryService, final XQueryPool xqueryPool, final Source query) throws PermissionDeniedException, XPathException, IOException {
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
}
