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
import org.exist.Namespaces;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.ReferenceNode;
import org.exist.xquery.Constants;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.InlineFunction;
import org.exist.xquery.ValueComparison;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import java.text.Normalizer;
import java.util.*;

/**
 * XQuery 4.0 deep-equal options and options-aware comparison engine.
 *
 * Holds the parsed option flags from the options map parameter and provides
 * comparison methods that respect those options.
 */
public class DeepEqualOptions {

    // Valid boolean option keys (no namespace)
    private static final Set<String> VALID_BOOLEAN_OPTIONS = Set.of(
            "base-uri", "comments", "debug",
            "id-property", "idrefs-property",
            "in-scope-namespaces", "namespace-prefixes", "nilled-property",
            "processing-instructions", "timezones", "type-annotations",
            "type-variety", "typed-values"
    );

    // Valid string-valued option keys
    private static final Set<String> VALID_STRING_OPTIONS = Set.of(
            "collation", "normalization-form", "whitespace"
    );

    // Valid boolean-valued option keys (not in VALID_BOOLEAN_OPTIONS)
    private static final Set<String> VALID_ORDERED_OPTIONS = Set.of(
            "ordered", "unordered", "map-order"
    );

    // All valid string keys (no namespace)
    private static final Set<String> ALL_VALID_KEYS;
    static {
        final Set<String> keys = new HashSet<>();
        keys.addAll(VALID_BOOLEAN_OPTIONS);
        keys.addAll(VALID_STRING_OPTIONS);
        keys.addAll(VALID_ORDERED_OPTIONS);
        ALL_VALID_KEYS = Collections.unmodifiableSet(keys);
    }

    // Option flags (defaults per XQ4 spec)
    public final boolean comments;             // default: false
    public final boolean processingInstructions; // default: false
    public final boolean ordered;              // default: true
    public final boolean namespacePrefixes;    // default: false
    public final boolean inScopeNamespaces;    // default: false
    public final boolean baseUri;              // default: false
    public final boolean idProperty;           // default: false
    public final boolean idrefsProperty;       // default: false
    public final boolean nilledProperty;       // default: false
    public final boolean timezones;            // default: true
    public final boolean typeAnnotations;      // default: false
    public final boolean typeVariety;          // default: false
    public final boolean typedValues;          // default: true
    public final boolean debug;                // default: false
    public final boolean mapOrder;             // default: false
    public final boolean unorderedElements;    // from 'ordered' key on element comparison

    public enum WhitespaceMode { PRESERVE, NORMALIZE, STRIP }
    public final WhitespaceMode whitespace;    // default: PRESERVE

    @Nullable
    public final Collator collator;

    /** Default options (XQ3.1 compatible behavior). */
    public static final DeepEqualOptions DEFAULTS = new DeepEqualOptions(
            false, false, true, false, false, false,
            false, false, false, true, false, false, true,
            false, false, WhitespaceMode.PRESERVE, null
    );

    private DeepEqualOptions(
            boolean comments, boolean processingInstructions, boolean ordered,
            boolean namespacePrefixes, boolean inScopeNamespaces, boolean baseUri,
            boolean idProperty, boolean idrefsProperty, boolean nilledProperty,
            boolean timezones, boolean typeAnnotations, boolean typeVariety,
            boolean typedValues, boolean debug, boolean mapOrder,
            WhitespaceMode whitespace, @Nullable Collator collator) {
        this.comments = comments;
        this.processingInstructions = processingInstructions;
        this.ordered = ordered;
        this.namespacePrefixes = namespacePrefixes;
        this.inScopeNamespaces = inScopeNamespaces;
        this.baseUri = baseUri;
        this.idProperty = idProperty;
        this.idrefsProperty = idrefsProperty;
        this.nilledProperty = nilledProperty;
        this.timezones = timezones;
        this.typeAnnotations = typeAnnotations;
        this.typeVariety = typeVariety;
        this.typedValues = typedValues;
        this.debug = debug;
        this.mapOrder = mapOrder;
        this.unorderedElements = !ordered;
        this.whitespace = whitespace;
        this.collator = collator;
    }

