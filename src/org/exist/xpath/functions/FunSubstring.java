/*
 * NativeBroker.java - eXist Open Source Native XML Database
 * Copyright (C) 2001 Wolfgang M. Meier
 * meier@ifs.tu-darmstadt.de
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

package org.exist.xpath.functions;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.*;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Expression;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.DoubleValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.NumericValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;

/**
 * Built-in function fn:substring().
 *
 */
public class FunSubstring extends Function {
	
	public final static FunctionSignature signature =
			new FunctionSignature(
				new QName("substring", BUILTIN_FUNCTION_NS),
				new SequenceType[] {
					 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
					 new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE)
				},
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
				,true);
				
	public FunSubstring(StaticContext context) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.STRING;
	}
		
	public Sequence eval(DocumentSet docs, Sequence contextSequence, Item contextItem) throws XPathException {
		Expression arg0 = getArgument(0);
		Expression arg1 = getArgument(1);
		Expression arg2 = null;
		if(getArgumentCount() > 2)
			arg2 = getArgument(2);

		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		Sequence seq = arg0.eval(docs, contextSequence);
		if(seq.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		int start = ((DoubleValue)arg1.eval(docs, contextSequence).itemAt(0).convertTo(Type.DOUBLE)).getInt();
		int length = 0;
		if(arg2 != null)
			length = ((NumericValue)arg2.eval(docs, contextSequence).
				itemAt(0).convertTo(Type.DOUBLE)).getInt();
		if(start <= 0 || length < 0)
			throw new IllegalArgumentException("Illegal start or length argument");
			 
		String result = seq.getStringValue();
		if(start < 0 || --start + length >= result.length())
			return new StringValue("");
		return new StringValue((length > 0) ? result.substring(start, start + length) : result.substring(start));
	}
}
