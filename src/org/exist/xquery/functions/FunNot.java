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

package org.exist.xquery.functions;

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunNot extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("not", Module.BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				 new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE));

	public FunNot(XQueryContext context) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.subTypeOf(getArgument(0).returnsType(), Type.NODE)
			? Type.NODE
			: Type.BOOLEAN;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET | getArgument(0).getDependencies();
	}
	
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		Expression arg = getArgument(0);
		
		// case 1: if the argument expression returns a node set,
		// subtract the set from the context node set and return
		// the remaining set
		if (Type.subTypeOf(arg.returnsType(), Type.NODE) &&
			(arg.getDependencies() & Dependency.CONTEXT_ITEM) == 0) {
			if (contextSequence.getLength() == 0) {
				// special treatment if the context sequence is empty:
				// within a predicate, we just return the empty sequence
				// otherwise evaluate the argument and return a boolean result
// TODO: why do we need this special case here?			    
//				if (inPredicate)
//					return Sequence.EMPTY_SEQUENCE;
//				else
					return evalBoolean(contextSequence, contextItem, arg);
			}
			NodeSet result = new ExtArrayNodeSet();
			if(contextSequence.getLength() > 0)
				result.addAll(contextSequence);
			NodeProxy current;
			if (inPredicate) {
				for (SequenceIterator i = result.iterate(); i.hasNext();) {
					current = (NodeProxy) i.nextItem();
					current.addContextNode(current);
				}
			}
			// evaluate argument expression
			Sequence argSeq =
				arg.eval(contextSequence, contextItem);
			NodeSet argSet = argSeq.toNodeSet().getContextNodes(true);
			return result.except(argSet);
			
		// case 2: simply invert the boolean value
		} else {
			return evalBoolean(contextSequence, contextItem, arg);
		}
	}

	/**
	 * @param contextSequence
	 * @param contextItem
	 * @param arg
	 * @return
	 * @throws XPathException
	 */
	private Sequence evalBoolean(Sequence contextSequence, Item contextItem, Expression arg) throws XPathException {
		Sequence seq =
			arg.eval(contextSequence, contextItem);
		return seq.effectiveBooleanValue() ? BooleanValue.FALSE : BooleanValue.TRUE;
	}
}
