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

package org.exist.xpath.functions;

import org.exist.dom.ContextItem;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Dependency;
import org.exist.xpath.Expression;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.BooleanValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

public class FunNot extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("not", BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				 new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE));

	public FunNot(StaticContext context) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.subTypeOf(getArgument(0).returnsType(), Type.NODE)
			? Type.NODE
			: Type.BOOLEAN;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET | getArgument(0).getDependencies();
	}

	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Expression arg = getArgument(0);
		// case 1: if the argument expression returns a node set,
		// subtract the set from the context node set and return
		// the remaining set
		if (Type.subTypeOf(arg.returnsType(), Type.NODE)) {
			NodeSet result = new ExtArrayNodeSet();
			if(contextSequence.getLength() > 0)
				result.addAll(contextSequence);
			NodeProxy current;
			if (inPredicate)
				for (SequenceIterator i = result.iterate(); i.hasNext();) {
					current = (NodeProxy) i.nextItem();
					current.addContextNode(current);
				}
			// evaluate argument expression
			Sequence argSeq =
				arg.eval(contextSequence, contextItem);
			LOG.debug("not argument: " + argSeq.getLength());
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
					//if ((parent = result.get(next)) != null)
					result.remove(next);
					contextNode = contextNode.getNextItem();
				}
			}
			LOG.debug("found " + result.getLength());
			return result;
		// case 2: simply invert the boolean value
		} else {
			Sequence seq =
				arg.eval(contextSequence, contextItem);
			return seq.effectiveBooleanValue() ? BooleanValue.FALSE : BooleanValue.TRUE;
		}
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("not(");
		buf.append(getArgument(0).pprint());
		buf.append(')');
		return buf.toString();
	}
}
