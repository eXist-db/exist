
/* eXist Open Source Native XML Database
 * Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * $Id$
 */
package org.exist.xpath.functions;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.AtomicValue;
import org.exist.xpath.value.ComputableValue;
import org.exist.xpath.value.DoubleValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

public class FunSum extends Function {

	public final static FunctionSignature signature =
			new FunctionSignature(
				new QName("sum", BUILTIN_FUNCTION_NS),
				"Returns a value obtained by adding together the values in $a. If the " +
				"single-argument form of the function is used, then the value returned for " +
				"an empty sequence is the xs:double value 0.0e0. If the two-argument form " +
				"is used, then the value returned for an empty sequence is the value of " +
				"the $b argument.",
				new SequenceType[] {
					 new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_MORE),
					 new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_ONE)},
				new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_ONE),
				true);
				
    public FunSum(StaticContext context) {
		super(context, signature);
    }

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence zero = DoubleValue.ZERO;
		if(getArgumentCount() == 2)
			zero = getArgument(1).eval(contextSequence, contextItem);
		Sequence inner = getArgument(0).eval(contextSequence, contextItem);
		if(inner.getLength() == 0)
			return zero;
		
		SequenceIterator iter = inner.iterate();
		AtomicValue next = (AtomicValue)iter.nextItem();
		if(!Type.subTypeOf(next.getType(), Type.NUMBER))
			throw new XPathException("Invalid argument to aggregate function. Expected number, got " + 
				Type.getTypeName(next.getType()));
		ComputableValue sum = (ComputableValue)next;
		while(iter.hasNext()) {
			next = (AtomicValue)iter.nextItem();
			if(next.getType() == Type.ATOMIC)
				next = next.convertTo(Type.DOUBLE);
			if(!Type.subTypeOf(next.getType(), Type.NUMBER))
				throw new XPathException("Invalid argument to aggregate function. Expected number, got " + 
					Type.getTypeName(next.getType()));
			sum = sum.plus((ComputableValue)next);
		}
		return sum;
	}
}
			  
