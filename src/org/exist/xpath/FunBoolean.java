/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
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

import org.exist.dom.*;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

/**
 * xpath-library function: boolean(object)
 *
 */
public class FunBoolean extends Function {

	public FunBoolean() {
		super("boolean");
	}
	
	public int returnsType() {
		return Type.BOOLEAN;
	}
	
	public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence,
		Item contextItem) throws XPathException {
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		return 
			getArgument(0).eval(context, docs, contextSequence, null).convertTo(Type.BOOLEAN);
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("boolean(");
		buf.append(getArgument(0).pprint());
		buf.append(")");
		return buf.toString();
	}
}
