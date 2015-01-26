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
package org.exist.xquery.modules.ngram;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * Module function definitions for NGram module.
 *
 * @author wolf
 * @author ljo
 *
 */
public class NGramModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/ngram";

    public static final String PREFIX = "ngram";
    public final static String INCLUSION_DATE = "2007-05-19";
    public final static String RELEASED_IN_VERSION = "eXist-1.2";

    public static final FunctionDef[] functions = {
    	new FunctionDef(AddMatch.signature, AddMatch.class),
        new FunctionDef(NGramSearch.signatures[0], NGramSearch.class),
        new FunctionDef(NGramSearch.signatures[1], NGramSearch.class),
        new FunctionDef(NGramSearch.signatures[2], NGramSearch.class),
        new FunctionDef(NGramSearch.signatures[3], NGramSearch.class),
        new FunctionDef(HighlightMatches.signature, HighlightMatches.class)
    };

    public NGramModule(Map<String, List<? extends Object>> parameters) {
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
        return "A module for NGram-based indexed searching.";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
