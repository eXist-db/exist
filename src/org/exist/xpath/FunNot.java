/* eXist Native XML Database
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

import org.exist.dom.ContextItem;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.TreeNodeSet;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.Type;

public class FunNot extends Function {

	public FunNot() {
		super("not");
	}

	public int returnsType() {
		return Type.NODE;
	}

	public DocumentSet preselect(DocumentSet in_docs, StaticContext context)
		throws XPathException {
		return getArgument(0).preselect(in_docs, context);
	}

	public Sequence eval(
		StaticContext context,
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		NodeSet result = new TreeNodeSet();
		Expression path = getArgument(0);
		result.addAll(contextSequence);
		NodeProxy current;
		if (inPredicate)
			for (SequenceIterator i = result.iterate(); i.hasNext();) {
				current = (NodeProxy) i.nextItem();
				current.addContextNode(current);
			}
		// evaluate argument expression
		Sequence argSeq =
			path.eval(context, docs, contextSequence, contextItem);
		NodeProxy parent;
		long pid;
		ContextItem contextNode;
		NodeProxy next;
		Item item;
		// iterate through nodes and remove hits from result
		for (SequenceIterator i = argSeq.iterate(); i.hasNext();) {
			item = (Item) i.nextItem();
			current = (NodeProxy) item;
			contextNode = current.getContext();
			if (contextNode == null) {
				LOG.warn("context node is missing!");
				break;
			}

			while (contextNode != null) {
				next = contextNode.getNode();
				if ((parent = result.get(next)) != null)
					result.remove(parent);
				contextNode = contextNode.getNextItem();
			}
		}
		return result;
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("not(");
		buf.append(getArgument(0).pprint());
		buf.append(')');
		return buf.toString();
	}
}
