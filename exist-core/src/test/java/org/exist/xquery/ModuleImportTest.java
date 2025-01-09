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
package org.exist.xquery;

import com.evolvedbinary.j8fu.Either;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;

import org.junit.ClassRule;
import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.evolvedbinary.j8fu.Either.Left;
import static com.evolvedbinary.j8fu.Either.Right;
import static com.ibm.icu.impl.Assert.fail;
import static org.exist.test.XQueryAssertions.assertThatXQResult;
import static org.exist.test.XQueryAssertions.assertXQStaticError;
import static org.hamcrest.Matchers.equalTo;

/**
 * Ensure library module imports work in one-off queries
 * needs functx to be installed => conf.xml => triggers => autodeploy
 *
 * @author <a href="mailto:juri@existsolutions.com">Juri Leino</a>
 */
public class ModuleImportTest {
    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(null, getConfigFile(), null, false, true);

    protected static Either<XPathException, CompiledXQuery> compileQuery(final String string) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = server.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try (final DBBroker broker = pool.getBroker()) {
            try {
                return Right(xqueryService.compile(new XQueryContext(broker.getDatabase()), string));
            } catch (final XPathException e) {
                return Left(e);
            }
        }
    }

    protected static Either<XPathException, Sequence> executeQuery(final String string) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = server.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try (final DBBroker broker = pool.getBroker()) {
            try {
                return Right(xqueryService.execute(broker, string, null));
            } catch (final XPathException e) {
                return Left(e);
            }
        }
    }

    private static Path getConfigFile() {
        final ClassLoader loader = ModuleImportTest.class.getClassLoader();
        final char separator = System.getProperty("file.separator").charAt(0);
        final String packagePath = ModuleImportTest.class.getPackage().getName().replace('.', separator);

        try {
            return Paths.get(loader.getResource(packagePath + separator + "conf.xml").toURI());
        } catch (final URISyntaxException e) {
            fail(e);
            return null;
        }
    }

    @Test
    public void importLibraryWithoutLocation() throws EXistException, PermissionDeniedException {
        final Sequence expected = new StringValue("xs:integer");

        final String query = "import module namespace functx='http://www.functx.com';" +
                "functx:atomic-type(4)";
        final Either<XPathException, Sequence> actual = executeQuery(query);

        assertThatXQResult(actual, equalTo(expected));
    }
    @Test
    public void importLibraryFromDbLocation() throws EXistException, PermissionDeniedException {
        final Sequence expected = new StringValue("xs:integer");

        final String query = "import module namespace functx='http://www.functx.com'" +
                " at '/db/system/repo/functx-1.0.1/functx/functx.xq';" +
                "functx:atomic-type(4)";
        final Either<XPathException, Sequence> actual = executeQuery(query);

        assertThatXQResult(actual, equalTo(expected));
    }

    @Test
    public void importLibraryFromXMLDBLocation() throws EXistException, PermissionDeniedException {
        final Sequence expected = new StringValue("xs:integer");

        final String query = "import module namespace functx='http://www.functx.com'" +
                " at 'xmldb:/db/system/repo/functx-1.0.1/functx/functx.xq';" +
                "functx:atomic-type(4)";
        final Either<XPathException, Sequence> actual = executeQuery(query);

        assertThatXQResult(actual, equalTo(expected));
    }

    @Test
    public void importLibraryFromXMLDBLocationDoubleSlash() throws EXistException, PermissionDeniedException {
        final Sequence expected = new StringValue("xs:integer");

        final String query = "import module namespace functx='http://www.functx.com'" +
                " at 'xmldb:///db/system/repo/functx-1.0.1/functx/functx.xq';" +
                "functx:atomic-type(4)";
        final Either<XPathException, Sequence> actual = executeQuery(query);

        assertThatXQResult(actual, equalTo(expected));
    }

    @Test
    public void importLibraryFromExistXMLDBLocation() throws EXistException, PermissionDeniedException {
        final Sequence expected = new StringValue("xs:integer");

        final String query = "import module namespace functx='http://www.functx.com'" +
                " at 'xmldb:exist:///db/system/repo/functx-1.0.1/functx/functx.xq';" +
                "functx:atomic-type(4)";
        final Either<XPathException, Sequence> actual = executeQuery(query);

        assertThatXQResult(actual, equalTo(expected));
    }

    @Test
    public void importLibraryFromUnknownLocation() throws EXistException, PermissionDeniedException {

        final String query = "import module namespace functx='http://www.functx.com'" +
                " at 'unknown:///db/system/repo/functx-1.0.1/functx/functx.xq';" +
                "functx:atomic-type(4)";
        final String expectedMessage = "error found while loading module functx: Source for module 'http://www.functx.com' not found module location hint URI 'unknown:///db/system/repo/functx-1.0.1/functx/functx.xq'.";

        assertXQStaticError(ErrorCodes.XQST0059, -1,-1, expectedMessage, compileQuery(query));
    }

    @Test
    public void importLibraryFromRelativeLocation() throws EXistException, PermissionDeniedException {
        final String query = "import module namespace functx='http://www.functx.com'" +
                " at './functx.xq';" +
                "functx:atomic-type(4)";
        final String expectedMessage = "error found while loading module functx: Source for module 'http://www.functx.com' not found module location hint URI './functx.xq'.";

        assertXQStaticError(ErrorCodes.XQST0059, -1,-1, expectedMessage, compileQuery(query));
    }

}
