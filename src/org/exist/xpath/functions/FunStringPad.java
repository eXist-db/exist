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

import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.XQueryContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.IntegerValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunStringPad extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("string-pad", BUILTIN_FUNCTION_NS),
			"Returns an xs:string consisting of a number copies of the first argument " +
			"concatenated together without any separators. The number of copies is specified " +
			"by the second argument.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				 new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));
			
	/**
	 * @param context
	 */
	public FunStringPad(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if(seq.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		String str = seq.getStringValue();
		int count = ((IntegerValue)getArgument(1).eval(contextSequence, contextItem).convertTo(Type.INTEGER)).getInt();
		if(count == 0)
			return StringValue.EMPTY_STRING;
		if(count < 0)
			throw new XPathException("Invalid string-pad count");
		StringBuffer buf = new StringBuffer(str.length() * count);
		for(int i = 0; i < count; i++)
			buf.append(str);
		return new StringValue(buf.toString());
	}

}
