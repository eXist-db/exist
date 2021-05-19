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

import com.evolvedbinary.j8fu.function.Function2E;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

import java.io.IOException;
import java.util.Properties;

/**
 * Tests utility methods.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class Util {
    static Sequence executeQuery(final DBBroker broker, final CompiledXQuery compiledXQuery) throws PermissionDeniedException, XPathException {
        final BrokerPool pool = broker.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        return xqueryService.execute(broker, compiledXQuery, null, new Properties());
    }

    static <T> T withCompiledQuery(final DBBroker broker, final Source source, final Function2E<CompiledXQuery, T, XPathException, PermissionDeniedException> op) throws XPathException, PermissionDeniedException, IOException {
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

    static CompiledXQuery compileQuery(final DBBroker broker, final XQuery xqueryService, final XQueryPool xqueryPool, final Source query) throws PermissionDeniedException, XPathException, IOException {
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
