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
package org.exist.storage.util;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * Module function definitions for xquery test module.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author ljo
 */
public class TestUtilModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/test";

    public final static String PREFIX = "test";
    public final static String INCLUSION_DATE = "2008-12-03";
    public final static String RELEASED_IN_VERSION = "eXist-1.4";

    private final static FunctionDef functions[] = {
        new FunctionDef(PauseFunction.signature, PauseFunction.class)
    };

    public TestUtilModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }

    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    public String getDefaultPrefix() {
        return PREFIX;
    }

    public String getDescription() {
        return "A module for utilities used by the test suite.";
    }

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

}
