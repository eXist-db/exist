/* 
 * eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;

/**
 * xpath-library function: string(object)
 *
 */
public class FunString extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("number", BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				 new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
			true);

	public FunString(StaticContext context) {
		super(context, signature);
	}

	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		if(getArgumentCount() == 1)
			contextSequence = getArgument(0).eval(docs, contextSequence);
		if(contextSequence.getLength() == 0)
			return StringValue.EMPTY_STRING;
		return contextSequence.convertTo(Type.STRING);
	}
}
