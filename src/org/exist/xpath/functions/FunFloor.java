/* eXist Open Source Native XML Database
 * Copyright (C) 2001-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
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
import org.exist.xpath.*;
import org.exist.xpath.Cardinality;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.NumericValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

public class FunFloor extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("floor", BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				 new SequenceType(Type.NUMBER, Cardinality.ZERO_OR_MORE)},
			new SequenceType(Type.NUMBER, Cardinality.ONE));

	public FunFloor(StaticContext context) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.NUMBER;
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
		return value.floor();
	}
}
