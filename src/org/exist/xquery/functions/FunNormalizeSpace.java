/*
 * NativeBroker.java - eXist Open Source Native XML Database
 * Copyright (C) 2001-03 Wolfgang M. Meier
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

package org.exist.xquery.functions;

import java.util.StringTokenizer;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * xpath-library function: string(object)
 *
 */
public class FunNormalizeSpace extends Function {
	
	public final static FunctionSignature signature =
			new FunctionSignature(
				new QName("normalize-space", BUILTIN_FUNCTION_NS),
				new SequenceType[] { new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE) },
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				true);
				
	public FunNormalizeSpace(XQueryContext context) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.STRING;
	}
		
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		String value;
		if(getArgumentCount() == 0)
			value = contextSequence.getLength() > 0 ? contextSequence.itemAt(0).getStringValue() : "";
		else {
			Sequence seq = getArgument(0).eval(contextSequence);
			if(seq.getLength() == 0)
				return Sequence.EMPTY_SEQUENCE;
			value = seq.getStringValue();
		}
		StringBuffer result = new StringBuffer();
		if(value.length() > 0) {
			StringTokenizer tok = new StringTokenizer(value);
			while (tok.hasMoreTokens()) {
				result.append(tok.nextToken());
				if(tok.hasMoreTokens()) result.append(' ');
			}
		}
		return new StringValue(result.toString());
	}
}
