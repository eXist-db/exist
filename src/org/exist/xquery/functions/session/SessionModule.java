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
 *  $Id
 */
package org.exist.xquery.functions.session;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

/**
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class SessionModule extends AbstractInternalModule {

	public static final String NAMESPACE_URI = "http://exist-db.org/xquery/session";
	public static final String PREFIX = "session";
	public static final QName SESSION_VAR = new QName("session", NAMESPACE_URI, PREFIX);
	
	public static final FunctionDef[] functions = {
		new FunctionDef(Create.signature, Create.class),
		new FunctionDef(EncodeURL.signature, EncodeURL.class),
		new FunctionDef(GetID.signature, GetID.class),
		new FunctionDef(GetAttribute.signature, GetAttribute.class),
		new FunctionDef(GetAttributeNames.signature, GetAttributeNames.class),
		new FunctionDef(Invalidate.signature, Invalidate.class),
		new FunctionDef(SetAttribute.signature, SetAttribute.class),
		new FunctionDef(SetCurrentUser.signature, SetCurrentUser.class),
		new FunctionDef(GetExists.signature, GetExists.class)
	};
	
	public SessionModule() throws XPathException {
		super(functions);
		// predefined module global variables:
		declareVariable(SESSION_VAR, null);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "Functions dealing with the HTTP session"; 
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
