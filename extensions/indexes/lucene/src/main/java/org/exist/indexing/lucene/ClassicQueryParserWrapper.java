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
package org.exist.indexing.lucene;

import java.util.function.BiFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.search.Query;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static java.lang.invoke.MethodType.methodType;

/**
 * Wrapper around Lucene parsers which are based on
 * {@link QueryParserBase}.
 *
 * @author Wolfgang
 */
public class ClassicQueryParserWrapper extends QueryParserWrapper {

    private static final Logger LOG = LogManager.getLogger(ClassicQueryParserWrapper.class);
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private QueryParserBase parser = null;

    /** Non-null default field for Lucene (avoids NPE per LUCENE-1418). */
    private static String defaultField(final String field) {
        return field != null ? field : "";
    }

    public ClassicQueryParserWrapper(final String className, final String field, final Analyzer analyzer) {
        super(defaultField(field), analyzer);
        final String safeField = defaultField(field);
        try {
            final Class<?> clazz = Class.forName(className);
            if (QueryParserBase.class.isAssignableFrom(clazz)) {

                final MethodHandle methodHandle = LOOKUP.findConstructor(clazz, methodType(void.class, String.class, Analyzer.class));
                final BiFunction<String, Analyzer, QueryParserBase> constructor = (BiFunction<String, Analyzer, QueryParserBase>)
                        LambdaMetafactory.metafactory(
                                LOOKUP, "apply", methodType(BiFunction.class),
                                methodHandle.type().erase(), methodHandle, methodHandle.type()).getTarget().invokeExact();

                parser = constructor.apply(safeField, analyzer);
            }

        } catch (final InterruptedException e) {
            // NOTE: must set interrupted flag
            Thread.currentThread().interrupt();
            LOG.warn("Failed to instantiate lucene query parser class: {}: {}", className, e.getMessage(), e);
        } catch (final Throwable e) {
            LOG.warn("Failed to instantiate lucene query parser class: {}: {}", className, e.getMessage(), e);
        }
        if (parser == null) {
            parser = new QueryParser(safeField, analyzer);
        }
    }

    public ClassicQueryParserWrapper(final String field, final Analyzer analyzer) {
        super(defaultField(field), analyzer);
        parser = new QueryParser(defaultField(field), analyzer);
    }

    public Query parse(final String query) throws XPathException {
        try {
            return parser.parse(query);
        } catch (ParseException e) {
            throw new XPathException((Expression) null, "Syntax error in Lucene query string: " + e.getMessage());
        }
    }

    @Override
    public CommonQueryParserConfiguration getConfiguration() {
        return parser;
    }
}