    /**
     * Parse an XQ4 options map into a DeepEqualOptions instance.
     * Validates all option keys and values per the spec.
     *
     * @param options the options map
     * @param context the XQuery context (for collation resolution)
     * @return parsed options
     * @throws XPathException XPTY0004 if any option key or value is invalid
     */
    public static DeepEqualOptions parse(final AbstractMapType options, final XQueryContext context) throws XPathException {
        boolean comments = false;
        boolean processingInstructions = false;
        boolean ordered = true;
        boolean namespacePrefixes = false;
        boolean inScopeNamespaces = false;
        boolean baseUri = false;
        boolean idProperty = false;
        boolean idrefsProperty = false;
        boolean nilledProperty = false;
        boolean timezones = true;
        boolean typeAnnotations = false;
        boolean typeVariety = false;
        boolean typedValues = true;
        boolean debug = false;
        boolean mapOrder = false;
        WhitespaceMode whitespace = WhitespaceMode.PRESERVE;
        Collator collator = context.getDefaultCollator();

        for (final IEntry<AtomicValue, Sequence> entry : options) {
            final AtomicValue key = entry.key();

            // Keys that are QNames in a namespace are ignored (vendor extensions)
            if (key.getType() == Type.QNAME) {
                final QNameValue qnv = (QNameValue) key;
                final String ns = qnv.getQName().getNamespaceURI();
                if (ns != null && !ns.isEmpty()) {
                    continue; // Ignore vendor extension options
                }
                // QName in no namespace → error
                throw new XPathException(ErrorCodes.XPTY0004,
                        "Option key in no namespace is not recognized: " + key.getStringValue());
            }

            final String keyStr = key.getStringValue();

            // Validate that the key is known
            if (!ALL_VALID_KEYS.contains(keyStr)) {
                throw new XPathException(ErrorCodes.XPTY0004,
                        "Unknown deep-equal option: '" + keyStr + "'");
            }

            final Sequence value = entry.value();

            if (VALID_BOOLEAN_OPTIONS.contains(keyStr)) {
                final boolean boolVal = parseBooleanOption(keyStr, value);
                switch (keyStr) {
                    case "comments" -> comments = boolVal;
                    case "processing-instructions" -> processingInstructions = boolVal;
                    case "namespace-prefixes" -> namespacePrefixes = boolVal;
                    case "in-scope-namespaces" -> inScopeNamespaces = boolVal;
                    case "base-uri" -> baseUri = boolVal;
                    case "id-property" -> idProperty = boolVal;
                    case "idrefs-property" -> idrefsProperty = boolVal;
                    case "nilled-property" -> nilledProperty = boolVal;
                    case "timezones" -> timezones = boolVal;
                    case "type-annotations" -> typeAnnotations = boolVal;
                    case "type-variety" -> typeVariety = boolVal;
                    case "typed-values" -> typedValues = boolVal;
                    case "debug" -> debug = boolVal;
                }
            } else if (VALID_ORDERED_OPTIONS.contains(keyStr)) {
                final boolean boolVal = parseBooleanOption(keyStr, value);
                switch (keyStr) {
                    case "ordered" -> ordered = boolVal;
                    case "unordered" -> ordered = !boolVal;
                    case "map-order" -> mapOrder = boolVal;
                }
            } else if ("collation".equals(keyStr)) {
                if (!value.isEmpty()) {
                    collator = context.getCollator(value.getStringValue());
                }
            } else if ("whitespace".equals(keyStr)) {
                if (!value.isEmpty()) {
                    final String wsVal = value.getStringValue();
                    whitespace = switch (wsVal) {
                        case "preserve" -> WhitespaceMode.PRESERVE;
                        case "normalize" -> WhitespaceMode.NORMALIZE;
                        case "strip" -> WhitespaceMode.STRIP;
                        default -> throw new XPathException(ErrorCodes.XPTY0004,
                                "Invalid whitespace option value: '" + wsVal + "'");
                    };
                }
            }
        }

        return new DeepEqualOptions(
                comments, processingInstructions, ordered,
                namespacePrefixes, inScopeNamespaces, baseUri,
                idProperty, idrefsProperty, nilledProperty,
                timezones, typeAnnotations, typeVariety, typedValues,
                debug, mapOrder, whitespace, collator
        );
    }

