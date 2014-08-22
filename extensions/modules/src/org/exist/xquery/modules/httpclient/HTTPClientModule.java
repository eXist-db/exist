/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2007-2009 The eXist Project
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
package org.exist.xquery.modules.httpclient;

import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;


/**
 * HTTPClient module
 *
 * @author   Adam Retter <adam.retter@devon.gov.uk>
 * @author   Andrzej Taramina <andrzej@chaeron.com>
 * @author   ljo
 * @version  1.3
 * @serial   20100228
 */
public class HTTPClientModule extends AbstractInternalModule
{
    public final static String         NAMESPACE_URI                  = "http://exist-db.org/xquery/httpclient";

    public final static String         PREFIX                         = "httpclient";
    public final static String         INCLUSION_DATE                 = "2007-09-06";
    public final static String         RELEASED_IN_VERSION            = "eXist-1.2";

    public final static String         HTTP_MODULE_PERSISTENT_STATE = "_eXist_httpclient_module_persistent_state";

    public final static HttpConnectionManager MANAGER = new MultiThreadedHttpConnectionManager();
	
    private final static FunctionDef[] functions                      = {
        new FunctionDef( GETFunction.signatures[0], GETFunction.class ),
        new FunctionDef( GETFunction.signatures[1], GETFunction.class ),
        new FunctionDef( PUTFunction.signatures[0], PUTFunction.class ),
        new FunctionDef( PUTFunction.signatures[1], PUTFunction.class ),
        new FunctionDef( DELETEFunction.signature, DELETEFunction.class ),
        new FunctionDef( POSTFunction.signatures[0], POSTFunction.class ),
        new FunctionDef( POSTFunction.signatures[1], POSTFunction.class ),
        new FunctionDef( HEADFunction.signature, HEADFunction.class ),
        new FunctionDef( OPTIONSFunction.signature, OPTIONSFunction.class ),
        new FunctionDef( ClearFunction.signatures[0], ClearFunction.class )
    };
	

    public HTTPClientModule(Map<String, List<? extends Object>> parameters)
    {
        super( functions, parameters );
    }
	

    public String getNamespaceURI()
    {
        return( NAMESPACE_URI );
    }


    public String getDefaultPrefix()
    {
        return( PREFIX );
    }


    public String getDescription()
    {
        return( "A module for performing HTTP requests as a client" );
    }


    public String getReleaseVersion()
    {
        return( RELEASED_IN_VERSION );
    }

}
