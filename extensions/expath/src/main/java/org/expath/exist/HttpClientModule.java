/*
 *  eXist EXPath HTTP Client Module Extension
 *  Copyright (C) 2011 Adam Retter <adam@existsolutions.com>
 *  www.existsolutions.com
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
package org.expath.exist;

import java.util.List;
import java.util.Map;

import org.expath.httpclient.HttpConstants;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 * @version EXPath HTTP Client Module Candidate 9 January 2010 http://expath.org/spec/http-client
 */
public class HttpClientModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = HttpConstants.HTTP_CLIENT_NS_URI;

    public final static String PREFIX = HttpConstants.HTTP_CLIENT_NS_PREFIX;
    public final static String INCLUSION_DATE = "2011-03-17";
    public final static String RELEASED_IN_VERSION = "1.5";

    private final static FunctionDef[] functions = {
        new FunctionDef(SendRequestFunction.signatures[0], SendRequestFunction.class),
        new FunctionDef(SendRequestFunction.signatures[1], SendRequestFunction.class),
        new FunctionDef(SendRequestFunction.signatures[2], SendRequestFunction.class),
    };

    public HttpClientModule(Map<String, List<? extends Object>> parameters) {
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
            return "EXPath HTTP Client http://expath.org/spec/http-client";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
