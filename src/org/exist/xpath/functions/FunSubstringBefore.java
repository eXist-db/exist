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

package org.exist.xpath.functions;

import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Expression;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;

/**
 * Built-in function fn:substring-before($operand1 as xs:string?, $operand2 as xs:string?) as xs:string?
 *
 */
public class FunSubstringBefore extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("substring-before", BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));

	public FunSubstringBefore(StaticContext context) {
		super(context, signature);
	}

	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Expression arg0 = getArgument(0);
		Expression arg1 = getArgument(1);

		if (contextItem != null)
			contextSequence = contextItem.toSequence();

		Sequence seq1 = arg0.eval(contextSequence);
		Sequence seq2 = arg0.eval(contextSequence);

		if (seq1.getLength() == 0 || seq2.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;

		String value = seq1.getStringValue();
		String cmp = seq2.getStringValue();
		if (cmp.length() == 0)
			return StringValue.EMPTY_STRING;
		int p = value.indexOf(cmp);
		if (p > -1)
			return new StringValue(value.substring(0, p));
		else
			return new StringValue("");
	}
}
