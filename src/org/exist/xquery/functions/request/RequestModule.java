/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class RequestModule extends AbstractInternalModule {

	public static final String NAMESPACE_URI = "http://exist-db.org/xquery/request";
	
	public static final String PREFIX = "request";
	
	public static final QName REQUEST_VAR = new QName("request", NAMESPACE_URI, PREFIX);
	public static final QName RESPONSE_VAR = new QName("response", NAMESPACE_URI, PREFIX);
	public static final QName SESSION_VAR = new QName("session", NAMESPACE_URI, PREFIX);
	
	public static final FunctionDef[] functions = {
		new FunctionDef(SessionAttributes.signature, SessionAttributes.class),
		new FunctionDef(GetSessionAttribute.signature, GetSessionAttribute.class),
		new FunctionDef(SetSessionAttribute.signature, SetSessionAttribute.class),
		new FunctionDef(InvalidateSession.signature, InvalidateSession.class),
		new FunctionDef(RequestParameter.signature, RequestParameter.class),
		new FunctionDef(RequestParameterNames.signature, RequestParameterNames.class),
		new FunctionDef(GetRequestData.signature, GetRequestData.class),
		new FunctionDef(CreateSession.signature, CreateSession.class),
		new FunctionDef(RequestURI.signature, RequestURI.class),
		new FunctionDef(RedirectTo.signature, RedirectTo.class),
		new FunctionDef(EncodeURL.signature, EncodeURL.class)
	};
	
	public RequestModule() {
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

}
