/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2009 The eXist Project
 * http://exist-db.org
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
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions;

import org.apache.log4j.Logger;

import java.text.Collator;

import org.exist.Namespaces;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.memtree.NodeImpl;
import org.exist.memtree.ReferenceNode;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.ValueComparison;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.NumericValue;
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
public class FunDeepEqual extends CollatingFunction {
	protected static final Logger logger = Logger.getLogger(FunDeepEqual.class);
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("deep-equal", Function.BUILTIN_FUNCTION_NS),
			"Returns true iff every item in $items-1 is deep-equal to the item at the same position in $items-2, " +
			"false otherwise. If both $items-1 and $items-2 are the empty sequence, returns true(). ",
			new SequenceType[] {
                new FunctionParameterSequenceType("items-1", Type.ITEM, Cardinality.ZERO_OR_MORE, "The first item sequence"), 
                new FunctionParameterSequenceType("items-2", Type.ITEM, Cardinality.ZERO_OR_MORE, "The second item sequence")
			},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ONE, "true() if the sequences are deep-equal, false() otherwise")
		),
		new FunctionSignature(
			new QName("deep-equal", Function.BUILTIN_FUNCTION_NS),
			"Returns true iff every item in $items-1 is deep-equal to the item at the same position in $items-2, " +
			"false otherwise. If both $items-1 and $items-2 are the empty sequence, returns true(). " +
			"Comparison collation is specified by $collation-uri. " + 
            THIRD_REL_COLLATION_ARG_EXAMPLE,
			new SequenceType[] {
                new FunctionParameterSequenceType("items-1", Type.ITEM, Cardinality.ZERO_OR_MORE, "The first item sequence"), 
                new FunctionParameterSequenceType("items-2", Type.ITEM, Cardinality.ZERO_OR_MORE, "The second item sequence"),
				new FunctionParameterSequenceType("collation-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collation URI")
			},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ONE, "true() if the sequences are deep-equal, false() otherwise")
		)
	};

	public FunDeepEqual(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public int getDependencies() {
		return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem)
        throws XPathException {
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
		Collator collator = getCollator(contextSequence, contextItem, 3);		
		int length = args[0].getItemCount();
		if (length != args[1].getItemCount()) 
            result = BooleanValue.FALSE;
        else {
        	result = BooleanValue.TRUE;
    		for (int i = 0; i < length; i++) {
    			if (!deepEquals(args[0].itemAt(i), args[1].itemAt(i), collator)) {
                    result = BooleanValue.FALSE;
                    break;
                }
    		}
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;
	}
	
	private boolean deepEquals(Item a, Item b, Collator collator) {
		try {
			final boolean aAtomic = Type.subTypeOf(a.getType(), Type.ATOMIC);
			final boolean bAtomic = Type.subTypeOf(b.getType(), Type.ATOMIC);
			if (aAtomic || bAtomic) {
				if (!aAtomic || !bAtomic) return false;
				try {
					AtomicValue av = (AtomicValue) a;
					AtomicValue bv = (AtomicValue) b;
					if (Type.subTypeOf(av.getType(), Type.NUMBER) && Type.subTypeOf(bv.getType(), Type.NUMBER)) {
						//or if both values are NaN
						if (((NumericValue) a).isNaN() && ((NumericValue) b).isNaN())
							return true;
					}
					return ValueComparison.compareAtomic(
						collator, av, bv, Constants.TRUNC_NONE, Constants.EQ);
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
				//Don't use this shortcut for in-memory nodes since the symbol table is ignored.
				if (nva.getImplementationType() != NodeValue.IN_MEMORY_NODE && nva.equals(nvb)) return true;		// shortcut!
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
				
				default: {
                    logger.error("unexpected item type " + Type.getTypeName(a.getType()));
                    throw new RuntimeException("unexpected item type " + Type.getTypeName(a.getType()));
                }
			}
		} catch (XPathException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
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
		while (!(a == null || b == null)) {
			int nodeTypeA = getEffectiveNodeType(a);
			int nodeTypeB = getEffectiveNodeType(b);
			if (nodeTypeA != nodeTypeB) return false;
			switch (nodeTypeA) {
				case Node.TEXT_NODE:
					if (a.getNodeType() == NodeImpl.REFERENCE_NODE && b.getNodeType() == NodeImpl.REFERENCE_NODE) {
						if (!safeEquals(((ReferenceNode)a).getReference().getNodeValue(), ((ReferenceNode)b).getReference().getNodeValue())) return false;
					} else if (a.getNodeType() == NodeImpl.REFERENCE_NODE) {
						if (!safeEquals(((ReferenceNode)a).getReference().getNodeValue(), b.getNodeValue())) return false;						
					} else if (b.getNodeType() == NodeImpl.REFERENCE_NODE) {
						if (!safeEquals(a.getNodeValue(), ((ReferenceNode)b).getReference().getNodeValue())) return false;						
					} else
						if (!safeEquals(a.getNodeValue(), b.getNodeValue())) return false;
					break;
				case Node.ELEMENT_NODE:
					if (!compareElements(a, b)) return false;
					break;
				default: {
					logger.error("unexpected node type " + nodeTypeA);
                    throw new RuntimeException("unexpected node type " + nodeTypeA);
                }
			}
			a = findNextTextOrElementNode(a.getNextSibling());
			b = findNextTextOrElementNode(b.getNextSibling());
		}
		return a == b;		// both null
	}
	
	private Node findNextTextOrElementNode(Node n) {
		for(;;) {
			if (n == null) return null;
			int nodeType = getEffectiveNodeType(n);
			if (nodeType == Node.ELEMENT_NODE || nodeType == Node.TEXT_NODE) return n;
			n = n.getNextSibling();
		}
	}
	
	private int getEffectiveNodeType(Node n) {
		int nodeType = n.getNodeType();
		if (nodeType == NodeImpl.REFERENCE_NODE) {
			nodeType = ((ReferenceNode) n).getReference().getNode().getNodeType();
		}
		return nodeType;
	}
	
	private boolean compareAttributes(Node a, Node b) {
		NamedNodeMap nnma = a.getAttributes(), nnmb = b.getAttributes();
		if (getAttrCount(nnma) != getAttrCount(nnmb)) return false;
        for (int i=0; i<nnma.getLength(); i++) {
			Node ta = nnma.item(i);
            if (Namespaces.XMLNS_NS.equals(ta.getNamespaceURI()))
                continue;
            Node tb = ta.getLocalName() == null ? nnmb.getNamedItem(ta.getNodeName()) : nnmb.getNamedItemNS(ta.getNamespaceURI(), ta.getLocalName());
			if (tb == null || !safeEquals(ta.getNodeValue(), tb.getNodeValue())) return false;
		}
		return true;
	}

    /**
     * Return the number of real attributes in the map. Filter out
     * xmlns namespace attributes.
     *
     * @param nnm
     * @return
     */
    private int getAttrCount(NamedNodeMap nnm) {
        int count = 0;
        for (int i=0; i<nnm.getLength(); i++) {
            Node n = nnm.item(i);
            if (!Namespaces.XMLNS_NS.equals(n.getNamespaceURI()))
                ++count;
        }
        return count;
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
