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
package org.exist.storage;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.source.StringSource;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Currently, tests for the {@link org.exist.storage.XQueryPool}
 * with the {@link StringSource}. 
 */
public class LowLevelTextTest {

	private static final String TEST_XQUERY_SOURCE = "/test";

	@RegisterExtension
	public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

	private DBBroker broker;

	private XQueryPool xqueryPool;

	private StringSource stringSource;

	private CompiledXQuery preCompiledXQuery;

    @BeforeEach
    void setUp() throws DatabaseConfigurationException, EXistException, XPathException, PermissionDeniedException, IOException {
		final BrokerPool pool = existEmbeddedServer.getBrokerPool();
		broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
		xqueryPool = pool.getXQueryPool();
		stringSource = new StringSource(TEST_XQUERY_SOURCE);

		final XQuery xquery = pool.getXQueryService();
		final XQueryContext context = new XQueryContext(broker.getBrokerPool());
		preCompiledXQuery = xquery.compile(context, stringSource);
	}

    @AfterEach
    void tearDown() {
		if(broker != null) {
			broker.close();
		}
	}

    @org.junit.jupiter.api.Test
    void borrowCompiledXQuery1() throws PermissionDeniedException {
		// put the preCompiledXQuery in cache - NOTE: returnCompiledXQuery() is not a good name
		xqueryPool.returnCompiledXQuery(stringSource, preCompiledXQuery);
		callAndTestBorrowCompiledXQuery(stringSource);
	}

    @org.junit.jupiter.api.Test
    void borrowCompiledXQuery2() throws PermissionDeniedException {
		xqueryPool.returnCompiledXQuery(stringSource, preCompiledXQuery);

		callAndTestBorrowCompiledXQuery(stringSource);
		callAndTestBorrowCompiledXQuery(stringSource);
	}

    @org.junit.jupiter.api.Test
    void borrowCompiledXQuery3() throws PermissionDeniedException {
		xqueryPool.returnCompiledXQuery(stringSource, preCompiledXQuery);

		callAndTestBorrowCompiledXQuery(stringSource);
		callAndTestBorrowCompiledXQuery(stringSource);
		callAndTestBorrowCompiledXQuery(stringSource);
	}

    /**
     * test with a new StringSource object having same content
     */
    @org.junit.jupiter.api.Test
    void borrowCompiledXQueryNewStringSource() throws PermissionDeniedException {
		xqueryPool.returnCompiledXQuery(stringSource, preCompiledXQuery);
		StringSource localStringSource = new StringSource(TEST_XQUERY_SOURCE);

		callAndTestBorrowCompiledXQuery(localStringSource);
	}

    /**
     * test with a new StringSource object having same content
     */
    @org.junit.jupiter.api.Test
    void borrowCompiledXQueryNewStringSource2() throws PermissionDeniedException {
		xqueryPool.returnCompiledXQuery(stringSource, preCompiledXQuery);
		StringSource localStringSource = new StringSource(TEST_XQUERY_SOURCE);

		callAndTestBorrowCompiledXQuery(localStringSource);
		callAndTestBorrowCompiledXQuery(localStringSource);
	}

	private void callAndTestBorrowCompiledXQuery(StringSource stringSourceArg) throws PermissionDeniedException {
		final CompiledXQuery compiledXQuery = xqueryPool.borrowCompiledXQuery(broker, stringSourceArg);
		assertNotNull(
				compiledXQuery,
				"borrowCompiledXQuery should retrieve something for this stringSource");
		assertEquals(
				preCompiledXQuery,
				compiledXQuery, "borrowCompiledXQuery should retrieve the preCompiled XQuery for this stringSource");
		xqueryPool.returnCompiledXQuery(stringSourceArg, compiledXQuery);
	}
}
