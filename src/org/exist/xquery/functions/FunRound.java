
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
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunRound extends Function {

	public final static FunctionSignature signature =
			new FunctionSignature(
				new QName("round", BUILTIN_FUNCTION_NS),
				new SequenceType[] { new SequenceType(Type.NUMBER, Cardinality.ZERO_OR_ONE) },
				new SequenceType(Type.NUMBER, Cardinality.EXACTLY_ONE)
			);
			
	public FunRound(XQueryContext context) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.DOUBLE;
	}

	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		Sequence seq =
			getArgument(0).eval(contextSequence, contextItem);
		if (seq.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		NumericValue value =
			(NumericValue) seq.itemAt(0).convertTo(Type.NUMBER);
		return value.round();
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("round(");
		buf.append(getArgument(0).pprint());
		buf.append(')');
		return buf.toString();
	}
}
