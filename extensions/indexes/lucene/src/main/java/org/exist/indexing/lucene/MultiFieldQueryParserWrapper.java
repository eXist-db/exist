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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.search.Query;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

/**
 * Wrapper around Lucene's MultiFieldQueryParser for indexes with nested fields.
 * Handles query strings that reference multiple fields (e.g. lemma:test AND pos:/N/)
 * including regex in any field. Fixes GitHub #4389.
 */
public class MultiFieldQueryParserWrapper extends QueryParserWrapper {

    private final MultiFieldQueryParser parser;

    public MultiFieldQueryParserWrapper(final String[] fields, final Analyzer analyzer) {
        super(requireNonEmptyFields(fields)[0], analyzer);
        parser = new MultiFieldQueryParser(fields, analyzer);
    }

    private static String[] requireNonEmptyFields(final String[] fields) {
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("fields must be non-null and non-empty");
        }
        return fields;
    }

    @Override
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
