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

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements the fn:subsequence function.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunSubSequence extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("subsequence", BUILTIN_FUNCTION_NS),
			"Returns a subsequence of the values in the first argument sequence, " +
			"starting at the position indicated by the value of the second argument and " +
			"including the number of items indicated by the value of the optional third" +
			"argument. If the third argument is missing, all items up to the end of the " +
			"sequence are included.",
			new SequenceType[] {
				 new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
				 new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
			true);
			
	/**
	 * @param context
	 */
	public FunSubSequence(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if(seq.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		int start = 
			((DoubleValue)getArgument(1).eval(contextSequence, contextItem).convertTo(Type.DOUBLE)).getInt();
		if(start < 0)
			start = 0;
		else
			--start;
		if(start >= seq.getLength())
			return Sequence.EMPTY_SEQUENCE;
		int length = -1;
		if(getArgumentCount() == 3)
			length =
				((DoubleValue)getArgument(2).eval(contextSequence, contextItem).convertTo(Type.DOUBLE)).getInt();
		 if(length < 0 || length > seq.getLength() - start)
		 	length = seq.getLength() - start;
		 Sequence result;
		 if(seq instanceof NodeSet)
		 	result = new ExtArrayNodeSet();
		 else
		 	result = new ValueSequence();
		 for(int i = 0; i < length; i++) {
		 	result.add(seq.itemAt(start + i));
		 }
		 return result;
	}

}
