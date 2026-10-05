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

package org.exist.test.runner;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Base class for the runners of a file of XQSuite or XML tests.
 *
 * @author Adam Retter
 */
public abstract class AbstractTestRunner {

    protected final Path path;

    protected AbstractTestRunner(final Path path) {
        this.path = path;
    }

    /**
     * Returns the path to the test file (XQuery or XML). Used for hang reporting and diagnostics.
     *
     * @return the source path of the test file
     */
    public Path getSourcePath() {
        return path;
    }

    /**
     * @return the name that groups the tests of this file
     */
    public abstract String getSuiteName();

    /**
     * @return the names of the tests that are expected to be reported when this file is run
     */
    public abstract List<String> getTestNames();

    /**
     * @return for each test of {@link #getTestNames()}, in the same order, the line of the file it starts on,
     * so that a tool can jump to the test; 0 for a test whose line is not known, and empty if no lines are known
     */
    public List<Integer> getTestLines() {
        return List.of();
    }

    /**
     * Runs the tests of this file, reporting each outcome to {@code events}.
     *
     * @param events receives the outcome of each test
     * @param brokerPool the running database to execute the tests against
     */
    public abstract void run(TestEvents events, BrokerPool brokerPool);

    protected static Sequence executeQuery(final BrokerPool brokerPool, final Source query, final List<Function<XQueryContext, Tuple2<String, Object>>> externalVariableBindings) throws EXistException, PermissionDeniedException, XPathException, IOException, DatabaseConfigurationException {
        return executeQuery(brokerPool, query, externalVariableBindings, null);
    }

    protected static Sequence executeQuery(final BrokerPool brokerPool, final Source query, final List<Function<XQueryContext, Tuple2<String, Object>>> externalVariableBindings, @javax.annotation.Nullable final Path moduleLoadPath) throws EXistException, PermissionDeniedException, XPathException, IOException, DatabaseConfigurationException {
	final SecurityManager securityManager = requireNonNull(brokerPool.getSecurityManager(), "securityManager is null");
        try (final DBBroker broker = brokerPool.get(Optional.of(securityManager.getSystemSubject()))) {
            final XQueryPool queryPool = brokerPool.getXQueryPool();
            CompiledXQuery compiledQuery = queryPool.borrowCompiledXQuery(broker, query);

            XQueryContext context = null;
            try {
                if (compiledQuery == null) {
                    context = new XQueryContext(broker.getBrokerPool());
                } else {
                    context = compiledQuery.getContext();
                    context.prepareForReuse();
                }

                // setup misc. context
                context.setBaseURI(new AnyURIValue("/db"));
                if (moduleLoadPath != null) {
                    context.setModuleLoadPath(moduleLoadPath.toAbsolutePath().toString());
                } else if (query instanceof FileSource source) {
                    final Path queryPath = Path.of(source.getPath().toAbsolutePath().toString());
                    if (Files.isDirectory(queryPath)) {
                        context.setModuleLoadPath(queryPath.toString());
                    } else {
                        context.setModuleLoadPath(queryPath.getParent().toString());
                    }
                }

                // declare variables for the query
                for(final Function<XQueryContext, Tuple2<String, Object>> externalVariableBinding : externalVariableBindings) {
                    final Tuple2<String, Object> nameValue = externalVariableBinding.apply(context);
                    context.declareVariable(nameValue._1, nameValue._2);
                }

                final XQuery xqueryService = brokerPool.getXQueryService();

                // compile or update the context
                if (compiledQuery == null) {
                    compiledQuery = xqueryService.compile(context, query);
                } else {
                    compiledQuery.getContext().updateContext(context);
                    context.getWatchDog().reset();
                }

                return xqueryService.execute(broker, compiledQuery, null);

            } finally {
                if (context != null) {
                    context.runCleanupTasks();
                }

                queryPool.returnCompiledXQuery(query, compiledQuery);
            }
        }
    }
}
