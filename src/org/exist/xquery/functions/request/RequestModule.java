/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions.request;

import java.util.Arrays;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.response.RedirectTo;
import org.exist.xquery.functions.response.StreamBinary;
import org.exist.xquery.functions.session.Create;
import org.exist.xquery.functions.session.EncodeURL;
import org.exist.xquery.functions.session.GetAttribute;
import org.exist.xquery.functions.session.GetAttributeNames;
import org.exist.xquery.functions.session.GetID;
import org.exist.xquery.functions.session.Invalidate;
import org.exist.xquery.functions.session.SetAttribute;
import org.exist.xquery.functions.session.SetCurrentUser;
import org.exist.xquery.functions.util.FunUnEscapeURI;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class RequestModule extends AbstractInternalModule {

	public static final String NAMESPACE_URI = "http://exist-db.org/xquery/request";
	public static final String PREFIX = "request";
	public static final QName REQUEST_VAR = new QName("request", NAMESPACE_URI, PREFIX);
	
	public static final FunctionDef[] functions = {
        new FunctionDef(GetRequestAttribute.signature, GetRequestAttribute.class),
        new FunctionDef(GetCookieNames.signature, GetCookieNames.class),
		new FunctionDef(GetCookieValue.signature, GetCookieValue.class),
		new FunctionDef(GetData.signature, GetData.class),
		new FunctionDef(GetHeader.signature, GetHeader.class),
		new FunctionDef(GetHeaderNames.signature, GetHeaderNames.class),
		new FunctionDef(GetMethod.signature, GetMethod.class),
		new FunctionDef(GetParameter.signature, GetParameter.class),
		new FunctionDef(GetParameterNames.signature, GetParameterNames.class),
		new FunctionDef(GetQueryString.signature, GetQueryString.class),
		new FunctionDef(GetUploadedFile.signature, GetUploadedFile.class),
		new FunctionDef(GetUploadedFileName.signature, GetUploadedFileName.class),
		new FunctionDef(GetURI.signature, GetURI.class),
		new FunctionDef(GetURL.signature, GetURL.class),
		new FunctionDef(GetServerName.signature, GetServerName.class),
		new FunctionDef(GetServerPort.signature, GetServerPort.class),
		new FunctionDef(GetHostname.signature, GetHostname.class),
		
		// deprecated functions:
		new FunctionDef(Create.deprecated, Create.class),
		new FunctionDef(EncodeURL.deprecated, EncodeURL.class),
		new FunctionDef(GetData.deprecated, GetData.class),
		new FunctionDef(GetAttribute.deprecated, GetAttribute.class),
		new FunctionDef(GetID.deprecated, GetID.class),
		new FunctionDef(Invalidate.deprecated, Invalidate.class),
		new FunctionDef(RedirectTo.deprecated, RedirectTo.class),
		new FunctionDef(GetHostname.deprecated, GetHostname.class),
		new FunctionDef(GetParameter.deprecated, GetParameter.class),
		new FunctionDef(GetParameterNames.deprecated, GetParameterNames.class),
		new FunctionDef(GetServerName.deprecated, GetServerName.class),
		new FunctionDef(GetServerPort.deprecated, GetServerPort.class),
        new FunctionDef(GetContextPath.signature, GetContextPath.class),
        new FunctionDef(GetPathInfo.signature, GetPathInfo.class),
        new FunctionDef(GetURI.deprecated, GetURI.class),
		new FunctionDef(GetAttributeNames.deprecated, GetAttributeNames.class),
		new FunctionDef(SetCurrentUser.deprecated, SetCurrentUser.class),
		new FunctionDef(SetAttribute.deprecated, SetAttribute.class),
		new FunctionDef(StreamBinary.deprecated, StreamBinary.class),
		new FunctionDef(FunUnEscapeURI.deprecated, FunUnEscapeURI.class)
		
	};

    static {
        Arrays.sort(functions, new FunctionComparator());
    }

    public RequestModule() throws XPathException {
		super(functions, true);
		// predefined module global variables:
		declareVariable(REQUEST_VAR, null);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "Functions dealing with HTTP requests"; 
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

}
