/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xpath.functions;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.memtree.NodeImpl;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunRoot extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("root", BUILTIN_FUNCTION_NS),
			"Returns the root of the tree to which $arg belongs. This will usually, "
				+ "but not necessarily, be a document node.",
			new SequenceType[] { new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
			true);

	/**
	 * @param context
	 * @param signature
	 */
	public FunRoot(StaticContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		Item item = contextItem;
		if (getArgumentCount() > 0) {
			Sequence seq = getArgument(0).eval(contextSequence, contextItem);
			if (seq.getLength() == 0)
				return Sequence.EMPTY_SEQUENCE;
			item = seq.itemAt(0);
		}
		if (!Type.subTypeOf(item.getType(), Type.NODE))
			throw new XPathException("Context item is not a node; got " + Type.getTypeName(item.getType()));
		if (item instanceof NodeProxy)
			return new NodeProxy(((NodeProxy) item).doc, 1);
		else
			return (NodeImpl) ((NodeImpl) item).getOwnerDocument().getDocumentElement();
	}

}
