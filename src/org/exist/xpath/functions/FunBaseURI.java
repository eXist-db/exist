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
package org.exist.xpath.functions;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.*;
import org.exist.xpath.Cardinality;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;

/**
 * @author wolf
 */
public class FunBaseURI extends Function {

	public final static FunctionSignature signature =
			new FunctionSignature(
				new QName("base-uri", BUILTIN_FUNCTION_NS),
                "Returns the value of the base-uri property for the argument. " +
                "Document, element and processing-instruction nodes have a " +
                "base-uri property. If that property is non-empty, its value " +
                "is returned. The base-uri of all other node types is the empty " +
                "sequence.",
				null,
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
			);
			
	/**
	 * @param name
	 */
	public FunBaseURI(StaticContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		return new StringValue(context.getBaseURI());
	}

}