    /**
     * Parse a boolean option value using XQ4 option parameter conventions.
     * Accepts: xs:boolean, xs:string ("true"/"false"/"0"/"1"),
     * xs:integer (0/1), or nodes (effective boolean value).
     */
    private static boolean parseBooleanOption(final String key, final Sequence value) throws XPathException {
        if (value.isEmpty()) {
            return false;
        }

        final Item item = value.itemAt(0);

        // If it's already a boolean, use it directly
        if (item.getType() == Type.BOOLEAN) {
            return ((BooleanValue) item).getValue();
        }

        // Try casting to xs:boolean — accepts "true"/"false"/"0"/"1" and numeric 0/1
        try {
            final AtomicValue boolVal = item.atomize().convertTo(Type.BOOLEAN);
            return ((BooleanValue) boolVal).getValue();
        } catch (final XPathException e) {
            throw new XPathException(ErrorCodes.XPTY0004,
                    "Invalid value for boolean option '" + key + "': " + item.getStringValue());
        }
    }

    // ========================================================================
    // Options-aware deep comparison engine
    // ========================================================================

    /**
     * Deep-compare two sequences with options.
     */
    public int deepCompareSeq(final Sequence sequence1, final Sequence sequence2) {
        if (sequence1 == sequence2) {
            return Constants.EQUAL;
        }

        if (!ordered) {
            return deepCompareSeqUnordered(sequence1, sequence2);
        }

        final int count1 = sequence1.getItemCount();
        final int count2 = sequence2.getItemCount();
        if (count1 != count2) {
            return count1 < count2 ? Constants.INFERIOR : Constants.SUPERIOR;
        }

        for (int i = 0; i < count1; i++) {
            final int cmp = deepCompare(sequence1.itemAt(i), sequence2.itemAt(i));
            if (cmp != Constants.EQUAL) {
                return cmp;
            }
        }
        return Constants.EQUAL;
    }

