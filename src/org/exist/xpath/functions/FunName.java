/* eXist Open Source Native XML Database
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
import org.exist.dom.NodeSet;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;
import org.w3c.dom.Node;

/**
 * xpath-library function: string(object)
 *
 */
public class FunName extends Function {

    public FunName() {
        super("name");
    }
	
    public int returnsType() {
        return Type.STRING;
    }
	
	public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence, 
			Item contextItem) throws XPathException {
			Node n = null;
			if(contextItem != null)
				contextSequence = contextItem.toSequence();
			if(getArgumentCount() > 0) {
				NodeSet result = (NodeSet)getArgument(0).eval(context, docs, contextSequence);
				if(result.getLength() > 0)
					n = result.item(0);
			} else {
				if(contextSequence.getLength() > 0 && contextSequence.getItemType() == Type.NODE)
					n = ((NodeSet)contextSequence).item(0);
			}
			if(n != null) {
				switch(n.getNodeType()) {
					case Node.ELEMENT_NODE:
						return new StringValue(n.getNodeName());
					case Node.ATTRIBUTE_NODE:
						return new StringValue(n.getNodeName());
					default:
						return new StringValue("");
				}
			} 
			return new StringValue("");
			}
}
