/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.test.runner;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
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
import org.junit.runner.Runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Base class for XSuite test runners.
 *
 * @author Adam Retter
 */
public abstract class AbstractTestRunner extends Runner {

    protected final Path path;
    protected final boolean parallel;

    protected AbstractTestRunner(final Path path, final boolean parallel) {
        this.path = path;
        this.parallel = parallel;
    }

    protected static Sequence executeQuery(final Source query, final List<Function<XQueryContext, Tuple2<String, Object>>> externalVariableBindings) throws EXistException, PermissionDeniedException, XPathException, IOException, DatabaseConfigurationException {
        final BrokerPool brokerPool = XSuite.ExistServer.getRunningServer().getBrokerPool();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final XQueryPool queryPool = brokerPool.getXQueryPool();
            CompiledXQuery compiledQuery = queryPool.borrowCompiledXQuery(broker, query);

            try {
                XQueryContext context;
                if (compiledQuery == null) {
                    context = new XQueryContext(broker.getBrokerPool());
                } else {
                    context = compiledQuery.getContext();
                    context.prepareForReuse();
                }

                // setup misc. context
                context.setBaseURI(new AnyURIValue("/db"));
                if(query instanceof FileSource) {
                    final Path queryPath = Paths.get(((FileSource)query).getFilePath());
                    if(Files.isDirectory(queryPath)) {
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
                    compiledQuery = xqueryService.compile(broker, context, query);
                } else {
                    compiledQuery.getContext().updateContext(context);
                    context.getWatchDog().reset();
                }

                return xqueryService.execute(broker, compiledQuery, null);

            } finally {
                queryPool.returnCompiledXQuery(query, compiledQuery);
            }
        }
    }
}
