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

import org.exist.dom.DocumentSet;
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
public class FunConcat extends Function {
	
	public FunConcat() {
		super("concat");
	}

	public int returnsType() {
		return Type.STRING;
	}
		
	public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence, 
		Item contextItem) throws XPathException {
		if(getArgumentCount() < 2)
			throw new XPathException ("concat requires at least two arguments");
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		StringBuffer result = new StringBuffer();
		for(int i = 0; i < getArgumentCount(); i++) {
			result.append(getArgument(i).eval(context, docs, contextSequence).getStringValue());
		}
		return new StringValue(result.toString());
	}
}
