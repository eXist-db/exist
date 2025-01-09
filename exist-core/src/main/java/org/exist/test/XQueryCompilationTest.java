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
package org.exist.test;

import com.evolvedbinary.j8fu.Either;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;

import static com.evolvedbinary.j8fu.Either.Left;
import static com.evolvedbinary.j8fu.Either.Right;

/**
 * Base class for test suites testing XQuery compilation
 * @author <a href="mailto:juri@existsolutions.com">Juri Leino</a>
 */
public abstract class XQueryCompilationTest {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);
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

}
