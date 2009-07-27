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
 * $Id$
 */
package org.exist.xquery.functions.xmldb;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 *
 */
public class XMLDBGetCurrentUser extends BasicFunction
{
	protected static final Logger logger = Logger.getLogger(XMLDBGetCurrentUser.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-current-user", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the current user from the context of the xquery.",
			null,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the username of the current user")
		);
	
	public XMLDBGetCurrentUser(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
	}
	
	public Sequence eval(Sequence args[], Sequence contextSequence) throws XPathException
	{
		logger.info("Entering " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
		StringValue stringValue = new StringValue(context.getUser().getName());
		logger.info("Exiting " + XMLDBModule.PREFIX + ":" + getName().getLocalName());
		return stringValue;
	}
}
