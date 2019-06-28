/*
Copyright (c) 2013, Adam Retter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Adam Retter Consulting nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.modules.request;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class RequestModule extends AbstractInternalModule {
    
    public static final String NAMESPACE_URI = "http://exquery.org/ns/request";
    public static final String PREFIX = "req";
    
    public final static String RELEASED_IN_VERSION = "eXist 2.0";
    
    public final static String EXQ_REQUEST_ATTR = "exquery-request";
    
    public static final FunctionDef[] functions = {
        
        new FunctionDef(GeneralFunctions.FNS_METHOD, GeneralFunctions.class),
        
        new FunctionDef(URIFunctions.FNS_SCHEME, URIFunctions.class),
        new FunctionDef(URIFunctions.FNS_HOSTNAME, URIFunctions.class),
        new FunctionDef(URIFunctions.FNS_PORT, URIFunctions.class),
        new FunctionDef(URIFunctions.FNS_PATH, URIFunctions.class),
        new FunctionDef(URIFunctions.FNS_QUERY, URIFunctions.class),
        new FunctionDef(URIFunctions.FNS_URI, URIFunctions.class),
        
        new FunctionDef(ConnectionFunctions.FNS_ADDRESS, ConnectionFunctions.class),
        new FunctionDef(ConnectionFunctions.FNS_REMOTE_HOSTNAME, ConnectionFunctions.class),
        new FunctionDef(ConnectionFunctions.FNS_REMOTE_ADDRESS, ConnectionFunctions.class),
        new FunctionDef(ConnectionFunctions.FNS_REMOTE_PORT, ConnectionFunctions.class),
        
        new FunctionDef(ParameterFunctions.FNS_PARAMETER_NAMES, ParameterFunctions.class),
        new FunctionDef(ParameterFunctions.FNS_PARAMETER, ParameterFunctions.class),
        new FunctionDef(ParameterFunctions.FNS_PARAMETER_WITH_DEFAULT, ParameterFunctions.class),
        
        new FunctionDef(HeaderFunctions.FNS_HEADER_NAMES, HeaderFunctions.class),
        new FunctionDef(HeaderFunctions.FNS_HEADER, HeaderFunctions.class),
        new FunctionDef(HeaderFunctions.FNS_HEADER_WITH_DEFAULT, HeaderFunctions.class),
        
        new FunctionDef(CookieFunctions.FNS_COOKIE, CookieFunctions.class),
        new FunctionDef(CookieFunctions.FNS_COOKIE_WITH_DEFAULT, CookieFunctions.class)
    };
    
    public RequestModule(final Map<String, List<? extends Object>> parameters) throws XPathException {
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
        return "EXQuery HTTP Request Module 1.0.";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
