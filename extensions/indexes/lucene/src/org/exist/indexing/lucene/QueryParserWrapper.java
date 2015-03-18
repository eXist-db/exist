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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.search.Query;
import org.exist.xquery.XPathException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Wrapper around Lucene's parser classes, which unfortunately do not all inherit
 * from the same base class.
 *
 * @author Wolfgang
 */
public abstract class QueryParserWrapper {

    private final static Logger LOG = LogManager.getLogger(QueryParserWrapper.class);

    public QueryParserWrapper(String field, Analyzer analyzer) {
    }

    public abstract CommonQueryParserConfiguration getConfiguration();

    public abstract Query parse(String query) throws XPathException;

    public static QueryParserWrapper create(String className, String field, Analyzer analyzer) {
        try {
            Class<?> clazz = Class.forName(className);
            if (QueryParserWrapper.class.isAssignableFrom(clazz)) {
                final Class<?> cParamClasses[] = new Class<?>[] {
                    String.class, Analyzer.class
                };
                final Constructor<?> cstr = clazz.getDeclaredConstructor(cParamClasses);
                return (QueryParserWrapper) cstr.newInstance(field, analyzer);
            }
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            LOG.warn("Failed to instantiate lucene query parser wrapper class: " + className, e);
        }
        return null;
    }
}
