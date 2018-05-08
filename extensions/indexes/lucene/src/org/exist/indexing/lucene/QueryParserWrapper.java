/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import com.evolvedbinary.j8fu.function.TriFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.search.Query;
import org.exist.xquery.XPathException;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.function.BiFunction;

import static java.lang.invoke.MethodType.methodType;

/**
 * Wrapper around Lucene's parser classes, which unfortunately do not all inherit
 * from the same base class.
 *
 * @author Wolfgang
 */
public abstract class QueryParserWrapper {

    private static final Logger LOG = LogManager.getLogger(QueryParserWrapper.class);
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    public QueryParserWrapper(String field, Analyzer analyzer) {
    }

    public abstract CommonQueryParserConfiguration getConfiguration();

    public abstract Query parse(String query) throws XPathException;

    public static QueryParserWrapper create(final String className, final String field, final Analyzer analyzer) {
        try {
            final Class<?> clazz = Class.forName(className);
            if (QueryParserWrapper.class.isAssignableFrom(clazz)) {
                final MethodHandle methodHandle = LOOKUP.findConstructor(clazz, methodType(void.class, String.class, Analyzer.class));
                final BiFunction<String, Analyzer, QueryParserWrapper> constructor = (BiFunction<String, Analyzer, QueryParserWrapper>)
                        LambdaMetafactory.metafactory(
                                LOOKUP, "apply", methodType(TriFunction.class),
                                methodHandle.type().erase(), methodHandle, methodHandle.type()).getTarget().invokeExact();

                return constructor.apply(field, analyzer);
            }

        } catch (final Throwable e) {
            if (e instanceof InterruptedException) {
                // NOTE: must set interrupted flag
                Thread.currentThread().interrupt();
            }

            LOG.warn("Failed to instantiate lucene query parser wrapper class: " + className, e);
        }
        return null;
    }
}
