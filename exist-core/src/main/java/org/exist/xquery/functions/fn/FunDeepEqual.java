/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.fn;

import com.ibm.icu.text.Collator;
import io.lacuna.bifurcan.IEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.ReferenceNode;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Constants.StringTruncationOperator;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.ValueComparison;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
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

import java.util.Objects;

/**
 * Implements the fn:deep-equal library function.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class FunDeepEqual extends CollatingFunction {

    protected static final Logger logger = LogManager.getLogger(FunDeepEqual.class);

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("deep-equal", Function.BUILTIN_FUNCTION_NS),
            "Returns true() iff every item in $items-1 is deep-equal to the item " +
            "at the same position in $items-2, false() otherwise. " +
            "If both $items-1 and $items-2 are the empty sequence, returns true(). ",
            new SequenceType[] {
                new FunctionParameterSequenceType("items-1", Type.ITEM,
                    Cardinality.ZERO_OR_MORE, "The first item sequence"),
                new FunctionParameterSequenceType("items-2", Type.ITEM,
                    Cardinality.ZERO_OR_MORE, "The second item sequence")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                "true() if the sequences are deep-equal, false() otherwise")
            ),
        new FunctionSignature(
            new QName("deep-equal", Function.BUILTIN_FUNCTION_NS),
            "Returns true() iff every item in $items-1 is deep-equal to the item " +
            "at the same position in $items-2, false() otherwise. " +
            "If both $items-1 and $items-2 are the empty sequence, returns true(). " +
            "Comparison collation is specified by $collation-uri. " + 
            THIRD_REL_COLLATION_ARG_EXAMPLE,
            new SequenceType[] {
                new FunctionParameterSequenceType("items-1", Type.ITEM,
                    Cardinality.ZERO_OR_MORE, "The first item sequence"),
                new FunctionParameterSequenceType("items-2", Type.ITEM,
                    Cardinality.ZERO_OR_MORE, "The second item sequence"),
                new FunctionParameterSequenceType("collation-uri", Type.STRING,
                    Cardinality.EXACTLY_ONE, "The collation URI")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                "true() if the sequences are deep-equal, false() otherwise")
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
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT ITEM", contextItem.toSequence());}
        }
        final Sequence[] args = getArguments(contextSequence, contextItem);
        final Collator collator = getCollator(contextSequence, contextItem, 3);
        final Sequence result = BooleanValue.valueOf(deepEqualsSeq(args[0], args[1], collator));
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        return result;
    }

    public static boolean deepEqualsSeq(Sequence sa, Sequence sb, Collator collator) {
        final int length = sa.getItemCount();
        if (length != sb.getItemCount()) {
            return false;
        } else {
            for (int i = 0; i < length; i++) {
                if (!deepEquals(sa.itemAt(i), sb.itemAt(i), collator)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static boolean deepEquals(Item a, Item b, Collator collator) {
        try {
            if (a.getType() == Type.ARRAY_ITEM || b.getType() == Type.ARRAY_ITEM) {
                if (a.getType() != b.getType()) {
                    return false;
                }
                final ArrayType ar = (ArrayType) a;
                final ArrayType br = (ArrayType) b;
                if (ar.getSize() != br.getSize()) {
                    return false;
                }
                for (int i = 0; i < ar.getSize(); i++) {
                    if (!deepEqualsSeq(ar.get(i), br.get(i), collator)) {
                        return false;
                    }
                }
                return true;
            }
            if (a.getType() == Type.MAP_ITEM || b.getType() == Type.MAP_ITEM) {
                if (a.getType() != b.getType()) {
                    return false;
                }
                final AbstractMapType amap = (AbstractMapType) a;
                final AbstractMapType bmap = (AbstractMapType) b;
                if (amap.size() != bmap.size()) {
                    return false;
                }
                for (final IEntry<AtomicValue, Sequence> aentry: amap) {
                    if (!bmap.contains(aentry.key())) {
                        return false;
                    }
                    if (!deepEqualsSeq(aentry.value(), bmap.get(aentry.key()), collator)) {
                        return false;
                    }
                }
                return true;
            }
            final boolean aAtomic = Type.subTypeOf(a.getType(), Type.ANY_ATOMIC_TYPE);
            final boolean bAtomic = Type.subTypeOf(b.getType(), Type.ANY_ATOMIC_TYPE);
            if (aAtomic || bAtomic) {
                if (!aAtomic || !bAtomic)
                    {return false;}
                try {
                    final AtomicValue av = (AtomicValue) a;
                    final AtomicValue bv = (AtomicValue) b;
                    if (Type.subTypeOfUnion(av.getType(), Type.NUMERIC) &&
                        Type.subTypeOfUnion(bv.getType(), Type.NUMERIC)) {
                        //or if both values are NaN
                        if (((NumericValue) a).isNaN() && ((NumericValue) b).isNaN())
                            {return true;}
                    }
                    return ValueComparison.compareAtomic(collator, av, bv,
                            StringTruncationOperator.NONE, Comparison.EQ);
                } catch (final XPathException e) {
                    return false;
                }
            }
            if (a.getType() != b.getType())
                {return false;}
            final NodeValue nva = (NodeValue) a;
            final NodeValue nvb = (NodeValue) b;
            if (nva == nvb) {return true;}
            try {
                //Don't use this shortcut for in-memory nodes
                //since the symbol table is ignored.
                if (nva.getImplementationType() != NodeValue.IN_MEMORY_NODE &&
                    nva.equals(nvb))
                    {return true;} // shortcut!
            } catch (final XPathException e) {
                // apparently incompatible values, do manual comparison
            }
            final Node na;
            final Node nb;
            switch(a.getType()) {
            case Type.DOCUMENT:
                // NodeValue.getNode() doesn't seem to work for document nodes
                na = nva instanceof Node ? (Node) nva : ((NodeProxy) nva).getOwnerDocument();
                nb = nvb instanceof Node ? (Node) nvb : ((NodeProxy) nvb).getOwnerDocument();
                return compareContents(na, nb);
            case Type.ELEMENT:
                na = nva.getNode();
                nb = nvb.getNode();
                return compareElements(na, nb);
            case Type.ATTRIBUTE:
                na = nva.getNode();
                nb = nvb.getNode();
                return compareNames(na, nb)
                    && safeEquals(na.getNodeValue(), nb.getNodeValue());
            case Type.PROCESSING_INSTRUCTION:
            case Type.NAMESPACE:
                na = nva.getNode(); nb = nvb.getNode();
                return safeEquals(na.getNodeName(), nb.getNodeName()) &&
                    safeEquals(nva.getStringValue(), nvb.getStringValue());
            case Type.TEXT:
            case Type.COMMENT:
                return safeEquals(nva.getStringValue(), nvb.getStringValue());
            default:
                throw new RuntimeException("unexpected item type " + Type.getTypeName(a.getType()));
            }
        } catch (final XPathException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static boolean compareElements(Node a, Node b) {
        return compareNames(a, b) && compareAttributes(a, b) &&
            compareContents(a, b);
    }

    private static boolean compareContents(Node a, Node b) {
        a = findNextTextOrElementNode(a.getFirstChild());
        b = findNextTextOrElementNode(b.getFirstChild());
        while (!(a == null || b == null)) {
            final int nodeTypeA = getEffectiveNodeType(a);
            final int nodeTypeB = getEffectiveNodeType(b);
            if (nodeTypeA != nodeTypeB)
                {return false;}
            switch (nodeTypeA) {
            case Node.TEXT_NODE:
                if (a.getNodeType() == NodeImpl.REFERENCE_NODE &&
                        b.getNodeType() == NodeImpl.REFERENCE_NODE) {
                    if (!safeEquals(((ReferenceNode)a).getReference().getNodeValue(),
                            ((ReferenceNode)b).getReference().getNodeValue()))
                        {return false;}
                } else if (a.getNodeType() == NodeImpl.REFERENCE_NODE) {
                    if (!safeEquals(((ReferenceNode)a).getReference().getNodeValue(),
                            b.getNodeValue()))
                        {return false;}
                } else if (b.getNodeType() == NodeImpl.REFERENCE_NODE) {
                    if (!safeEquals(a.getNodeValue(), 
                            ((ReferenceNode)b).getReference().getNodeValue()))
                        {return false;}
                } else {
                    if (!safeEquals(a.getNodeValue(), b.getNodeValue()))
                        {return false;}
                }
                break;
            case Node.ELEMENT_NODE:
                if (!compareElements(a, b))
                    {return false;}
                break;
            default:
                throw new RuntimeException("unexpected node type " + nodeTypeA);
            }
            a = findNextTextOrElementNode(a.getNextSibling());
            b = findNextTextOrElementNode(b.getNextSibling());
        }
        return a == b; // both null
    }

    private static Node findNextTextOrElementNode(Node n) {
        for(;;) {
            if (n == null)
                {return null;}
            final int nodeType = getEffectiveNodeType(n);
            if (nodeType == Node.ELEMENT_NODE || nodeType == Node.TEXT_NODE)
                {return n;}
            n = n.getNextSibling();
        }
    }

    private static int getEffectiveNodeType(Node n) {
        int nodeType = n.getNodeType();
        if (nodeType == NodeImpl.REFERENCE_NODE) {
            nodeType = ((ReferenceNode) n).getReference().getNode().getNodeType();
        }
        return nodeType;
    }

    private static boolean compareAttributes(Node a, Node b) {
        final NamedNodeMap nnma = a.getAttributes();
        final NamedNodeMap nnmb = b.getAttributes();
        if (getAttrCount(nnma) != getAttrCount(nnmb)) {return false;}
        for (int i = 0; i < nnma.getLength(); i++) {
            final Node ta = nnma.item(i);
            final String nsA = ta.getNamespaceURI();
            if (nsA != null && Namespaces.XMLNS_NS.equals(nsA)) {
                continue;
            }
            final Node tb = ta.getLocalName() == null ?
                nnmb.getNamedItem(ta.getNodeName()) :
                nnmb.getNamedItemNS(ta.getNamespaceURI(), ta.getLocalName());
            if (tb == null || !safeEquals(ta.getNodeValue(), tb.getNodeValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Return the number of real attributes in the map. Filter out
     * xmlns namespace attributes.
     */
    private static int getAttrCount(NamedNodeMap nnm) {
        int count = 0;
        for (int i=0; i<nnm.getLength(); i++) {
            final Node n = nnm.item(i);
            final String ns = n.getNamespaceURI();
            if (ns == null || !Namespaces.XMLNS_NS.equals(ns)) {
                ++count;
            }
        }
        return count;
    }

    private static boolean compareNames(Node a, Node b) {
        if (a.getLocalName() != null || b.getLocalName() != null) {
            return safeEquals(a.getNamespaceURI(), b.getNamespaceURI()) &&
                safeEquals(a.getLocalName(), b.getLocalName());
        }
        return safeEquals(a.getNodeName(), b.getNodeName());
    }

    private static boolean safeEquals(Object a, Object b) {
        return Objects.equals(a, b);
    }

}
