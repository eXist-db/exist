
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
package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.ComputableValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

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
				
    public FunSum(XQueryContext context) {
		super(context, signature);
    }

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence inner = getArgument(0).eval(contextSequence, contextItem);
		if (inner.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;

		SequenceIterator iter = inner.iterate();
		AtomicValue next = (AtomicValue) iter.nextItem();
		if (!Type.subTypeOf(next.getType(), Type.NUMBER))
			next = next.convertTo(Type.DOUBLE);
		ComputableValue sum = (ComputableValue) next;
		while (iter.hasNext()) {
			next = (AtomicValue) iter.nextItem();
			if (!Type.subTypeOf(next.getType(), Type.NUMBER))
				next = next.convertTo(Type.DOUBLE);
			sum = sum.plus((NumericValue) next);
		}
		return sum;
	}
}
			  
