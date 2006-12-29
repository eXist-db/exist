/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.performance.xquery;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

public class PerfTestModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/test/performance";

	public final static String PREFIX = "pt";

    public final static FunctionDef[] functions = {
            new FunctionDef(RandomText.signature, RandomText.class)
    };


    public PerfTestModule() {
        super(functions);
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
        return "Helper functions for the performance test suite.";
    }
}
