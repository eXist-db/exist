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

package org.exist.xpath.functions;

import org.exist.dom.DocumentSet;
import org.exist.xpath.Expression;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;

/**
 * xpath-library function: string(object)
 *
 */
public class FunSubstringAfter extends Function {

	public FunSubstringAfter() {
		super("substring-after");
	}

	public int returnsType() {
		return Type.STRING;
	}

	public Sequence eval(
		StaticContext context,
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (getArgumentCount() < 2)
			throw new XPathException("substring-after requires two arguments");
		Expression arg0 = getArgument(0);
		Expression arg1 = getArgument(1);

		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		String value =
			arg0.eval(context, docs, contextSequence).getStringValue();
		String cmp = arg1.eval(context, docs, contextSequence).getStringValue();

		Sequence nodes = arg0.eval(context, docs, contextSequence);
		String result = nodes.getStringValue();
		int p = value.indexOf(cmp);
		if (p > -1)
			return new StringValue(
				p + 1 < value.length() ? value.substring(p + 1) : "");
		else
			return new StringValue("");
	}
}
