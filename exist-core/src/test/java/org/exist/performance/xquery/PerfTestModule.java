/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2006-2009 The eXist Project
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
package org.exist.performance.xquery;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * Module function definitions for performance test module.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author ljo
 */
public class PerfTestModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/test/performance";

	public final static String PREFIX = "pt";
    public final static String INCLUSION_DATE = "2006-12-29";
    public final static String RELEASED_IN_VERSION = "exist-1.4";

    public final static FunctionDef[] functions = {
            new FunctionDef(RandomText.signature, RandomText.class)
    };


    public PerfTestModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }


    /* (non-Javadoc)
* @see org.exist.xquery.Module#getNamespaceURI()
*/
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }


    /* (non-Javadoc)
* @see org.exist.xquery.Module#getDefaultPrefix()
*/
    public String getDefaultPrefix() {
        return PREFIX;
    }


    public String getDescription() {
        return "A module for helper functions in the performance test suite.";
    }

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

}
