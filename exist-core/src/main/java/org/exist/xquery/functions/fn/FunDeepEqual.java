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
import org.exist.storage.DBBroker;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
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

import javax.annotation.Nullable;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;

/**
 * Implements the fn:deep-equal library function.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
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
        final Sequence result = BooleanValue.valueOf(
                deepEqualsSeq(args[0], args[1], collator, context.getBroker()));
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        return result;
    }

    /**
     * Deep comparison of two Sequences according to the rules of fn:deep-equals.
     *
     * @param sequence1 the first Sequence to be compared.
     * @param sequence2 the second Sequence to be compared.
     * @param collator a collator to use for the comparison, or null to use the default collator.
     *
     * @return a negative integer, zero, or a positive integer, if the first argument is less than, equal to, or greater than the second.
     */
    public static int deepCompareSeq(final Sequence sequence1, final Sequence sequence2, @Nullable final Collator collator) {
        return deepCompareSeq(sequence1, sequence2, collator, null);
    }

    /**
     * Broker-aware variant of {@link #deepCompareSeq(Sequence, Sequence, Collator)}.
     *
     * <p>When a non-null {@code broker} is supplied and an item pair is
     * a persistent {@code DOCUMENT} or {@code ELEMENT}, the comparison
     * uses {@link FunDeepEqualStreamingComparator} as a fast path. Other
     * shapes (atomic, memtree, attribute, text, map, array) fall through
     * to the legacy recursive path.
     *
     * @param sequence1 first sequence.
     * @param sequence2 second sequence.
     * @param collator collation, or {@code null} for code-point.
     * @param broker active broker, or {@code null} to disable the fast path.
     * @return {@link Constants#EQUAL} / {@link Constants#INFERIOR} / {@link Constants#SUPERIOR}.
     */
    public static int deepCompareSeq(final Sequence sequence1, final Sequence sequence2,
            @Nullable final Collator collator, @Nullable final DBBroker broker) {
        if (sequence1 == sequence2) {
            return Constants.EQUAL;
        }

        final int sequence1Count = sequence1.getItemCount();
        final int sequence2Count = sequence2.getItemCount();
        if (sequence1Count == sequence2Count) {
            for (int i = 0; i < sequence1Count; i++) {
                final Item item1 = sequence1.itemAt(i);
                final Item item2 = sequence2.itemAt(i);

                final int comparison = deepCompare(item1, item2, collator, broker);
                if (comparison != Constants.EQUAL) {
                    return comparison;
                }
            }
            return Constants.EQUAL;
        } else {
            return sequence1Count < sequence2Count ? Constants.INFERIOR : Constants.SUPERIOR;
        }
    }

    /**
     * Deep comparison of two Items according to the rules of fn:deep-equals.
     *
     * @param item1 the first Item to be compared.
     * @param item2 the second Item to be compared.
     * @param collator a collator to use for the comparison, or null to use the default collator.
     *
     * @return a negative integer, zero, or a positive integer, if the first argument is less than, equal to, or greater than the second.
     *
     * @throws UnexpectedItemTypeException if an item has an unknown type.
     */
    public static int deepCompare(final Item item1, final Item item2, @Nullable final Collator collator) {
        return deepCompare(item1, item2, collator, null);
    }

    /**
     * Broker-aware variant of {@link #deepCompare(Item, Item, Collator)}.
     *
     * <p>When a non-null {@code broker} is supplied and the item pair is
     * a persistent {@code DOCUMENT} or {@code ELEMENT}, the comparison
     * uses {@link FunDeepEqualStreamingComparator} as a fast path. On
     * stream / IO failure the call falls through to the legacy recursive
     * path so correctness is preserved.
     *
     * @param item1 first item.
     * @param item2 second item.
     * @param collator collation, or {@code null} for code-point.
     * @param broker active broker, or {@code null} to disable the fast path.
     * @return {@link Constants#EQUAL} / {@link Constants#INFERIOR} / {@link Constants#SUPERIOR}.
     */
    public static int deepCompare(final Item item1, final Item item2,
            @Nullable final Collator collator, @Nullable final DBBroker broker) {
        if (item1 == item2) {
            return Constants.EQUAL;
        }

        try {
            if (item1.getType() == Type.ARRAY_ITEM || item2.getType() == Type.ARRAY_ITEM) {
                return compareArrayItems(item1, item2, collator, broker);
            }
            if (item1.getType() == Type.MAP_ITEM || item2.getType() == Type.MAP_ITEM) {
                return compareMapItems(item1, item2, collator, broker);
            }

            final boolean item1IsAtomic = Type.subTypeOf(item1.getType(), Type.ANY_ATOMIC_TYPE);
            final boolean item2IsAtomic = Type.subTypeOf(item2.getType(), Type.ANY_ATOMIC_TYPE);
            if (item1IsAtomic || item2IsAtomic) {
                return compareAtomicItems(item1, item2, item1IsAtomic, item2IsAtomic, collator);
            }

            return compareNodeItems(item1, item2, collator, broker);
        } catch (final XPathException e) {
            logger.error(e.getMessage(), e);
            return Constants.INFERIOR;
        }
    }

    private static int compareArrayItems(final Item item1, final Item item2,
            @Nullable final Collator collator, @Nullable final DBBroker broker) throws XPathException {
        if (item1.getType() != item2.getType()) {
            return Constants.INFERIOR;
        }
        final ArrayType array1 = (ArrayType) item1;
        final ArrayType array2 = (ArrayType) item2;
        final int array1Size = array1.getSize();
        final int array2Size = array2.getSize();
        if (array1Size != array2Size) {
            return array1Size < array2Size ? Constants.INFERIOR : Constants.SUPERIOR;
        }
        for (int i = 0; i < array1Size; i++) {
            final int comparison = deepCompareSeq(array1.get(i), array2.get(i), collator, broker);
            if (comparison != Constants.EQUAL) {
                return comparison;
            }
        }
        return Constants.EQUAL;
    }

    private static int compareMapItems(final Item item1, final Item item2,
            @Nullable final Collator collator, @Nullable final DBBroker broker) throws XPathException {
        if (item1.getType() != item2.getType()) {
            return Constants.INFERIOR;
        }
        final AbstractMapType map1 = (AbstractMapType) item1;
        final AbstractMapType map2 = (AbstractMapType) item2;
        final int map1Size = map1.size();
        final int map2Size = map2.size();
        if (map1Size != map2Size) {
            return map1Size < map2Size ? Constants.INFERIOR : Constants.SUPERIOR;
        }
        for (final IEntry<AtomicValue, Sequence> entry1 : map1) {
            if (!map2.contains(entry1.key())) {
                return Constants.SUPERIOR;
            }
            final int comparison = deepCompareSeq(entry1.value(), map2.get(entry1.key()), collator, broker);
            if (comparison != Constants.EQUAL) {
                return comparison;
            }
        }
        return Constants.EQUAL;
    }

    private static int compareAtomicItems(final Item item1, final Item item2,
            final boolean item1IsAtomic, final boolean item2IsAtomic, @Nullable final Collator collator) {
        if (!item1IsAtomic) {
            return Constants.SUPERIOR;
        }
        if (!item2IsAtomic) {
            return Constants.INFERIOR;
        }
        try {
            final AtomicValue av = (AtomicValue) item1;
            final AtomicValue bv = (AtomicValue) item2;
            if (Type.subTypeOfUnion(av.getType(), Type.NUMERIC)
                    && Type.subTypeOfUnion(bv.getType(), Type.NUMERIC)
                    && ((NumericValue) item1).isNaN()
                    && ((NumericValue) item2).isNaN()) {
                return Constants.EQUAL;
            }
            return ValueComparison.compareAtomic(collator, av, bv);
        } catch (final XPathException e) {
            if (logger.isTraceEnabled()) {
                logger.trace(e.getMessage());
            }
            return Constants.INFERIOR;
        }
    }

    private static int compareNodeItems(final Item item1, final Item item2,
            @Nullable final Collator collator, @Nullable final DBBroker broker) throws XPathException {
        if (item1.getType() != item2.getType()) {
            return Constants.INFERIOR;
        }
        final NodeValue nva = (NodeValue) item1;
        final NodeValue nvb = (NodeValue) item2;
        // NOTE(AR): intentional reference equality check
        if (nva == nvb) {
            return Constants.EQUAL;
        }

        try {
            //Don't use this shortcut for in-memory nodes
            //since the symbol table is ignored.
            if (nva.getImplementationType() != NodeValue.IN_MEMORY_NODE && nva.equals(nvb)) {
                return Constants.EQUAL;  // shortcut!
            }
        } catch (final XPathException e) {
            // apparently incompatible values, do manual comparison
        }

        return switch (item1.getType()) {
            case Type.DOCUMENT -> compareDocumentItems(nva, nvb, collator, broker);
            case Type.ELEMENT -> compareElementItems(nva, nvb, collator, broker);
            case Type.ATTRIBUTE -> compareAttributeItems(nva, nvb, collator);
            case Type.PROCESSING_INSTRUCTION, Type.NAMESPACE -> comparePiOrNamespaceItems(nva, nvb, collator);
            case Type.TEXT, Type.COMMENT -> safeCompare(nva.getStringValue(), nvb.getStringValue(), collator);
            default -> throw new UnexpectedItemTypeException(item1);
        };
    }

    private static int compareDocumentItems(final NodeValue nva, final NodeValue nvb,
            @Nullable final Collator collator, @Nullable final DBBroker broker) {
        // GH-4050 fast path: persistent-DOM streaming comparator.
        // Falls through to legacy on stream/IO failure to preserve correctness.
        final Integer streamed = tryStreamingCompare(nva, nvb, collator, broker, /*subtree=*/false);
        if (streamed != null) {
            return streamed;
        }
        final Node node1 = nva instanceof Node nnva ? nnva : ((NodeProxy) nva).getOwnerDocument();
        final Node node2 = nvb instanceof Node nnvb ? nnvb : ((NodeProxy) nvb).getOwnerDocument();
        return compareContents(node1, node2, collator);
    }

    private static int compareElementItems(final NodeValue nva, final NodeValue nvb,
            @Nullable final Collator collator, @Nullable final DBBroker broker) {
        final Integer streamed = tryStreamingCompare(nva, nvb, collator, broker, /*subtree=*/true);
        if (streamed != null) {
            return streamed;
        }
        return compareElements(nva.getNode(), nvb.getNode(), collator);
    }

    private static int compareAttributeItems(final NodeValue nva, final NodeValue nvb,
            @Nullable final Collator collator) {
        final Node node1 = nva.getNode();
        final Node node2 = nvb.getNode();
        final int attributeNameComparison = compareNames(node1, node2);
        if (attributeNameComparison != Constants.EQUAL) {
            return attributeNameComparison;
        }
        return safeCompare(node1.getNodeValue(), node2.getNodeValue(), collator);
    }

    private static int comparePiOrNamespaceItems(final NodeValue nva, final NodeValue nvb,
            @Nullable final Collator collator) throws XPathException {
        final Node node1 = nva.getNode();
        final Node node2 = nvb.getNode();
        final int nameComparison = safeCompare(node1.getNodeName(), node2.getNodeName(), null);
        if (nameComparison != Constants.EQUAL) {
            return nameComparison;
        }
        return safeCompare(nva.getStringValue(), nvb.getStringValue(), collator);
    }

    @Nullable
    private static Integer tryStreamingCompare(final NodeValue nva, final NodeValue nvb,
            @Nullable final Collator collator, @Nullable final DBBroker broker, final boolean subtree) {
        if (broker == null
                || !(nva instanceof NodeProxy npa)
                || !(nvb instanceof NodeProxy npb)
                || nva.getImplementationType() != NodeValue.PERSISTENT_NODE
                || nvb.getImplementationType() != NodeValue.PERSISTENT_NODE) {
            return null;
        }
        try {
            return FunDeepEqualStreamingComparator.compare(broker, npa, npb, subtree, collator);
        } catch (final XMLStreamException | IOException | RuntimeException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Streaming deep-equal fast path failed, falling back: " + e.getMessage());
            }
            return null;
        }
    }

    public static class UnexpectedItemTypeException extends RuntimeException {
        public UnexpectedItemTypeException(final Item item) {
            super("Unexpected item type: " + Type.getTypeName(item.getType()));
        }
    }


    /**
     * Deep equality of two Sequences according to the rules of fn:deep-equals.
     *
     * @param sequence1 the first Sequence to be compared.
     * @param sequence2 the second Sequence to be compared.
     * @param collator a collator to use for the comparison, or null to use the default collator.
     *
     * @return true if the Sequences are equal according to the rules of fn:deep-equals, false otherwise.
     */
    public static boolean deepEqualsSeq(final Sequence sequence1, final Sequence sequence2, @Nullable final Collator collator) {
        return deepCompareSeq(sequence1, sequence2, collator) == Constants.EQUAL;
    }

    /**
     * Broker-aware variant of {@link #deepEqualsSeq(Sequence, Sequence, Collator)}.
     *
     * @param sequence1 first sequence.
     * @param sequence2 second sequence.
     * @param collator collation, or {@code null} for code-point.
     * @param broker active broker, or {@code null} to disable the streaming
     * fast path on persistent-DOM nodes.
     * @return true iff the sequences are deep-equal.
     */
    public static boolean deepEqualsSeq(final Sequence sequence1, final Sequence sequence2,
            @Nullable final Collator collator, @Nullable final DBBroker broker) {
        return deepCompareSeq(sequence1, sequence2, collator, broker) == Constants.EQUAL;
    }

    /**
     * Deep equality of two Items according to the rules of fn:deep-equals.
     *
     * @param item1 the first Item to be compared.
     * @param item2 the second Item to be compared.
     * @param collator a collator to use for the comparison, or null to use the default collator.
     *
     * @return true if the Items are equal according to the rules of fn:deep-equals, false otherwise.
     */
    public static boolean deepEquals(final Item item1, final Item item2, @Nullable final Collator collator) {
        return deepCompare(item1, item2, collator) == Constants.EQUAL;
    }

    private static int compareElements(final Node a, final Node b, @Nullable final Collator collator) {
        int comparison = compareNames(a, b);
        if (comparison != Constants.EQUAL) {
            return comparison;
        }

        comparison = compareAttributes(a, b, collator);
        if (comparison != Constants.EQUAL) {
            return comparison;
        }

        return compareContents(a, b, collator);
    }

    private static int compareContents(Node a, Node b, @Nullable final Collator collator) {
        a = findNextTextOrElementNode(a.getFirstChild());
        b = findNextTextOrElementNode(b.getFirstChild());
        while (!(a == null || b == null)) {
            final int nodeTypeA = getEffectiveNodeType(a);
            final int nodeTypeB = getEffectiveNodeType(b);
            if (nodeTypeA != nodeTypeB) {
                return Constants.INFERIOR;
            }
            switch (nodeTypeA) {
            case Node.TEXT_NODE:
                final String nodeValueA = getNodeValue(a);
                final String nodeValueB = getNodeValue(b);
                final int textComparison = safeCompare(nodeValueA, nodeValueB, collator);
                if (textComparison != Constants.EQUAL) {
                    return textComparison;
                }
                break;
            case Node.ELEMENT_NODE:
                final int elementComparison = compareElements(a, b, collator);
                if (elementComparison != Constants.EQUAL) {
                    return elementComparison;
                }
                break;
            default:
                // Per XPath 3.1 §15.3.1, deep-equal compares only element and text
                // children; other node types (comments, PIs) are skipped.
                break;
            }
            a = findNextTextOrElementNode(a.getNextSibling());
            b = findNextTextOrElementNode(b.getNextSibling());
        }

        // NOTE(AR): intentional reference equality check
        if (a == b) {
            return Constants.EQUAL; // both null
        } else if (a == null) {
            return Constants.INFERIOR;
        } else {
            return Constants.SUPERIOR;
        }
    }

    private static String getNodeValue(final Node n) {
        if (n.getNodeType() == NodeImpl.REFERENCE_NODE) {
           return ((ReferenceNode)n).getReference().getNodeValue();
        } else {
            return n.getNodeValue();
        }
    }

    private static Node findNextTextOrElementNode(Node n) {
        for(;;) {
            if (n == null) {
                return null;
            }
            final int nodeType = getEffectiveNodeType(n);
            if (nodeType == Node.ELEMENT_NODE || nodeType == Node.TEXT_NODE) {
                return n;
            }
            n = n.getNextSibling();
        }
    }

    private static int getEffectiveNodeType(final Node n) {
        int nodeType = n.getNodeType();
        if (nodeType == NodeImpl.REFERENCE_NODE) {
            nodeType = ((ReferenceNode) n).getReference().getNode().getNodeType();
        }
        return nodeType;
    }

    private static int compareAttributes(final Node a, final Node b, @Nullable final Collator collator) {
        final NamedNodeMap nnma = a.getAttributes();
        final NamedNodeMap nnmb = b.getAttributes();

        final int aCount = getAttrCount(nnma);
        final int bCount = getAttrCount(nnmb);

        if (aCount == bCount) {
            for (int i = 0; i < nnma.getLength(); i++) {
                final Node ta = nnma.item(i);
                final String nsA = ta.getNamespaceURI();
                if (nsA != null && Namespaces.XMLNS_NS.equals(nsA)) {
                    continue;
                }
                final Node tb = ta.getLocalName() == null ? nnmb.getNamedItem(ta.getNodeName()) : nnmb.getNamedItemNS(ta.getNamespaceURI(), ta.getLocalName());
                if (tb == null) {
                    return Constants.SUPERIOR;
                }
                final int comparison = safeCompare(ta.getNodeValue(), tb.getNodeValue(), collator);
                if (comparison != Constants.EQUAL) {
                    return comparison;
                }
            }

            return Constants.EQUAL;
        } else {
            return aCount < bCount ? Constants.INFERIOR : Constants.SUPERIOR;
        }
    }

    /**
     * Return the number of real attributes in the map. Filter out
     * xmlns namespace attributes.
     */
    private static int getAttrCount(final NamedNodeMap nnm) {
        int count = 0;
        for (int i = 0; i < nnm.getLength(); i++) {
            final Node n = nnm.item(i);
            final String ns = n.getNamespaceURI();
            if (ns == null || !Namespaces.XMLNS_NS.equals(ns)) {
                ++count;
            }
        }
        return count;
    }

    private static int compareNames(final Node a, final Node b) {
        if (a.getLocalName() != null || b.getLocalName() != null) {
            final int nsComparison = safeCompare(a.getNamespaceURI(), b.getNamespaceURI(), null);
            if (nsComparison != Constants.EQUAL) {
                return nsComparison;
            }
            return safeCompare(a.getLocalName(), b.getLocalName(), null);
        }
        return safeCompare(a.getNodeName(), b.getNodeName(), null);
    }

    private static int safeCompare(@Nullable final String a, @Nullable final String b, @Nullable final Collator collator) {
        // NOTE(AR): intentional reference equality check
        if (a == b) {
            return Constants.EQUAL;
        }

        if (a == null) {
            return Constants.INFERIOR;
        }

        if (b == null) {
            return Constants.SUPERIOR;
        }

        if (collator != null) {
            return collator.compare(a, b);
        } else {
            return a.compareTo(b);
        }
    }
}
