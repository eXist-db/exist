/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2010 The eXist Project
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
 *  $Id: XMLDBIsAuthenticated.java 13941 2011-03-08 17:25:09Z shabanovd $
 */
package org.exist.xquery.functions.securitymanager;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class IsExternallyAuthenticated extends BasicFunction
{
	protected static final Logger logger = Logger.getLogger(IsExternallyAuthenticated.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("is-externally-authenticated", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX),
			"Returns the true() if current account is authenticated by external user, false() otherwise.",
			null,
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if user from the xquery context is authenticated, false() otherwise")
		);
	
	public IsExternallyAuthenticated(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	public Sequence eval(Sequence args[], Sequence contextSequence) throws XPathException {
		return new BooleanValue(context.getSubject().isExternallyAuthenticated());
	}
}
