/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
package org.exist.xquery.functions;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.memtree.AttributeImpl;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;

/**
 * Implements the fn:deep-equal library function.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class FunDeepEqual extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("deep-equal", Module.BUILTIN_FUNCTION_NS),
			new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE), 
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.ONE));

	public FunDeepEqual(XQueryContext context) {
		super(context, signature);
	}

	public int getDependencies() {
		return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence[] args = getArguments(contextSequence, contextItem);
		int length = args[0].getLength();
		if (length != args[1].getLength()) return BooleanValue.FALSE;
		for (int i=0; i<length; i++) {
			if (!deepEquals(args[0].itemAt(i), args[1].itemAt(i))) return BooleanValue.FALSE;
		}
		return BooleanValue.TRUE;
	}
	
	private boolean deepEquals(Item a, Item b) {
		try {
			if (Type.subTypeOf(a.getType(), Type.ATOMIC) || Type.subTypeOf(b.getType(), Type.ATOMIC)) {
				if (!Type.subTypeOf(a.getType(), Type.ATOMIC) || !Type.subTypeOf(b.getType(), Type.ATOMIC)) return false;
				return a.atomize().compareTo(context.getDefaultCollator(), b.atomize()) == 0;
			} else {
//				assert Type.subTypeOf(a.getType(), Type.NODE);
//				assert Type.subTypeOf(b.getType(), Type.NODE);
				if (a.getType() != b.getType()) return false;
				NodeValue nva = (NodeValue) a, nvb = (NodeValue) b;
				if (nva.equals(nvb)) return true;		// shortcut!
				Node na, nb;
				switch(a.getType()) {
					case Type.DOCUMENT:
						// NodeValue.getNode() doesn't seem to work for document nodes
						na = nva instanceof Node ? (Node) nva : ((NodeProxy) nva).getDocument();
						nb = nvb instanceof Node ? (Node) nvb : ((NodeProxy) nvb).getDocument();
						return compareContents(na, nb);
					case Type.ELEMENT:
						na = nva.getNode(); nb = nvb.getNode();
						return compareElements(na, nb);
					case Type.ATTRIBUTE:
						na = nva.getNode(); nb = nvb.getNode();
						return
							compareNames(na, nb)
							&& safeEquals(na.getNodeValue(), nb.getNodeValue());
					case Type.PROCESSING_INSTRUCTION:
					case Type.NAMESPACE:
						na = nva.getNode(); nb = nvb.getNode();
						return
							safeEquals(na.getNodeName(), nb.getNodeName())
							&& safeEquals(nva.getStringValue(), nvb.getStringValue());
					case Type.TEXT:
					case Type.COMMENT:
						return safeEquals(nva.getStringValue(), nvb.getStringValue());
					
					default: throw new RuntimeException("unexpected item type " + Type.getTypeName(a.getType()));
				}
			}
		} catch (XPathException e) {
			return false;
		}
	}
	
	private boolean compareElements(Node a, Node b) {
		return
			compareNames(a, b)
			&& compareAttributes(a, b)
			&& compareContents(a, b);
	}
	
	private boolean compareContents(Node a, Node b) {
		a = findNextTextOrElementNode(a.getFirstChild());
		b = findNextTextOrElementNode(b.getFirstChild());
		while(!(a == null || b == null)) {
			if (a.getNodeType() != b.getNodeType()) return false;
			switch(a.getNodeType()) {
				case Node.TEXT_NODE:
					if (!safeEquals(a.getNodeValue(), b.getNodeValue())) return false;
					break;
				case Node.ELEMENT_NODE:
					if (!compareElements(a, b)) return false;
					break;
				default:
					throw new RuntimeException("unexpected node type " + a.getNodeType());
			}
			a = findNextTextOrElementNode(a.getNextSibling());
			b = findNextTextOrElementNode(b.getNextSibling());
		}
		return a == b;		// both null
	}
	
	private Node findNextTextOrElementNode(Node n) {
		while (n != null && !(n.getNodeType() == Node.ELEMENT_NODE || n.getNodeType() == Node.TEXT_NODE)) {
			n = n.getNextSibling();
		}
		return n;
	}
	
	private boolean compareAttributes(Node a, Node b) {
		NamedNodeMap nnma = a.getAttributes(), nnmb = b.getAttributes();
		if (nnma.getLength() != nnmb.getLength()) return false;
		for (int i=0; i<nnma.getLength(); i++) {
			Node ta = nnma.item(i);
			Node tb = ta.getLocalName() == null ? nnmb.getNamedItem(ta.getNodeName()) : nnmb.getNamedItemNS(ta.getNamespaceURI(), ta.getLocalName());
			if (tb == null || !safeEquals(ta.getNodeValue(), tb.getNodeValue())) return false;
		}
		return true;
	}
	
	private boolean compareNames(Node a, Node b) {
		if (a.getLocalName() != null || b.getLocalName() != null) {
			return safeEquals(a.getNamespaceURI(), b.getNamespaceURI()) && safeEquals(a.getLocalName(), b.getLocalName());
		} else {
			return safeEquals(a.getNodeName(), b.getNodeName());
		}
	}
	
	private boolean safeEquals(Object a, Object b) {
		return a == null ? b == null : a.equals(b);
	}

}
