/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;
import org.exist.xquery.XPathException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Wrapper around Lucene parsers which are based on
 * {@link QueryParserBase}.
 *
 * @author Wolfgang
 */
public class ClassicQueryParserWrapper extends QueryParserWrapper {

    private final static Logger LOG = Logger.getLogger(ClassicQueryParserWrapper.class);

    private QueryParserBase parser = null;

    public ClassicQueryParserWrapper(String className, String field, Analyzer analyzer) {
        super(field, analyzer);
        try {
            Class<?> clazz = Class.forName(className);
            if (QueryParserBase.class.isAssignableFrom(clazz)) {
                final Class<?> cParamClasses[] = new Class<?>[] {
                        Version.class, String.class, Analyzer.class
                };
                final Constructor<?> cstr = clazz.getDeclaredConstructor(cParamClasses);
                parser = (QueryParserBase) cstr.newInstance(LuceneIndex.LUCENE_VERSION_IN_USE, field, analyzer);
            }
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException e) {
            LOG.warn("Failed to instantiate lucene query parser class: " + className, e);
        } catch (NoSuchMethodException | InstantiationException e) {
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
