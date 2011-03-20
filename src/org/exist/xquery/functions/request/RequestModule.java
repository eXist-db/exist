/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
package org.exist.xquery.functions.request;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

/**
 * Module function definitions for transform module.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author ljo
 */
public class RequestModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://exist-db.org/xquery/request";
    public static final String PREFIX = "request";
    public final static String INCLUSION_DATE = "2004-09-12, 2006-04-09";
    public final static String RELEASED_IN_VERSION = "pre eXist-1.0 (Many functions originally in this module have been moved into new modules response and session.)";
    public static final QName REQUEST_VAR = new QName("request", NAMESPACE_URI, PREFIX);
    public static final FunctionDef[] functions = {
        new FunctionDef(GetRequestAttribute.signatures[0], GetRequestAttribute.class),
        new FunctionDef(GetRequestAttribute.signatures[1], GetRequestAttribute.class),
        new FunctionDef(GetCookieNames.signature, GetCookieNames.class),
        new FunctionDef(GetCookieValue.signature, GetCookieValue.class),
        new FunctionDef(GetData.signature, GetData.class),
        new FunctionDef(GetHeader.signature, GetHeader.class),
        new FunctionDef(GetHeaderNames.signature, GetHeaderNames.class),
        new FunctionDef(GetMethod.signature, GetMethod.class),
        new FunctionDef(GetScheme.signature, GetScheme.class),
        new FunctionDef(GetParameter.signatures[0], GetParameter.class),
        new FunctionDef(GetParameter.signatures[1], GetParameter.class),
        new FunctionDef(GetParameterNames.signature, GetParameterNames.class),
        new FunctionDef(GetQueryString.signature, GetQueryString.class),
        new FunctionDef(GetUploadedFile.signatures[0], GetUploadedFile.class),
        new FunctionDef(GetUploadedFileName.signature, GetUploadedFileName.class),
        new FunctionDef(GetUploadedFileSize.signature, GetUploadedFileSize.class),
        new FunctionDef(GetURI.signatures[0], GetURI.class),
        new FunctionDef(GetURI.signatures[1], GetURI.class),
        new FunctionDef(GetURL.signature, GetURL.class),
        new FunctionDef(GetServerName.signature, GetServerName.class),
        new FunctionDef(GetServerPort.signature, GetServerPort.class),
        new FunctionDef(GetHostname.signature, GetHostname.class),
        new FunctionDef(GetRemoteAddr.signature, GetRemoteAddr.class),
        new FunctionDef(GetRemoteHost.signature, GetRemoteHost.class),
        new FunctionDef(GetRemotePort.signature, GetRemotePort.class),
        new FunctionDef(GetExists.signature, GetExists.class),
        new FunctionDef(SetAttribute.signature, SetAttribute.class),
        new FunctionDef(GetContextPath.signatures[0], GetContextPath.class),
        new FunctionDef(GetContextPath.signatures[1], GetContextPath.class),
        new FunctionDef(GetPathInfo.signature, GetPathInfo.class),
        new FunctionDef(IsMultiPartContent.signature, IsMultiPartContent.class)
    };

    static {
        Arrays.sort(functions, new FunctionComparator());
    }

    public RequestModule(Map<String, List<? extends Object>> parameters) throws XPathException {
        super(functions, parameters, true);
        // predefined module global variables:
        declareVariable(REQUEST_VAR, null);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getDescription()
     */
    public String getDescription() {
        return "A module for dealing with HTTP requests.";
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

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