    /**
     * Unordered sequence comparison: every item in seq1 must match some
     * item in seq2 (and vice versa, by equal counts of matches).
     */
    private int deepCompareSeqUnordered(final Sequence sequence1, final Sequence sequence2) {
        final int count1 = sequence1.getItemCount();
        final int count2 = sequence2.getItemCount();
        if (count1 != count2) {
            return count1 < count2 ? Constants.INFERIOR : Constants.SUPERIOR;
        }

        // For each item in seq1, find a matching item in seq2
        final boolean[] matched = new boolean[count2];
        for (int i = 0; i < count1; i++) {
            final Item item1 = sequence1.itemAt(i);
            boolean found = false;
            for (int j = 0; j < count2; j++) {
                if (!matched[j] && deepCompare(item1, sequence2.itemAt(j)) == Constants.EQUAL) {
                    matched[j] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return Constants.INFERIOR;
            }
        }
        return Constants.EQUAL;
    }

    /**
     * Deep-compare two items with options.
     */
    public int deepCompare(final Item item1, final Item item2) {
        if (item1 == item2) {
            return Constants.EQUAL;
        }

        try {
            // Array comparison
            if (item1.getType() == Type.ARRAY_ITEM || item2.getType() == Type.ARRAY_ITEM) {
                if (item1.getType() != item2.getType()) {
                    return Constants.INFERIOR;
                }
                final ArrayType array1 = (ArrayType) item1;
                final ArrayType array2 = (ArrayType) item2;
                if (array1.getSize() != array2.getSize()) {
                    return array1.getSize() < array2.getSize() ? Constants.INFERIOR : Constants.SUPERIOR;
                }
                for (int i = 0; i < array1.getSize(); i++) {
                    final int cmp = deepCompareSeq(array1.get(i), array2.get(i));
                    if (cmp != Constants.EQUAL) {
                        return cmp;
                    }
                }
                return Constants.EQUAL;
            }

            // Map comparison
            if (item1.getType() == Type.MAP_ITEM || item2.getType() == Type.MAP_ITEM) {
                if (item1.getType() != item2.getType()) {
                    return Constants.INFERIOR;
                }
                return compareMaps((AbstractMapType) item1, (AbstractMapType) item2);
            }

            // Function items: identity comparison via function-identity semantics
            if (Type.subTypeOf(item1.getType(), Type.FUNCTION) || Type.subTypeOf(item2.getType(), Type.FUNCTION)) {
                if (!Type.subTypeOf(item1.getType(), Type.FUNCTION) || !Type.subTypeOf(item2.getType(), Type.FUNCTION)) {
                    return Constants.INFERIOR;
                }
                return compareFunctionItems(item1, item2);
            }

            // Atomic values
            final boolean item1IsAtomic = Type.subTypeOf(item1.getType(), Type.ANY_ATOMIC_TYPE);
            final boolean item2IsAtomic = Type.subTypeOf(item2.getType(), Type.ANY_ATOMIC_TYPE);
            if (item1IsAtomic || item2IsAtomic) {
                if (!item1IsAtomic || !item2IsAtomic) {
                    return item1IsAtomic ? Constants.INFERIOR : Constants.SUPERIOR;
                }
                return compareAtomics((AtomicValue) item1, (AtomicValue) item2);
            }

            // Node comparison
            if (item1.getType() != item2.getType()) {
                return Constants.INFERIOR;
            }

            final NodeValue nva = (NodeValue) item1;
            final NodeValue nvb = (NodeValue) item2;
            if (nva == nvb) {
                return Constants.EQUAL;
            }

            switch (item1.getType()) {
                case Type.DOCUMENT:
                    return compareContents(
                            nva instanceof org.w3c.dom.Node n1 ? n1 : ((org.exist.dom.persistent.NodeProxy) nva).getOwnerDocument(),
                            nvb instanceof org.w3c.dom.Node n2 ? n2 : ((org.exist.dom.persistent.NodeProxy) nvb).getOwnerDocument());

                case Type.ELEMENT:
                    return compareElements(nva.getNode(), nvb.getNode());

                case Type.ATTRIBUTE:
                    final int attrNameCmp = compareNames(nva.getNode(), nvb.getNode());
                    if (attrNameCmp != Constants.EQUAL) {
                        return attrNameCmp;
                    }
                    // whitespace:normalize applies to attribute values, but strip does NOT
                    return safeCompare(
                            maybeNormalizeWSAttr(nva.getNode().getNodeValue()),
                            maybeNormalizeWSAttr(nvb.getNode().getNodeValue()),
                            collator);

                case Type.PROCESSING_INSTRUCTION:
                    return comparePIs(nva, nvb);

                case Type.NAMESPACE:
                    final int nsNameCmp = safeCompare(nva.getNode().getNodeName(), nvb.getNode().getNodeName(), null);
                    if (nsNameCmp != Constants.EQUAL) {
                        return nsNameCmp;
                    }
                    return safeCompare(nva.getStringValue(), nvb.getStringValue(), collator);

                case Type.TEXT:
                    return safeCompare(
                            maybeNormalizeWS(nva.getStringValue()),
                            maybeNormalizeWS(nvb.getStringValue()),
                            collator);

                case Type.COMMENT:
                    // Apply whitespace normalization to comment content if whitespace option is set
                    return safeCompare(
                            maybeNormalizeWS(nva.getStringValue()),
                            maybeNormalizeWS(nvb.getStringValue()),
                            collator);

                default:
                    return Constants.INFERIOR;
            }
        } catch (final XPathException e) {
            return Constants.INFERIOR;
        }
    }

    /**
     * Compare function items using function-identity semantics (XQ4).
     * Named functions with same name and arity are equal.
     * Anonymous functions use reference identity.
     */
    private static int compareFunctionItems(final Item item1, final Item item2) {
        if (item1 == item2) {
            return Constants.EQUAL;
        }
        if (item1 instanceof FunctionReference ref1 && item2 instanceof FunctionReference ref2) {
            final FunctionSignature sig1 = ref1.getSignature();
            final FunctionSignature sig2 = ref2.getSignature();
            final org.exist.dom.QName name1 = sig1.getName();
            final org.exist.dom.QName name2 = sig2.getName();
            // Both must be named functions (not inline/anonymous)
            if (name1 != null && name2 != null
                    && name1 != InlineFunction.INLINE_FUNCTION_QNAME
                    && name2 != InlineFunction.INLINE_FUNCTION_QNAME) {
                if (name1.equals(name2) && sig1.getArgumentCount() == sig2.getArgumentCount()) {
                    return Constants.EQUAL;
                }
            }
        }
        return Constants.INFERIOR;
    }

    private int compareMaps(final AbstractMapType map1, final AbstractMapType map2) {
        if (map1.size() != map2.size()) {
            return map1.size() < map2.size() ? Constants.INFERIOR : Constants.SUPERIOR;
        }

        for (final IEntry<AtomicValue, Sequence> entry1 : map1) {
            if (!map2.contains(entry1.key())) {
                return Constants.SUPERIOR;
            }
            final int cmp = deepCompareSeq(entry1.value(), map2.get(entry1.key()));
            if (cmp != Constants.EQUAL) {
                return cmp;
            }
        }
        return Constants.EQUAL;
    }

    private int compareAtomics(final AtomicValue av, final AtomicValue bv) {
        try {
            // Whitespace normalization for string-like atomics
            if (whitespace != WhitespaceMode.PRESERVE) {
                if (isStringLike(av) && isStringLike(bv)) {
                    final String a = applyWhitespace(av.getStringValue());
                    final String b = applyWhitespace(bv.getStringValue());
                    if (collator != null) {
                        return collator.compare(a, b);
                    }
                    return a.compareTo(b);
                }
            }

            if (Type.subTypeOfUnion(av.getType(), Type.NUMERIC) &&
                    Type.subTypeOfUnion(bv.getType(), Type.NUMERIC)) {
                if (((NumericValue) av).isNaN() && ((NumericValue) bv).isNaN()) {
                    return Constants.EQUAL;
                }
            }
            return ValueComparison.compareAtomic(collator, av, bv);
        } catch (final XPathException e) {
            return Constants.INFERIOR;
        }
    }

    private static boolean isStringLike(final AtomicValue v) {
        return Type.subTypeOf(v.getType(), Type.STRING) ||
                v.getType() == Type.UNTYPED_ATOMIC ||
                v.getType() == Type.ANY_URI;
    }

    private int compareElements(final org.w3c.dom.Node a, final org.w3c.dom.Node b) {
        int cmp = compareNames(a, b);
        if (cmp != Constants.EQUAL) {
            return cmp;
        }

        // Compare namespace prefixes if option is set
        if (namespacePrefixes) {
            cmp = safeCompare(a.getPrefix(), b.getPrefix(), null);
            if (cmp != Constants.EQUAL) {
                return cmp;
            }
        }

        cmp = compareAttributes(a, b);
        if (cmp != Constants.EQUAL) {
            return cmp;
        }

        if (unorderedElements) {
            return compareContentsUnordered(a, b);
        }

        return compareContents(a, b);
    }

    private int comparePIs(final NodeValue nva, final NodeValue nvb) throws XPathException {
        final int nameCmp = safeCompare(nva.getNode().getNodeName(), nvb.getNode().getNodeName(), null);
        if (nameCmp != Constants.EQUAL) {
            return nameCmp;
        }
        // Apply whitespace normalization to PI data content
        return safeCompare(
                maybeNormalizeWS(nva.getStringValue()),
                maybeNormalizeWS(nvb.getStringValue()),
                collator);
    }

    private int compareContents(final org.w3c.dom.Node a, final org.w3c.dom.Node b) {
        final List<org.w3c.dom.Node> childrenA = getSignificantChildren(a);
        final List<org.w3c.dom.Node> childrenB = getSignificantChildren(b);

        // Merge adjacent text nodes
        final List<Object> mergedA = mergeAdjacentTextNodes(childrenA);
        final List<Object> mergedB = mergeAdjacentTextNodes(childrenB);

        if (mergedA.size() != mergedB.size()) {
            return mergedA.size() < mergedB.size() ? Constants.INFERIOR : Constants.SUPERIOR;
        }

        for (int i = 0; i < mergedA.size(); i++) {
            final Object itemA = mergedA.get(i);
            final Object itemB = mergedB.get(i);

            if (itemA instanceof String sa && itemB instanceof String sb) {
                // Text may already be normalized/stripped by addMergedText; apply WS normalization if PRESERVE mode
                final String normA = whitespace == WhitespaceMode.PRESERVE ? sa : maybeNormalizeWS(sa);
                final String normB = whitespace == WhitespaceMode.PRESERVE ? sb : maybeNormalizeWS(sb);
                final int cmp = safeCompare(normA, normB, collator);
                if (cmp != Constants.EQUAL) {
                    return cmp;
                }
            } else if (itemA instanceof org.w3c.dom.Node na && itemB instanceof org.w3c.dom.Node nb) {
                final int typeA = getEffectiveNodeType(na);
                final int typeB = getEffectiveNodeType(nb);
                if (typeA != typeB) {
                    return Constants.INFERIOR;
                }
                final int cmp;
                switch (typeA) {
                    case org.w3c.dom.Node.ELEMENT_NODE:
                        cmp = compareElements(na, nb);
                        break;
                    case org.w3c.dom.Node.COMMENT_NODE:
                        cmp = safeCompare(maybeNormalizeWS(na.getNodeValue()),
                                maybeNormalizeWS(nb.getNodeValue()), collator);
                        break;
                    case org.w3c.dom.Node.PROCESSING_INSTRUCTION_NODE:
                        final int piNameCmp = safeCompare(na.getNodeName(), nb.getNodeName(), null);
                        cmp = piNameCmp != Constants.EQUAL ? piNameCmp :
                                safeCompare(maybeNormalizeWS(na.getNodeValue()),
                                        maybeNormalizeWS(nb.getNodeValue()), collator);
                        break;
                    default:
                        cmp = Constants.INFERIOR;
                }
                if (cmp != Constants.EQUAL) {
                    return cmp;
                }
            } else {
                // Mismatched types (text vs node)
                return Constants.INFERIOR;
            }
        }
        return Constants.EQUAL;
    }

    /**
     * Unordered element comparison: child elements are compared as multisets.
     */
    private int compareContentsUnordered(final org.w3c.dom.Node a, final org.w3c.dom.Node b) {
        final List<org.w3c.dom.Node> childrenA = getSignificantChildren(a);
        final List<org.w3c.dom.Node> childrenB = getSignificantChildren(b);

        // Separate text content and element/other nodes
        final StringBuilder textA = new StringBuilder();
        final List<org.w3c.dom.Node> elementsA = new ArrayList<>();
        for (final org.w3c.dom.Node n : childrenA) {
            final int type = getEffectiveNodeType(n);
            if (type == org.w3c.dom.Node.TEXT_NODE) {
                textA.append(getNodeValue(n));
            } else {
                elementsA.add(n);
            }
        }

        final StringBuilder textB = new StringBuilder();
        final List<org.w3c.dom.Node> elementsB = new ArrayList<>();
        for (final org.w3c.dom.Node n : childrenB) {
            final int type = getEffectiveNodeType(n);
            if (type == org.w3c.dom.Node.TEXT_NODE) {
                textB.append(getNodeValue(n));
            } else {
                elementsB.add(n);
            }
        }

        // Compare concatenated text content
        final int textCmp = safeCompare(
                maybeNormalizeWS(textA.toString()),
                maybeNormalizeWS(textB.toString()),
                collator);
        if (textCmp != Constants.EQUAL) {
            return textCmp;
        }

        // Compare elements as multisets
        if (elementsA.size() != elementsB.size()) {
            return elementsA.size() < elementsB.size() ? Constants.INFERIOR : Constants.SUPERIOR;
        }

        final boolean[] matched = new boolean[elementsB.size()];
        for (final org.w3c.dom.Node na : elementsA) {
            boolean found = false;
            for (int j = 0; j < elementsB.size(); j++) {
                if (!matched[j]) {
                    final int typeA = getEffectiveNodeType(na);
                    final int typeB = getEffectiveNodeType(elementsB.get(j));
                    if (typeA == typeB) {
                        int cmp;
                        if (typeA == org.w3c.dom.Node.ELEMENT_NODE) {
                            cmp = compareElements(na, elementsB.get(j));
                        } else if (typeA == org.w3c.dom.Node.COMMENT_NODE) {
                            cmp = safeCompare(na.getNodeValue(), elementsB.get(j).getNodeValue(), collator);
                        } else if (typeA == org.w3c.dom.Node.PROCESSING_INSTRUCTION_NODE) {
                            cmp = safeCompare(na.getNodeName(), elementsB.get(j).getNodeName(), null);
                            if (cmp == Constants.EQUAL) {
                                cmp = safeCompare(na.getNodeValue(), elementsB.get(j).getNodeValue(), collator);
                            }
                        } else {
                            cmp = Constants.INFERIOR;
                        }
                        if (cmp == Constants.EQUAL) {
                            matched[j] = true;
                            found = true;
                            break;
                        }
                    }
                }
            }
            if (!found) {
                return Constants.INFERIOR;
            }
        }
        return Constants.EQUAL;
    }

    /**
     * Get child nodes that are significant for deep-equal comparison,
     * based on the current options.
     */
    private List<org.w3c.dom.Node> getSignificantChildren(final org.w3c.dom.Node parent) {
        final List<org.w3c.dom.Node> result = new ArrayList<>();
        final boolean preserveWS = isXmlSpacePreserve(parent);
        org.w3c.dom.Node child = parent.getFirstChild();
        while (child != null) {
            final int type = getEffectiveNodeType(child);
            switch (type) {
                case org.w3c.dom.Node.ELEMENT_NODE:
                    result.add(child);
                    break;
                case org.w3c.dom.Node.TEXT_NODE:
                    if (whitespace == WhitespaceMode.STRIP) {
                        // Strip whitespace-only text nodes (deep-equal strip option
                        // overrides xml:space="preserve" per XQ4 spec)
                        final String value = getNodeValue(child);
                        if (value != null && !value.trim().isEmpty()) {
                            result.add(child);
                        }
                    } else {
                        result.add(child);
                    }
                    break;
                case org.w3c.dom.Node.COMMENT_NODE:
                    if (comments) {
                        result.add(child);
                    }
                    break;
                case org.w3c.dom.Node.PROCESSING_INSTRUCTION_NODE:
                    if (processingInstructions) {
                        result.add(child);
                    }
                    break;
            }
            child = child.getNextSibling();
        }
        return result;
    }

    /**
     * Merge adjacent text nodes into single String entries.
     * Non-text nodes are kept as-is. This handles the case where
     * comments/PIs split text differently in two trees.
     */
    private List<Object> mergeAdjacentTextNodes(final List<org.w3c.dom.Node> nodes) {
        final List<Object> result = new ArrayList<>();
        StringBuilder currentText = null;

        for (final org.w3c.dom.Node node : nodes) {
            final int type = getEffectiveNodeType(node);
            if (type == org.w3c.dom.Node.TEXT_NODE) {
                if (currentText == null) {
                    currentText = new StringBuilder();
                }
                currentText.append(getNodeValue(node));
            } else {
                if (currentText != null) {
                    addMergedText(result, currentText.toString());
                    currentText = null;
                }
                result.add(node);
            }
        }
        if (currentText != null) {
            addMergedText(result, currentText.toString());
        }
        return result;
    }

    /**
     * Add merged text to the result list, applying whitespace rules.
     * In STRIP mode, whitespace-only text is dropped.
     * In NORMALIZE mode, text that normalizes to empty is dropped.
     */
    private void addMergedText(final List<Object> result, final String text) {
        if (whitespace == WhitespaceMode.STRIP) {
            if (!text.trim().isEmpty()) {
                result.add(text);
            }
        } else if (whitespace == WhitespaceMode.NORMALIZE) {
            final String normalized = normalizeWhitespace(text);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        } else {
            result.add(text);
        }
    }

    private int compareAttributes(final org.w3c.dom.Node a, final org.w3c.dom.Node b) {
        final org.w3c.dom.NamedNodeMap nnma = a.getAttributes();
        final org.w3c.dom.NamedNodeMap nnmb = b.getAttributes();

        final int aCount = getAttrCount(nnma);
        final int bCount = getAttrCount(nnmb);

        if (aCount != bCount) {
            return aCount < bCount ? Constants.INFERIOR : Constants.SUPERIOR;
        }

        for (int i = 0; i < nnma.getLength(); i++) {
            final org.w3c.dom.Node ta = nnma.item(i);
            final String nsA = ta.getNamespaceURI();
            if (nsA != null && Namespaces.XMLNS_NS.equals(nsA)) {
                continue;
            }
            final org.w3c.dom.Node tb = ta.getLocalName() == null ?
                    nnmb.getNamedItem(ta.getNodeName()) :
                    nnmb.getNamedItemNS(ta.getNamespaceURI(), ta.getLocalName());
            if (tb == null) {
                return Constants.SUPERIOR;
            }
            final int cmp = safeCompare(
                    maybeNormalizeWSAttr(ta.getNodeValue()),
                    maybeNormalizeWSAttr(tb.getNodeValue()),
                    collator);
            if (cmp != Constants.EQUAL) {
                return cmp;
            }
        }
        return Constants.EQUAL;
    }

    // ========================================================================
    // Utility methods
    // ========================================================================

    private String maybeNormalizeWS(@Nullable final String s) {
        if (s == null || whitespace == WhitespaceMode.PRESERVE) {
            return s;
        }
        // Both NORMALIZE and STRIP normalize text content
        return normalizeWhitespace(s);
    }

    /**
     * Normalize whitespace for attribute values: only NORMALIZE mode applies,
     * STRIP mode does NOT affect attribute values.
     */
    private String maybeNormalizeWSAttr(@Nullable final String s) {
        if (s == null || whitespace != WhitespaceMode.NORMALIZE) {
            return s;
        }
        return normalizeWhitespace(s);
    }

    private static String normalizeWhitespace(final String s) {
        return s.strip().replaceAll("\\s+", " ");
    }

    private String applyWhitespace(final String s) {
        if (whitespace == WhitespaceMode.NORMALIZE) {
            return normalizeWhitespace(s);
        }
        if (whitespace == WhitespaceMode.STRIP) {
            return normalizeWhitespace(s);
        }
        return s;
    }

    private static int getAttrCount(final org.w3c.dom.NamedNodeMap nnm) {
        int count = 0;
        for (int i = 0; i < nnm.getLength(); i++) {
            final org.w3c.dom.Node n = nnm.item(i);
            final String ns = n.getNamespaceURI();
            if (ns == null || !Namespaces.XMLNS_NS.equals(ns)) {
                ++count;
            }
        }
        return count;
    }

    private static int compareNames(final org.w3c.dom.Node a, final org.w3c.dom.Node b) {
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
        }
        return a.compareTo(b);
    }

