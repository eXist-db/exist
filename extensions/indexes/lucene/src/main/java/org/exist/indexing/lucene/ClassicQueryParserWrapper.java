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
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
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

    public ClassicQueryParserWrapper(final String className, final String field, final Analyzer analyzer) {
        super(field, analyzer);
        try {
            final Class<?> clazz = Class.forName(className);
            if (QueryParserBase.class.isAssignableFrom(clazz)) {

                final MethodHandle methodHandle = LOOKUP.findConstructor(clazz, methodType(void.class, Version.class, String.class, Analyzer.class));
                final TriFunction<Version, String, Analyzer, QueryParserBase> constructor = (TriFunction<Version, String, Analyzer, QueryParserBase>)
                        LambdaMetafactory.metafactory(
                                LOOKUP, "apply", methodType(TriFunction.class),
                                methodHandle.type().erase(), methodHandle, methodHandle.type()).getTarget().invokeExact();

                parser = constructor.apply(LuceneIndex.LUCENE_VERSION_IN_USE, field, analyzer);
            }

        } catch (final Throwable e) {
            if (e instanceof InterruptedException) {
                // NOTE: must set interrupted flag
                Thread.currentThread().interrupt();
            }

            LOG.warn("Failed to instantiate lucene query parser class: " + className + ": " + e.getMessage(), e);
        }
    }

    public ClassicQueryParserWrapper(String field, Analyzer analyzer) {
        super(field, analyzer);
        parser = new QueryParser(LuceneIndex.LUCENE_VERSION_IN_USE, field, analyzer);
    }

    public Query parse(String query) throws XPathException {
        try {
            return parser.parse(query);
        } catch (ParseException e) {
            throw new XPathException("Syntax error in Lucene query string: " + e.getMessage());
        }
    }

    @Override
    public CommonQueryParserConfiguration getConfiguration() {
        return parser;
    }
}
