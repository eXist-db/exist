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
import org.exist.memtree.NodeImpl;
import org.exist.memtree.ReferenceNode;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.GeneralComparison;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Implements the fn:deep-equal library function.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class FunDeepEqual extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("deep-equal", Function.BUILTIN_FUNCTION_NS),
			new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE), 
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.ONE));
//	TODO: collation as argument

	public FunDeepEqual(XQueryContext context) {
		super(context, signature);
	}

	public int getDependencies() {
		return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }       
        
        Sequence result;
		Sequence[] args = getArguments(contextSequence, contextItem);
		int length = args[0].getLength();
		if (length != args[1].getLength()) 
            result = BooleanValue.FALSE;
        else {
        	result = BooleanValue.TRUE;
    		for (int i = 0; i < length; i++) {
    			if (!deepEquals(args[0].itemAt(i), args[1].itemAt(i))) {
                    result = BooleanValue.FALSE;
                    break;
                }   
    		}    		
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;
	}
	
	private boolean deepEquals(Item a, Item b) {
		try {
			final boolean aAtomic = Type.subTypeOf(a.getType(), Type.ATOMIC);
			final boolean bAtomic = Type.subTypeOf(b.getType(), Type.ATOMIC);
			if (aAtomic || bAtomic) {
				if (!aAtomic || !bAtomic) return false;
				try {
					return GeneralComparison.compareAtomic(
						context.getDefaultCollator(), (AtomicValue) a, (AtomicValue) b, 
						context.isBackwardsCompatible(), Constants.TRUNC_NONE, Constants.EQ);
				} catch (XPathException e) {
					return false;
				}
			}
//		assert Type.subTypeOf(a.getType(), Type.NODE);
//		assert Type.subTypeOf(b.getType(), Type.NODE);
			if (a.getType() != b.getType()) return false;
			NodeValue nva = (NodeValue) a, nvb = (NodeValue) b;
			if (nva == nvb) return true;
			try {
				if (nva.equals(nvb)) return true;		// shortcut!
			} catch (XPathException e) {
				// apparently incompatible values, do manual comparison
			}
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
		} catch (XPathException e) {
            e.printStackTrace();
			return false;
		}
	}
	
	private boolean compareElements(Node a, Node b) {
		if (!compareNames(a, b))
            return false;
        if (!compareAttributes(a, b))
            return false;
		if (!compareContents(a, b))
            return false;
        return true;
	}
	
	private boolean compareContents(Node a, Node b) {
		a = findNextTextOrElementNode(a.getFirstChild());
		b = findNextTextOrElementNode(b.getFirstChild());
		while(!(a == null || b == null)) {
            int nodeTypeA = a.getNodeType();
            if (nodeTypeA == NodeImpl.REFERENCE_NODE) {
                //Retrieve the actual node type
                NodeProxy p = ((ReferenceNode)a).getReference();
                nodeTypeA = p.getNodeType();
            }
            int nodeTypeB = b.getNodeType();
            if (nodeTypeB == NodeImpl.REFERENCE_NODE) {
                //Retrieve the actual node type
                NodeProxy p = ((ReferenceNode)b).getReference();
                nodeTypeB = p.getNodeType();
            }             
			if (nodeTypeA != nodeTypeB) return false;
			switch(nodeTypeA) {
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
		if (n == null) 
			return null;
        int nodeType = n.getNodeType();
		if (nodeType == NodeImpl.REFERENCE_NODE) {
			//Retrieve the actual node type
			NodeProxy p = ((ReferenceNode)n).getReference();
            nodeType = p.getNodeType();
            n = p.getNode();
		}		
		while (!(nodeType == Node.ELEMENT_NODE || nodeType == Node.TEXT_NODE)) {
			n = n.getNextSibling();            
			if (n == null) 
				return null;
            nodeType = n.getNodeType();
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
		}
		return safeEquals(a.getNodeName(), b.getNodeName());
	}
	
	private boolean safeEquals(Object a, Object b) {
		return a == null ? b == null : a.equals(b);
	}

}