    private static String getNodeValue(final org.w3c.dom.Node n) {
        if (n.getNodeType() == NodeImpl.REFERENCE_NODE) {
            return ((ReferenceNode) n).getReference().getNodeValue();
        }
        return n.getNodeValue();
    }

    private static int getEffectiveNodeType(final org.w3c.dom.Node n) {
        int nodeType = n.getNodeType();
        if (nodeType == NodeImpl.REFERENCE_NODE) {
            nodeType = ((ReferenceNode) n).getReference().getNode().getNodeType();
        }
        return nodeType;
    }

    /**
     * Check if the given node or any ancestor has xml:space="preserve".
     * Uses NamedNodeMap lookup for broader DOM compatibility.
     */
    private static boolean isXmlSpacePreserve(final org.w3c.dom.Node node) {
        org.w3c.dom.Node current = node;
        while (current != null && current.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            if (current instanceof org.w3c.dom.Element elem) {
                // Use Element.getAttributeNS for persistent DOM and other implementations
                final String xmlSpace = elem.getAttributeNS(
                        "http://www.w3.org/XML/1998/namespace", "space");
                if ("preserve".equals(xmlSpace)) {
                    return true;
                }
                if ("default".equals(xmlSpace)) {
                    return false;
                }
            }
            // Also check via NamedNodeMap for broader compatibility
            final org.w3c.dom.NamedNodeMap attrs = current.getAttributes();
            if (attrs != null) {
                org.w3c.dom.Node xmlSpace = attrs.getNamedItemNS(
                        "http://www.w3.org/XML/1998/namespace", "space");
                if (xmlSpace == null) {
                    xmlSpace = attrs.getNamedItem("xml:space");
                }
                if (xmlSpace != null) {
                    final String val = xmlSpace.getNodeValue();
                    if ("preserve".equals(val)) {
                        return true;
                    }
                    if ("default".equals(val)) {
                        return false;
                    }
                }
            }
            current = current.getParentNode();
        }
        return false;
    }

    /**
     * Deep equality using these options.
     */
    public boolean deepEqualsSeq(final Sequence sequence1, final Sequence sequence2) {
        return deepCompareSeq(sequence1, sequence2) == Constants.EQUAL;
    }
}
