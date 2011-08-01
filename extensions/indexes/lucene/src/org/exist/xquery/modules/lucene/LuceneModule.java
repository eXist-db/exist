/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2008-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.modules.lucene;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
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
        new FunctionDef(RemoveIndex.signature, RemoveIndex.class),
        new FunctionDef(Search.signatures[0], Search.class),
        new FunctionDef(Search.signatures[1], Search.class),
        new FunctionDef(GetField.signatures[0], GetField.class)
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

}

