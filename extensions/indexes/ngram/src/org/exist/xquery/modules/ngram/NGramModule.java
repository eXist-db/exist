/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.xquery.modules.ngram;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * Module function definitions for NGram module.
 */
public class NGramModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/ngram";

    public static final String PREFIX = "ngram";

    public static final FunctionDef[] functions = {
        new FunctionDef(NGramSearch.signatures[0], NGramSearch.class),
        new FunctionDef(NGramSearch.signatures[1], NGramSearch.class),
        new FunctionDef(NGramSearch.signatures[2], NGramSearch.class),
        new FunctionDef(NGramSearch.signatures[3], NGramSearch.class),
        new FunctionDef(HighlightMatches.signature, HighlightMatches.class)
    };

    public NGramModule() {
        super(functions, false);
    }

    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    public String getDefaultPrefix() {
        return PREFIX;
    }

    public String getDescription() {
        return "Extension functions for NGram search.";
    }
}
