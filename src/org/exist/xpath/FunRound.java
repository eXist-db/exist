
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
package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.xpath.value.DecimalValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.NumericValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

public class FunRound extends Function {

	public FunRound() {
		super("round");
	}

	public int returnsType() {
		return Type.DOUBLE;
	}

	public DocumentSet preselect(DocumentSet in_docs, StaticContext context) throws XPathException {
		return getArgument(0).preselect(in_docs, context);
	}

	public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence,
		Item contextItem) throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		NumericValue value =
			(NumericValue) getArgument(0)
				.eval(context, docs, contextSequence, contextItem)
				.convertTo(Type.NUMBER);
		return new DecimalValue(Math.round(value.getDouble()));
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("round(");
		buf.append(getArgument(0).pprint());
		buf.append(')');
		return buf.toString();
	}
}
