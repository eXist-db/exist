/*
 * NativeBroker.java - eXist Open Source Native XML Database
 * Copyright (C) 2001-03 Wolfgang M. Meier
 * wolfgang@exist-db.org
 * http://exist.sourceforge.net
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
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 */

package org.exist.xquery.functions;

import java.util.Iterator;
import java.util.List;

import org.exist.dom.QName;
import org.exist.xquery.Atomize;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Implements the library function fn:concat().
 *
 */
public class FunConcat extends Function {
	
	public final static FunctionSignature signature =
			new FunctionSignature(
				new QName("concat", Module.BUILTIN_FUNCTION_NS),
				"Accepts two or more xdt:anyAtomicType arguments and converts them " +
				"to xs:string. Returns the xs:string that is the concatenation of the values " +
				"of its arguments after conversion. If any of the arguments is the empty sequence, " +
				"the argument is treated as the zero-length string.",
				new SequenceType[] {
						new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_ONE)
				},
				new SequenceType(Type.STRING, Cardinality.ONE),
				true
			);
			
	public FunConcat(XQueryContext context) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.STRING;
	}
	
	/**
	 * Overloaded function: no static type checking.
	 * 
	 * @see org.exist.xquery.functions.Function#setArguments(java.util.List)
	 */
	public void setArguments(List arguments) throws XPathException {
		for(Iterator i = arguments.iterator(); i.hasNext(); ) {
			Expression next = (Expression) i.next();
			if(!Type.subTypeOf(next.returnsType(), Type.ATOMIC))
				next = new Atomize(context, next);
			steps.add(next);
		}
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		if(getArgumentCount() < 2)
			throw new XPathException ("concat requires at least two arguments");
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		StringBuffer result = new StringBuffer();
		for(int i = 0; i < getArgumentCount(); i++) {
			result.append(getArgument(i).eval(contextSequence).getStringValue());
		}
		return new StringValue(result.toString());
	}
}
