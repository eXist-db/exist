/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *
 * $Id$
 */
package org.exist.xquery.modules.cache;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * XQuery Extension module for store data in global cache
 * 
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 * @author ljo
 * @version 1.0
 */
public class CacheModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/cache";

    public final static String PREFIX = "cache";
    public final static String INCLUSION_DATE = "2009-03-04";
    public final static String RELEASED_IN_VERSION = "eXist-1.4";
        
    private final static FunctionDef[] functions = {
        new FunctionDef(PutFunction.signatures[0], PutFunction.class),
        new FunctionDef(GetFunction.signatures[0], GetFunction.class),
        new FunctionDef(CacheFunction.signatures[0], CacheFunction.class),
        new FunctionDef(ClearFunction.signatures[0], ClearFunction.class),
        new FunctionDef(ClearFunction.signatures[1], ClearFunction.class),
        new FunctionDef(RemoveFunction.signatures[0], RemoveFunction.class),
        new FunctionDef(ListFunction.signature, ListFunction.class)
    };

    public CacheModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
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
            return "A module for accessing a global cache for stored/shared data between sessions";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
