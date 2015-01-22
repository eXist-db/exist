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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.Query;
import org.exist.xquery.XPathException;

/**
 * Wrapper around Lucene's new flexible {@link StandardQueryParser}.
 *
 * @author Wolfgang
 */
public class StandardQueryParserWrapper extends QueryParserWrapper {

    private String field = null;
    private StandardQueryParser parser = null;

    public StandardQueryParserWrapper(String field, Analyzer analyzer) {
        super(field, analyzer);
        this.field = field;
        this.parser = new StandardQueryParser(analyzer);
    }

    @Override
    public CommonQueryParserConfiguration getConfiguration() {
        return parser;
    }

    @Override
    public Query parse(String query) throws XPathException {
        try {
            return parser.parse(query, field);
        } catch (QueryNodeException e) {
            throw new XPathException("Syntax error in Lucene query string: " + e.getMessage());
        }
    }
}
