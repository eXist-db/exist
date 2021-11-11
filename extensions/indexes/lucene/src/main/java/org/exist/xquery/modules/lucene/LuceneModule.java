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
package org.exist.xquery.modules.lucene;

import java.util.List;
import java.util.Map;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.ErrorCodes.ErrorCode;
import org.exist.xquery.FunctionDef;

/**
 * Module function definitions for Lucene-based full text indexed searching.
 *
 * @author wolf
 * @author ljo
 *
 */
public class LuceneModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/lucene";

    public static final String PREFIX = "ft";
    public final static String INCLUSION_DATE = "2008-09-03";
    public final static String RELEASED_IN_VERSION = "eXist-1.4";

    public final static ErrorCode EXXQDYFT0001 = new LuceneErrorCode("EXXQDYFT0001", "Permission denied.");
    public final static ErrorCode EXXQDYFT0002 = new LuceneErrorCode("EXXQDYFT0002", "IO Exception in lucene index.");
    public final static ErrorCode EXXQDYFT0003 = new LuceneErrorCode("EXXQDYFT0003", "Document not found.");
    public final static ErrorCode EXXQDYFT0004 = new LuceneErrorCode("EXXQDYFT0004", "Wrong configuration passed to ft:query");
    
    public static final FunctionDef[] functions = {
        new FunctionDef(Query.signatures[0], Query.class),
        new FunctionDef(Query.signatures[1], Query.class),
        new FunctionDef(QueryField.signatures[0], QueryField.class),
        new FunctionDef(QueryField.signatures[1], QueryField.class),
        new FunctionDef(Score.signature, Score.class),
        new FunctionDef(Optimize.signature, Optimize.class),
        new FunctionDef(Index.signatures[0], Index.class),
        new FunctionDef(Index.signatures[1], Index.class),
        new FunctionDef(Index.signatures[2], Index.class),
        new FunctionDef(InspectIndex.signatures[0], InspectIndex.class),
        new FunctionDef(RemoveIndex.signature, RemoveIndex.class),
        new FunctionDef(Search.signatures[0], Search.class),
        new FunctionDef(Search.signatures[1], Search.class),
        new FunctionDef(Search.signatures[2], Search.class),
        new FunctionDef(GetField.signatures[0], GetField.class),
        new FunctionDef(Facets.signatures[0], Facets.class),
        new FunctionDef(Facets.signatures[1], Facets.class),
        new FunctionDef(Facets.signatures[2], Facets.class),
        new FunctionDef(Field.signatures[0], Field.class),
        new FunctionDef(Field.signatures[1], Field.class),
        new FunctionDef(Field.signatures[2], Field.class),
        new FunctionDef(Field.signatures[3], Field.class),
        new FunctionDef(LuceneIndexKeys.signatures[0], LuceneIndexKeys.class)
    };

    public LuceneModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters, false);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "A module for full text indexed searching based on Lucene.";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

    protected final static class LuceneErrorCode extends ErrorCode {

		public LuceneErrorCode(String code, String description) {
			super(new QName(code, NAMESPACE_URI, PREFIX), description);
		}
    	
    }
}

