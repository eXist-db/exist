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

package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.NumericValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;

/**
 * xpath-library function: string(object)
 *
 */
public class FunSubstring extends Function {
	
	public FunSubstring() {
		super("substring");
	}

	public int returnsType() {
		return Type.STRING;
	}
		
	public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence, 
		Item contextItem) throws XPathException {
		if(getArgumentCount() < 2)
			throw new XPathException("substring requires at least two arguments");
		Expression arg0 = getArgument(0);
		Expression arg1 = getArgument(1);
		Expression arg2 = null;
		if(getArgumentCount() > 2)
			arg2 = getArgument(2);

		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		int start = ((NumericValue)arg1.eval(context, docs, contextSequence).convertTo(Type.INTEGER)).getInt();
		int length = 1;
		if(arg2 != null)
			length = ((NumericValue)arg2.eval(context, docs, contextSequence).convertTo(Type.INTEGER)).getInt();
		if(start <= 0 || length < 0)
			throw new IllegalArgumentException("Illegal start or length argument");
		Sequence nodes = arg0.eval(context, docs, contextSequence);
		String result = nodes.getStringValue();
		if(start < 0 || --start + length >= result.length())
			return new StringValue("");
		return new StringValue((length > 0) ? result.substring(start, start + length) : result.substring(start));
	}
}
