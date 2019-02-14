/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2010 The eXist Project
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
package org.exist.xquery.modules.sort;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

import java.util.List;
import java.util.Map;

public class SortModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/sort";

    public final static String PREFIX = "sort";
    public final static String INCLUSION_DATE = "2010-03-22";
    public final static String RELEASED_IN_VERSION = "eXist-1.5";

    public final static FunctionDef[] functions = {
            new FunctionDef(CreateOrderIndex.signatures[0], CreateOrderIndex.class),
            new FunctionDef(CreateOrderIndex.signatures[1], CreateOrderIndex.class),
            new FunctionDef(GetIndex.signature, GetIndex.class),
            new FunctionDef(HasIndex.signature, HasIndex.class),
            new FunctionDef(RemoveIndex.signatures[0], RemoveIndex.class),
            new FunctionDef(RemoveIndex.signatures[1], RemoveIndex.class)
    };

    public SortModule(final Map<String, List<?>> parameters) {
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
        return "Creates and manages pre-ordered indexes for use with an 'order by' expression.";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
