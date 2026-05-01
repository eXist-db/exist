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

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.w3c.dom.*;

import javax.xml.XMLConstants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implements XQuery 4.0 fn:element-to-map.
 *
 * Converts an element node to a map representation following the XQ4 spec rules
 * for different content models (empty, simple, record, list, sequence, mixed).
 */
public class FnElementToMap extends BasicFunction {

    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";
    private static final String XSI_NS = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;

    private static FunctionReturnSequenceType makeReturnType() {
        // XQ4 spec: map(xs:string, item()?)?
        final FunctionReturnSequenceType rt = new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_ONE,
                "The map representation");
        rt.setFunctionParamTypes(new SequenceType[] {
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE)
        });
        return rt;
    }

    public static final FunctionSignature[] FN_ELEMENT_TO_MAP = {
            new FunctionSignature(
                    new QName("element-to-map", Function.BUILTIN_FUNCTION_NS),
                    "Converts an element to a map representation.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("element", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "The element to convert")
                    },
                    makeReturnType()),
            new FunctionSignature(
                    new QName("element-to-map", Function.BUILTIN_FUNCTION_NS),
                    "Converts an element to a map representation with options.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("element", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "The element to convert"),
                            new FunctionParameterSequenceType("options", Type.MAP_ITEM, Cardinality.ZERO_OR_ONE, "Options map")
                    },
                    makeReturnType())
    };

    private static final String DEFAULT_ATTR_MARKER = "@";
    private static final String DEFAULT_CONTENT_KEY = "#content";
    private static final String DEFAULT_COMMENT_KEY = "#comment";
    private static final String DEFAULT_NAME_FORMAT = "default";

    public FnElementToMap(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Node node = ((NodeValue) args[0].itemAt(0)).getNode();
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            throw new XPathException(this, ErrorCodes.XPTY0004, "Expected element node");
        }

        // Parse options
        String nameFormat = DEFAULT_NAME_FORMAT;
        String attrMarker = DEFAULT_ATTR_MARKER;
        String contentKey = DEFAULT_CONTENT_KEY;
        String commentKey = DEFAULT_COMMENT_KEY;
        MapType plan = null;

        if (args.length > 1 && !args[1].isEmpty()) {
            final MapType options = (MapType) args[1].itemAt(0);

            // name-format
            final Sequence nfSeq = options.get(new StringValue(this, "name-format"));
            if (nfSeq != null && !nfSeq.isEmpty()) {
                if (nfSeq.itemAt(0).getType() != Type.STRING &&
                        nfSeq.itemAt(0).getType() != Type.UNTYPED_ATOMIC) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'name-format' must be a string");
                }
                nameFormat = nfSeq.getStringValue();
                if (!"default".equals(nameFormat) && !"eqname".equals(nameFormat) &&
                        !"lexical".equals(nameFormat) && !"local".equals(nameFormat)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Invalid name-format: '" + nameFormat + "'");
                }
            }

            // attribute-marker
            final Sequence amSeq = options.get(new StringValue(this, "attribute-marker"));
            if (amSeq != null && !amSeq.isEmpty()) {
                if (amSeq.itemAt(0).getType() != Type.STRING &&
                        amSeq.itemAt(0).getType() != Type.UNTYPED_ATOMIC) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'attribute-marker' must be a string");
                }
                attrMarker = amSeq.getStringValue();
            }

            // content-key
            final Sequence ckSeq = options.get(new StringValue(this, "content-key"));
            if (ckSeq != null && !ckSeq.isEmpty()) {
                contentKey = ckSeq.getStringValue();
            }

            // comment-key
            final Sequence cmSeq = options.get(new StringValue(this, "comment-key"));
            if (cmSeq != null && !cmSeq.isEmpty()) {
                commentKey = cmSeq.getStringValue();
            }

            // plan
            final Sequence planSeq = options.get(new StringValue(this, "plan"));
            if (planSeq != null && !planSeq.isEmpty()) {
                if (planSeq.itemAt(0) instanceof MapType mapType) {
                    plan = mapType;
                } else {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'plan' must be a map");
                }
            }
        }

        final Options opts = new Options(nameFormat, attrMarker, contentKey, commentKey, plan);
        final Sequence result = convertElement((Element) node, null, opts);
        if (result == Sequence.EMPTY_SEQUENCE) {
            return Sequence.EMPTY_SEQUENCE;
        }
        return result;
    }

    private Sequence convertElement(final Element elem, final Element parent, final Options opts) throws XPathException {
        final String elemName = formatElementName(elem, parent, opts);
        final Sequence value = convertContent(elem, opts);

        // Propagate deep-skip
        if (value == Sequence.EMPTY_SEQUENCE && opts.plan != null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        MapType result = new MapType(this, context);
        result = (MapType) result.put(new StringValue(this, elemName), value);
        return result;
    }

    private Sequence convertContent(final Element elem, final Options opts) throws XPathException {
        // Check if plan specifies layout for this element
        if (opts.plan != null) {
            return convertWithPlan(elem, opts);
        }
        return convertAutoLayout(elem, opts);
    }

    private Sequence convertAutoLayout(final Element elem, final Options opts) throws XPathException {
        // Collect attributes
        final Map<String, Sequence> attrs = collectAttributes(elem, opts);

        // Check for xsi:nil
        final Sequence nilResult = checkNil(elem, attrs, opts);
        if (nilResult != null) {
            return nilResult;
        }

        // Collect child nodes
        final List<Node> children = collectChildNodes(elem);

        // Classify content
        final boolean hasElements = children.stream().anyMatch(n -> n.getNodeType() == Node.ELEMENT_NODE);
        final boolean hasAnyText = children.stream().anyMatch(n ->
                n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE);
        final boolean hasSignificantText = children.stream().anyMatch(n ->
                (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE)
                        && !n.getTextContent().trim().isEmpty());
        final boolean hasComments = children.stream().anyMatch(n -> n.getNodeType() == Node.COMMENT_NODE);

        // Empty element — only truly empty (no text nodes at all, or only whitespace WITH elements)
        if (children.isEmpty() || (!hasElements && !hasAnyText && !hasComments)) {
            if (attrs.isEmpty()) {
                return new StringValue(this, "");
            } else {
                return buildAttrOnlyMap(attrs);
            }
        }

        // Simple text content (no child elements, no comments)
        if (!hasElements && !hasComments) {
            final String textContent = getTextContent(children);
            final Sequence typedValue = coerceByXsiType(elem, textContent);
            if (attrs.isEmpty()) {
                return typedValue;
            } else {
                return buildContentMap(attrs, typedValue, opts);
            }
        }

        // Mixed content (has both significant text and element children)
        if (hasSignificantText && hasElements) {
            return buildMixedContent(children, attrs, elem, opts);
        }

        // Comments with significant text but no elements
        if (hasComments && !hasElements && hasSignificantText) {
            return buildMixedContent(children, attrs, elem, opts);
        }

        // Element-only (or element + whitespace-only text + optional comments)
        final List<Element> childElements = new ArrayList<>();
        for (final Node child : children) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                childElements.add((Element) child);
            }
        }

        // Comments interleaved with elements → sequence layout
        if (hasComments && hasElements) {
            return buildSequenceContent(children, attrs, elem, opts);
        }

        // Check if all children have the same name (list pattern)
        final boolean allSameName = childElements.size() > 1 &&
                childElements.stream().allMatch(e ->
                        formatElementName(e, elem, opts).equals(formatElementName(childElements.get(0), elem, opts)));

        if (allSameName) {
            return buildListContent(childElements, attrs, elem, opts);
        }

        // Check if all children have unique names (record pattern)
        final Map<String, List<Element>> groupedByName = new LinkedHashMap<>();
        for (final Element child : childElements) {
            groupedByName.computeIfAbsent(formatElementName(child, elem, opts), k -> new ArrayList<>()).add(child);
        }
        final boolean allUnique = groupedByName.values().stream().allMatch(l -> l.size() == 1);

        if (allUnique) {
            return buildRecordContent(childElements, attrs, elem, opts);
        }

        // Non-unique names → sequence layout
        return buildSequenceContent(children, attrs, elem, opts);
    }

    private Sequence convertWithPlan(final Element elem, final Options opts) throws XPathException {
        final Map<String, Sequence> attrs = collectAttributes(elem, opts);

        // Check for xsi:nil
        final Sequence nilResult = checkNil(elem, attrs, opts);
        if (nilResult != null) {
            return nilResult;
        }

        // Look up layout in plan for this element's local name
        final String elemKey = getPlanKey(elem);
        MapType planEntry = lookupPlan(elemKey, opts);

        if (planEntry == null) {
            // Try fallback "*"
            planEntry = lookupPlan("*", opts);
        }

        if (planEntry == null) {
            // No plan entry — fall back to auto layout
            return convertAutoLayout(elem, opts);
        }

        // Get layout from plan entry
        final Sequence layoutSeq = planEntry.get(new StringValue(this, "layout"));
        if (layoutSeq == null || layoutSeq.isEmpty()) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Plan entry for '" + elemKey + "' missing required 'layout' field");
        }
        final Item layoutItem = layoutSeq.itemAt(0);
        if (!(layoutItem instanceof StringValue) && layoutItem.getType() != Type.UNTYPED_ATOMIC) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Plan entry 'layout' must be a string, got " + Type.getTypeName(layoutItem.getType()));
        }
        final String layout = layoutSeq.getStringValue();

        // Get type for simple content
        String valueType = null;
        final Sequence typeSeq = planEntry.get(new StringValue(this, "type"));
        if (typeSeq != null && !typeSeq.isEmpty()) {
            valueType = typeSeq.getStringValue();
        }

        // Get child name for list layout
        String childName = null;
        final Sequence childSeq = planEntry.get(new StringValue(this, "child"));
        if (childSeq != null && !childSeq.isEmpty()) {
            childName = childSeq.getStringValue();
        }

        final List<Node> children = collectChildNodes(elem);
        final List<Element> childElements = new ArrayList<>();
        for (final Node child : children) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                childElements.add((Element) child);
            }
        }

        return switch (layout) {
            case "empty" -> {
                if (!childElements.isEmpty()) {
                    yield handlePlanFallback(elem, attrs, children, childElements, opts);
                }
                yield new StringValue(this, "");
            }
            case "empty-plus" -> {
                if (!childElements.isEmpty()) {
                    throw new XPathException(this, ErrorCodes.FOJS0008,
                            "Element has child elements but plan specifies 'empty-plus' layout");
                }
                yield attrs.isEmpty() ? new MapType(this, context) : buildAttrOnlyMap(attrs);
            }
            case "simple" -> {
                if (!childElements.isEmpty()) {
                    yield handleSimpleFallback(elem, attrs, children, childElements, opts);
                }
                final String simpleText = getTextContent(children);
                yield coerceValue(simpleText, valueType);
            }
            case "simple-plus" -> {
                if (!childElements.isEmpty()) {
                    yield handleSimpleFallback(elem, attrs, children, childElements, opts);
                }
                final String spText = getTextContent(children);
                final Sequence spValue = coerceValue(spText, valueType);
                yield buildContentMap(attrs, spValue, opts);
            }
            case "list" -> {
                if (childName == null) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Plan entry 'list' requires 'child' field");
                }
                final boolean hasSignificantText = children.stream().anyMatch(n ->
                        (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE)
                                && !n.getTextContent().trim().isEmpty());
                if (hasSignificantText) {
                    throw new XPathException(this, ErrorCodes.FOJS0008,
                            "Element has text content but plan specifies 'list' layout");
                }
                if (!childElements.isEmpty()) {
                    final String expectedChildKey = childName;
                    final boolean allMatch = childElements.stream().allMatch(e ->
                            getPlanKey(e).equals(expectedChildKey));
                    if (!allMatch) {
                        throw new XPathException(this, ErrorCodes.FOJS0008,
                                "Not all child elements match the expected child name '" + childName + "'");
                    }
                }
                yield buildListArray(childElements, opts);
            }
            case "list-plus" -> {
                final String listPlusChildName = childName != null ? childName :
                        (!childElements.isEmpty() ? getPlanKey(childElements.get(0)) : "item");
                yield buildListPlusContent(childElements, attrs, listPlusChildName, opts);
            }
            case "record" -> buildRecordContent(childElements, attrs, elem, opts);
            case "sequence" -> buildSequenceContent(children, attrs, elem, opts);
            case "mixed" -> buildMixedContent(children, attrs, elem, opts);
            case "xml" -> new StringValue(this, serializeToXml(elem));
            case "error" -> throw new XPathException(this, ErrorCodes.FOJS0008,
                    "Plan specifies 'error' layout for element '" + elemKey + "'");
            case "deep-skip" -> Sequence.EMPTY_SEQUENCE;
            default -> throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Invalid layout in plan: '" + layout + "'");
        };
    }

    private Sequence handlePlanFallback(final Element elem, final Map<String, Sequence> attrs,
                                         final List<Node> children, final List<Element> childElements,
                                         final Options opts) throws XPathException {
        // Check for fallback layout in plan
        final MapType fallback = lookupPlan("*", opts);
        if (fallback != null) {
            final Sequence fbLayoutSeq = fallback.get(new StringValue(this, "layout"));
            if (fbLayoutSeq != null && !fbLayoutSeq.isEmpty()) {
                final String fbLayout = fbLayoutSeq.getStringValue();
                switch (fbLayout) {
                    case "record" -> { return buildRecordContent(childElements, attrs, elem, opts); }
                    case "sequence" -> { return buildSequenceContent(children, attrs, elem, opts); }
                    case "mixed" -> { return buildMixedContent(children, attrs, elem, opts); }
                    case "xml" -> { return new StringValue(this, serializeToXml(elem)); }
                    default -> { /* fall through to throw */ }
                }
            }
        }
        throw new XPathException(this, ErrorCodes.FOJS0008,
                "Element has child elements but plan specifies layout that doesn't support them");
    }

    private Sequence handleSimpleFallback(final Element elem, final Map<String, Sequence> attrs,
                                           final List<Node> children, final List<Element> childElements,
                                           final Options opts) throws XPathException {
        // Check for fallback layout in plan
        final MapType fallback = lookupPlan("*", opts);
        if (fallback != null) {
            final Sequence fbLayoutSeq = fallback.get(new StringValue(this, "layout"));
            if (fbLayoutSeq != null && !fbLayoutSeq.isEmpty()) {
                final String fbLayout = fbLayoutSeq.getStringValue();
                switch (fbLayout) {
                    case "mixed" -> { return buildMixedContent(children, attrs, elem, opts); }
                    case "xml" -> { return new StringValue(this, serializeToXml(elem)); }
                    case "deep-skip" -> { return Sequence.EMPTY_SEQUENCE; }
                    case "record" -> { return buildRecordContent(childElements, attrs, elem, opts); }
                    case "sequence" -> { return buildSequenceContent(children, attrs, elem, opts); }
                    default -> { /* fall through to throw */ }
                }
            }
        }
        throw new XPathException(this, ErrorCodes.FOJS0008,
                "Element has child elements but plan specifies 'simple' layout");
    }

    private MapType lookupPlan(final String key, final Options opts) throws XPathException {
        if (opts.plan == null) {
            return null;
        }
        final Sequence entry = opts.plan.get(new StringValue(this, key));
        if (entry == null || entry.isEmpty()) {
            return null;
        }
        if (entry.itemAt(0) instanceof MapType mapType) {
            return mapType;
        }
        throw new XPathException(this, ErrorCodes.XPTY0004,
                "Plan entry for '" + key + "' must be a map");
    }

    private String getPlanKey(final Node elem) {
        final String ns = elem.getNamespaceURI();
        final String local = elem.getLocalName() != null ? elem.getLocalName() : elem.getNodeName();
        if (ns != null && !ns.isEmpty()) {
            return "Q{" + ns + "}" + local;
        }
        return local;
    }

    private Map<String, Sequence> collectAttributes(final Element elem, final Options opts) throws XPathException {
        final Map<String, Sequence> attrs = new LinkedHashMap<>();
        final NamedNodeMap attrNodes = elem.getAttributes();
        if (attrNodes != null) {
            for (int i = 0; i < attrNodes.getLength(); i++) {
                final Attr attr = (Attr) attrNodes.item(i);
                final String attrName = attr.getName();
                // Skip namespace declarations
                if (attrName.startsWith("xmlns") && (attrName.length() == 5 || attrName.charAt(5) == ':')) {
                    continue;
                }
                // Skip xsi:type and xsi:nil
                final String attrNs = attr.getNamespaceURI();
                if (XSI_NS.equals(attrNs)) {
                    continue;
                }
                final String key = opts.attrMarker + formatAttrName(attr, opts);

                // Apply type coercion from plan if available
                Sequence value = new StringValue(this, attr.getValue());
                if (opts.plan != null) {
                    final String attrPlanKey = "@" + getAttrPlanKey(attr);
                    final MapType attrPlan = lookupPlan(attrPlanKey, opts);
                    if (attrPlan != null) {
                        final Sequence typeSeq = attrPlan.get(new StringValue(this, "type"));
                        if (typeSeq != null && !typeSeq.isEmpty()) {
                            final String type = typeSeq.getStringValue();
                            if ("skip".equals(type)) {
                                continue;
                            }
                            value = coerceValue(attr.getValue(), type);
                        }
                    }
                }
                attrs.put(key, value);
            }
        }
        return attrs;
    }

    private String getAttrPlanKey(final Attr attr) {
        final String ns = attr.getNamespaceURI();
        final String local = attr.getLocalName() != null ? attr.getLocalName() : attr.getName();
        if (ns != null && !ns.isEmpty()) {
            return "Q{" + ns + "}" + local;
        }
        return local;
    }

    private Sequence checkNil(final Element elem, final Map<String, Sequence> attrs, final Options opts) throws XPathException {
        final String nilAttr = elem.getAttributeNS(XSI_NS, "nil");
        if ("true".equals(nilAttr) || "1".equals(nilAttr)) {
            final Sequence nullValue = new QNameValue(this, context,
                    new QName("null", Function.BUILTIN_FUNCTION_NS, "fn"));
            if (attrs.isEmpty()) {
                return nullValue;
            } else {
                MapType attrMap = new MapType(this, context);
                for (final Map.Entry<String, Sequence> a : attrs.entrySet()) {
                    attrMap = (MapType) attrMap.put(new StringValue(this, a.getKey()), a.getValue());
                }
                attrMap = (MapType) attrMap.put(new StringValue(this, opts.contentKey), nullValue);
                return attrMap;
            }
        }
        return null;
    }

    private List<Node> collectChildNodes(final Element elem) {
        final List<Node> children = new ArrayList<>();
        final NodeList childNodes = elem.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            final Node child = childNodes.item(i);
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE, Node.TEXT_NODE, Node.CDATA_SECTION_NODE, Node.COMMENT_NODE -> children.add(child);
                default -> { /* ignore */ }
            }
        }
        return children;
    }

    private MapType buildAttrOnlyMap(final Map<String, Sequence> attrs) throws XPathException {
        MapType attrMap = new MapType(this, context);
        for (final Map.Entry<String, Sequence> a : attrs.entrySet()) {
            attrMap = (MapType) attrMap.put(new StringValue(this, a.getKey()), a.getValue());
        }
        return attrMap;
    }

    private Sequence buildContentMap(final Map<String, Sequence> attrs, final Sequence contentValue, final Options opts) throws XPathException {
        MapType map = new MapType(this, context);
        for (final Map.Entry<String, Sequence> a : attrs.entrySet()) {
            map = (MapType) map.put(new StringValue(this, a.getKey()), a.getValue());
        }
        map = (MapType) map.put(new StringValue(this, opts.contentKey), contentValue);
        return map;
    }

    private Sequence buildListContent(final List<Element> children, final Map<String, Sequence> attrs,
                                       final Element parent, final Options opts) throws XPathException {
        final List<Sequence> items = new ArrayList<>();
        for (final Element child : children) {
            items.add(convertContentForChild(child, opts));
        }
        final ArrayType array = new ArrayType(this, context, items);

        if (attrs.isEmpty()) {
            return array;
        } else {
            // list-plus: attrs + childName → array
            MapType map = new MapType(this, context);
            for (final Map.Entry<String, Sequence> a : attrs.entrySet()) {
                map = (MapType) map.put(new StringValue(this, a.getKey()), a.getValue());
            }
            final String childName = formatElementName(children.get(0), parent, opts);
            map = (MapType) map.put(new StringValue(this, childName), array);
            return map;
        }
    }

    private Sequence buildListArray(final List<Element> children, final Options opts) throws XPathException {
        final List<Sequence> items = new ArrayList<>();
        for (final Element child : children) {
            items.add(convertContentForChild(child, opts));
        }
        return new ArrayType(this, context, items);
    }

    private Sequence buildListPlusContent(final List<Element> children, final Map<String, Sequence> attrs,
                                            final String childName, final Options opts) throws XPathException {
        final List<Sequence> items = new ArrayList<>();
        for (final Element child : children) {
            items.add(convertContentForChild(child, opts));
        }
        final ArrayType array = new ArrayType(this, context, items);

        // list-plus always produces a map with child name key + attrs
        MapType map = new MapType(this, context);
        for (final Map.Entry<String, Sequence> a : attrs.entrySet()) {
            map = (MapType) map.put(new StringValue(this, a.getKey()), a.getValue());
        }
        map = (MapType) map.put(new StringValue(this, childName), array);
        return map;
    }

    private Sequence convertContentForChild(final Element child, final Options opts) throws XPathException {
        if (opts.plan != null) {
            return convertWithPlan(child, opts);
        }
        return convertAutoLayout(child, opts);
    }

    private Sequence buildRecordContent(final List<Element> childElements, final Map<String, Sequence> attrs,
                                         final Element parent, final Options opts) throws XPathException {
        MapType recordMap = new MapType(this, context);

        // Add attributes first
        for (final Map.Entry<String, Sequence> a : attrs.entrySet()) {
            recordMap = (MapType) recordMap.put(new StringValue(this, a.getKey()), a.getValue());
        }

        // Add child elements — group duplicates into arrays
        final Map<String, List<Sequence>> childGroups = new LinkedHashMap<>();
        for (final Element child : childElements) {
            // Check for deep-skip in plan
            if (opts.plan != null) {
                final String childKey = getPlanKey(child);
                MapType childPlan = lookupPlan(childKey, opts);
                if (childPlan != null) {
                    final Sequence layoutSeq = childPlan.get(new StringValue(this, "layout"));
                    if (layoutSeq != null && !layoutSeq.isEmpty() && "deep-skip".equals(layoutSeq.getStringValue())) {
                        continue;
                    }
                }
            }

            final String childName = formatElementName(child, parent, opts);
            final Sequence childValue = convertContentForChild(child, opts);
            if (childValue != Sequence.EMPTY_SEQUENCE || opts.plan == null) {
                childGroups.computeIfAbsent(childName, k -> new ArrayList<>()).add(childValue);
            }
        }

        for (final Map.Entry<String, List<Sequence>> entry : childGroups.entrySet()) {
            if (entry.getValue().size() == 1) {
                recordMap = (MapType) recordMap.put(new StringValue(this, entry.getKey()), entry.getValue().get(0));
            } else {
                final ArrayType array = new ArrayType(this, context, entry.getValue());
                recordMap = (MapType) recordMap.put(new StringValue(this, entry.getKey()), array);
            }
        }

        return recordMap;
    }

    private Sequence buildSequenceContent(final List<Node> children, final Map<String, Sequence> attrs,
                                           final Element parent, final Options opts) throws XPathException {
        final List<Sequence> items = new ArrayList<>();

        // With attrs, prepend an attr-only map as first element
        if (!attrs.isEmpty()) {
            items.add(buildAttrOnlyMap(attrs));
        }

        for (final Node child : children) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                // Check for deep-skip
                if (opts.plan != null) {
                    final String childKey = getPlanKey(child);
                    MapType childPlan = lookupPlan(childKey, opts);
                    if (childPlan != null) {
                        final Sequence layoutSeq = childPlan.get(new StringValue(this, "layout"));
                        if (layoutSeq != null && !layoutSeq.isEmpty() && "deep-skip".equals(layoutSeq.getStringValue())) {
                            continue;
                        }
                    }
                }
                items.add(convertElement((Element) child, parent, opts));
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                final String text = child.getTextContent();
                // In sequence layout, skip whitespace-only text nodes
                if (!text.trim().isEmpty()) {
                    items.add(new StringValue(this, text));
                }
            } else if (child.getNodeType() == Node.COMMENT_NODE) {
                MapType commentMap = new MapType(this, context);
                commentMap = (MapType) commentMap.put(
                        new StringValue(this, opts.commentKey),
                        new StringValue(this, child.getTextContent()));
                items.add(commentMap);
            }
        }
        return new ArrayType(this, context, items);
    }

    private Sequence buildMixedContent(final List<Node> children, final Map<String, Sequence> attrs,
                                        final Element parent, final Options opts) throws XPathException {
        final List<Sequence> items = new ArrayList<>();

        // With attrs, prepend an attr-only map as first element
        if (!attrs.isEmpty()) {
            items.add(buildAttrOnlyMap(attrs));
        }

        for (final Node child : children) {
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE -> items.add(convertElement((Element) child, parent, opts));
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    final String text = child.getTextContent();
                    if (!text.isEmpty()) {
                        items.add(new StringValue(this, text));
                    }
                }
                case Node.COMMENT_NODE -> {
                    MapType commentMap = new MapType(this, context);
                    commentMap = (MapType) commentMap.put(
                            new StringValue(this, opts.commentKey),
                            new StringValue(this, child.getTextContent()));
                    items.add(commentMap);
                }
                default -> { /* ignore */ }
            }
        }
        return new ArrayType(this, context, items);
    }

    private Sequence coerceByXsiType(final Element elem, final String text) throws XPathException {
        final String xsiType = elem.getAttributeNS(XSI_NS, "type");
        if (xsiType == null || xsiType.isEmpty()) {
            return new StringValue(this, text);
        }
        // Strip prefix if present (e.g. "xs:integer" → "integer")
        final String localType = xsiType.contains(":") ? xsiType.substring(xsiType.indexOf(':') + 1) : xsiType;
        return switch (localType) {
            case "integer", "int", "long", "short", "byte", "nonNegativeInteger", "positiveInteger",
                 "nonPositiveInteger", "negativeInteger", "unsignedLong", "unsignedInt",
                 "unsignedShort", "unsignedByte" -> {
                try {
                    yield new IntegerValue(this, Long.parseLong(text.trim()));
                } catch (final NumberFormatException e) {
                    yield new StringValue(this, text);
                }
            }
            case "decimal" -> {
                try {
                    yield new DecimalValue(this, text.trim());
                } catch (final XPathException e) {
                    yield new StringValue(this, text);
                }
            }
            case "double", "float" -> {
                try {
                    yield new DoubleValue(this, Double.parseDouble(text.trim()));
                } catch (final NumberFormatException e) {
                    yield new StringValue(this, text);
                }
            }
            case "boolean" -> {
                final String trimmed = text.trim();
                if ("true".equals(trimmed) || "1".equals(trimmed)) {
                    yield BooleanValue.TRUE;
                } else if ("false".equals(trimmed) || "0".equals(trimmed)) {
                    yield BooleanValue.FALSE;
                }
                yield new StringValue(this, text);
            }
            default -> new StringValue(this, text);
        };
    }

    private Sequence coerceValue(final String text, final String type) throws XPathException {
        if (type == null || "string".equals(type)) {
            return new StringValue(this, text);
        }
        return switch (type) {
            case "numeric" -> {
                try {
                    if (text.contains(".") || text.contains("e") || text.contains("E") ||
                            "NaN".equals(text) || "INF".equals(text) || "-INF".equals(text)) {
                        if (text.contains("e") || text.contains("E") ||
                                "NaN".equals(text) || "INF".equals(text) || "-INF".equals(text)) {
                            yield new DoubleValue(this, Double.parseDouble(text));
                        }
                        yield new DecimalValue(this, text);
                    }
                    yield new IntegerValue(this, Long.parseLong(text));
                } catch (final NumberFormatException e) {
                    yield new StringValue(this, text);
                }
            }
            case "boolean" -> {
                if ("true".equals(text) || "1".equals(text)) {
                    yield BooleanValue.TRUE;
                } else if ("false".equals(text) || "0".equals(text)) {
                    yield BooleanValue.FALSE;
                }
                yield new StringValue(this, text);
            }
            default -> new StringValue(this, text);
        };
    }

    private String formatElementName(final Element elem, final Element parent, final Options opts) {
        final String ns = elem.getNamespaceURI();
        final String local = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();

        return switch (opts.nameFormat) {
            case "eqname" -> (ns != null && !ns.isEmpty()) ? "Q{" + ns + "}" + local : local;
            case "lexical" -> {
                final String prefix = elem.getPrefix();
                yield (prefix != null && !prefix.isEmpty()) ? prefix + ":" + local : local;
            }
            case "local" -> local;
            default -> formatDefaultName(ns, local, parent);
        };
    }

    private String formatDefaultName(final String ns, final String local, final Element parent) {
        if (parent == null) {
            // Top-level element
            if (ns == null || ns.isEmpty()) {
                return local;
            }
            return "Q{" + ns + "}" + local;
        }

        final String parentNs = parent.getNamespaceURI();

        // Same namespace as parent → local name only
        if (Objects.equals(ns, parentNs)) {
            return local;
        }
        if (ns != null && ns.equals(parentNs)) {
            return local;
        }
        if ((ns == null || ns.isEmpty()) && (parentNs == null || parentNs.isEmpty())) {
            return local;
        }

        // Different namespace from parent
        if (ns == null || ns.isEmpty()) {
            // No namespace but parent has one → Q{}local
            return "Q{}" + local;
        }
        return "Q{" + ns + "}" + local;
    }

    private String formatAttrName(final Attr attr, final Options opts) {
        final String ns = attr.getNamespaceURI();
        final String local = attr.getLocalName() != null ? attr.getLocalName() : attr.getName();

        return switch (opts.nameFormat) {
            case "eqname" -> (ns != null && !ns.isEmpty()) ? "Q{" + ns + "}" + local : local;
            case "lexical" -> {
                final String prefix = attr.getPrefix();
                yield (prefix != null && !prefix.isEmpty()) ? prefix + ":" + local : local;
            }
            case "local" -> local;
            default -> {
                if (ns == null || ns.isEmpty()) {
                    yield local;
                }
                if (XML_NS.equals(ns)) {
                    yield "xml:" + local;
                }
                yield "Q{" + ns + "}" + local;
            }
        };
    }

    private static String getTextContent(final List<Node> children) {
        final StringBuilder sb = new StringBuilder();
        for (final Node child : children) {
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getTextContent());
            }
        }
        return sb.toString();
    }

    private String serializeToXml(final Element elem) {
        final StringBuilder sb = new StringBuilder();
        serializeElement(elem, sb);
        return sb.toString();
    }

    private void serializeElement(final Element elem, final StringBuilder sb) {
        final String tagName = elem.getTagName();
        sb.append('<').append(tagName);

        // Attributes
        final NamedNodeMap attrs = elem.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                final Attr attr = (Attr) attrs.item(i);
                sb.append(' ').append(attr.getName()).append("=\"");
                sb.append(escapeXmlAttr(attr.getValue()));
                sb.append('"');
            }
        }

        // Children
        final NodeList children = elem.getChildNodes();
        if (children.getLength() == 0) {
            sb.append("/>");
        } else {
            sb.append('>');
            for (int i = 0; i < children.getLength(); i++) {
                final Node child = children.item(i);
                switch (child.getNodeType()) {
                    case Node.ELEMENT_NODE -> serializeElement((Element) child, sb);
                    case Node.TEXT_NODE, Node.CDATA_SECTION_NODE ->
                            sb.append(escapeXmlText(child.getTextContent()));
                    case Node.COMMENT_NODE ->
                            sb.append("<!--").append(child.getTextContent()).append("-->");
                    default -> { /* ignore */ }
                }
            }
            sb.append("</").append(tagName).append('>');
        }
    }

    private static String escapeXmlText(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeXmlAttr(final String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static class Options {
        final String nameFormat;
        final String attrMarker;
        final String contentKey;
        final String commentKey;
        final MapType plan;

        Options(final String nameFormat, final String attrMarker, final String contentKey,
                final String commentKey, final MapType plan) {
            this.nameFormat = nameFormat;
            this.attrMarker = attrMarker;
            this.contentKey = contentKey;
            this.commentKey = commentKey;
            this.plan = plan;
        }
    }
}
