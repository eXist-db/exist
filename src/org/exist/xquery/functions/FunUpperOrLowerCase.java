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
package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunUpperOrLowerCase extends Function {

	public final static FunctionSignature fnUpperCase =
		new FunctionSignature(
			new QName("upper-case", Function.BUILTIN_FUNCTION_NS),
			new SequenceType[] { new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE) },
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));
	
	public final static FunctionSignature fnLowerCase =
		new FunctionSignature(
			new QName("lower-case", Function.BUILTIN_FUNCTION_NS),
			new SequenceType[] { new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE) },
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));
	
	/**
	 * @param context
	 * @param signature
	 */
	public FunUpperOrLowerCase(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		Sequence seq = getArgument(0).eval(contextSequence);
		if(seq.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		String value = seq.getStringValue();
		if(isCalledAs("upper-case"))
			return new StringValue(value.toUpperCase());
		else
			return new StringValue(value.toLowerCase());
	}

}
