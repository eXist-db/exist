/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import javax.annotation.Nonnull;

/**
 * @author wolf
 */
public class SetAttribute extends StrictRequestFunction {
	
	protected static final Logger logger = LogManager.getLogger(SetAttribute.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "set-attribute", RequestModule.NAMESPACE_URI, RequestModule.PREFIX ),
			"Stores a value in the current request using the supplied attribute name.",
			new SequenceType[] {
				new FunctionParameterSequenceType( "name", Type.STRING, Cardinality.EXACTLY_ONE, "The attribute name" ),
				new FunctionParameterSequenceType( "value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The attribute value" )
				},
			new SequenceType( Type.ITEM, Cardinality.EMPTY )
		);
	
	public SetAttribute(final XQueryContext context)
	{
		super( context, signature );
	}
	
	@Override
	public Sequence eval(final Sequence args[], @Nonnull final RequestWrapper request) throws XPathException {
		final String attribName = args[0].getStringValue();
		final Sequence attribValue = args[1];

		request.setAttribute(attribName, attribValue);

		return Sequence.EMPTY_SEQUENCE;
	}
}
