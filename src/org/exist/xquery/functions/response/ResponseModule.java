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
package org.exist.xquery.functions.response;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

/**
 * Module function definitions for xmldb module.
 *
 * @author Adam Retter (adam.retter@devon.gov.uk)
 * @author ljo
 */
public class ResponseModule extends AbstractInternalModule {

	public static final String NAMESPACE_URI = "http://exist-db.org/xquery/response";	
	public static final String PREFIX = "response";
    public final static String INCLUSION_DATE = "2006-04-09";
    public final static String RELEASED_IN_VERSION = "eXist-1.0";

	public static final QName RESPONSE_VAR = new QName("response", NAMESPACE_URI, PREFIX);
	
	public static final FunctionDef[] functions = {
		new FunctionDef(RedirectTo.signature, RedirectTo.class),
		new FunctionDef(SetCookie.signatures[0], SetCookie.class),
		new FunctionDef(SetCookie.signatures[1], SetCookie.class),
		new FunctionDef(SetHeader.signature, SetHeader.class),
		new FunctionDef(SetStatusCode.signature, SetStatusCode.class),
        new FunctionDef(StreamBinary.signature, StreamBinary.class),
		new FunctionDef(GetExists.signature, GetExists.class)
	};
	
	public ResponseModule() throws XPathException {
		super(functions);
		// predefined module global variables:
		declareVariable(RESPONSE_VAR, null);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "A module for dealing with HTTP responses."; 
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
