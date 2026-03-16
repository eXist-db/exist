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

/**
 * Implements XQuery 4.0 fn:element-to-map.
 *
 * Converts an element node to a map representation following the XQ4 spec rules
 * for different content models (empty, simple, record, list, sequence, mixed).
 */
public class FnElementToMap extends BasicFunction {

    public static final FunctionSignature[] FN_ELEMENT_TO_MAP = {
            new FunctionSignature(
                    new QName("element-to-map", Function.BUILTIN_FUNCTION_NS),
                    "Converts an element to a map representation.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("element", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "The element to convert")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_ONE, "The map representation")),
            new FunctionSignature(
                    new QName("element-to-map", Function.BUILTIN_FUNCTION_NS),
                    "Converts an element to a map representation with options.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("element", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "The element to convert"),
                            new FunctionParameterSequenceType("options", Type.MAP_ITEM, Cardinality.ZERO_OR_ONE, "Options map")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_ONE, "The map representation"))
    };

    private static final String DEFAULT_ATTR_MARKER = "@";
    private static final String DEFAULT_CONTENT_KEY = "#content";
    private static final String DEFAULT_COMMENT_KEY = "#comment";
    private static final String DEFAULT_NAME_FORMAT = "eqname";

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

        if (args.length > 1 && !args[1].isEmpty()) {
            final MapType options = (MapType) args[1].itemAt(0);
            final Sequence nfSeq = options.get(new StringValue(this, "name-format"));
            if (nfSeq != null && !nfSeq.isEmpty()) {
                nameFormat = nfSeq.getStringValue();
            }
            final Sequence amSeq = options.get(new StringValue(this, "attribute-marker"));
            if (amSeq != null && !amSeq.isEmpty()) {
                attrMarker = amSeq.getStringValue();
            }
            final Sequence ckSeq = options.get(new StringValue(this, "content-key"));
            if (ckSeq != null && !ckSeq.isEmpty()) {
                contentKey = ckSeq.getStringValue();
            }
            final Sequence cmSeq = options.get(new StringValue(this, "comment-key"));
            if (cmSeq != null && !cmSeq.isEmpty()) {
                commentKey = cmSeq.getStringValue();
            }
        }

        final Options opts = new Options(nameFormat, attrMarker, contentKey, commentKey);
        return convertElement((Element) node, opts);
    }

    private MapType convertElement(final Element elem, final Options opts) throws XPathException {
        final String elemName = formatName(elem, opts);
        final Sequence value = convertContent(elem, opts);

        MapType result = new MapType(this, context);
        result = (MapType) result.put(new StringValue(this, elemName), value);
        return result;
    }

    private Sequence convertContent(final Element elem, final Options opts) throws XPathException {
        // Collect attributes (excluding xmlns and xsi:type)
        final Map<String, String> attrs = new LinkedHashMap<>();
        final NamedNodeMap attrNodes = elem.getAttributes();
        if (attrNodes != null) {
            for (int i = 0; i < attrNodes.getLength(); i++) {
                final Attr attr = (Attr) attrNodes.item(i);
                final String attrName = attr.getName();
                // Skip namespace declarations and xsi:type
                if (attrName.startsWith("xmlns") && (attrName.length() == 5 || attrName.charAt(5) == ':')) {
                    continue;
                }
                if ("xsi:type".equals(attrName)) {
                    continue;
                }
                if (attrName.equals("xsi:nil")) {
                    continue;
                }
                final String key = opts.attrMarker + formatAttrName(attr, opts);
                attrs.put(key, attr.getValue());
            }
        }

        // Check for xsi:nil
        final String nilAttr = elem.getAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "nil");
        if ("true".equals(nilAttr) || "1".equals(nilAttr)) {
            if (attrs.isEmpty()) {
                // Return fn:null() as QName
                return new QNameValue(this, context, new QName("null", Function.BUILTIN_FUNCTION_NS, "fn"));
            } else {
                MapType attrMap = new MapType(this, context);
                for (final Map.Entry<String, String> a : attrs.entrySet()) {
                    attrMap = (MapType) attrMap.put(new StringValue(this, a.getKey()), new StringValue(this, a.getValue()));
                }
                attrMap = (MapType) attrMap.put(
                        new StringValue(this, opts.contentKey),
                        new QNameValue(this, context, new QName("null", Function.BUILTIN_FUNCTION_NS, "fn")));
                return attrMap;
            }
        }

        // Collect child nodes (elements, text, comments, PIs)
        final List<Node> children = new ArrayList<>();
        final NodeList childNodes = elem.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            final Node child = childNodes.item(i);
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE:
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                case Node.COMMENT_NODE:
                    children.add(child);
                    break;
                default:
                    break;
            }
        }

        // Classify content model
        final boolean hasElements = children.stream().anyMatch(n -> n.getNodeType() == Node.ELEMENT_NODE);
        final boolean hasTextContent = children.stream().anyMatch(n ->
                (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE)
                        && !n.getTextContent().trim().isEmpty());
        final boolean hasComments = children.stream().anyMatch(n -> n.getNodeType() == Node.COMMENT_NODE);

        // Empty element
        if (children.isEmpty() || (!hasElements && !hasTextContent && !hasComments)) {
            if (attrs.isEmpty()) {
                return new StringValue(this, "");
            } else {
                // Empty-plus: attributes only, no #content key
                MapType attrMap = new MapType(this, context);
                for (final Map.Entry<String, String> a : attrs.entrySet()) {
                    attrMap = (MapType) attrMap.put(new StringValue(this, a.getKey()), new StringValue(this, a.getValue()));
                }
                return attrMap;
            }
        }

        // Simple text content (no child elements)
        if (!hasElements && !hasComments) {
            final String textContent = getTextContent(children);
            if (attrs.isEmpty()) {
                return new StringValue(this, textContent);
            } else {
                return buildAttrMap(attrs, new StringValue(this, textContent), opts);
            }
        }

        // Mixed content (has both text and element children)
        if (hasTextContent && hasElements) {
            return buildMixedContent(children, attrs, opts);
        }

        // Element-only content — determine layout
        final List<Element> childElements = new ArrayList<>();
        for (final Node child : children) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                childElements.add((Element) child);
            }
        }

        // Check for comments interleaved with elements
        if (hasComments && !hasElements) {
            return buildMixedContent(children, attrs, opts);
        }

        // Check if all children have the same name (list pattern)
        final boolean allSameName = childElements.size() > 1 &&
                childElements.stream().allMatch(e ->
                        formatName(e, opts).equals(formatName(childElements.get(0), opts)));

        // Check if all children have unique names (record pattern)
        final Map<String, List<Element>> groupedByName = new LinkedHashMap<>();
        for (final Element child : childElements) {
            groupedByName.computeIfAbsent(formatName(child, opts), k -> new ArrayList<>()).add(child);
        }
        final boolean allUnique = groupedByName.values().stream().allMatch(l -> l.size() == 1);

        if (allSameName) {
            // List layout: array of child values
            return buildListContent(childElements, attrs, opts);
        } else if (allUnique) {
            // Record layout: map of child name → value
            return buildRecordContent(childElements, attrs, children, opts);
        } else {
            // Sequence layout: array of child maps
            return buildSequenceContent(children, attrs, opts);
        }
    }

    private Sequence buildAttrMap(final Map<String, String> attrs, final Sequence contentValue, final Options opts) throws XPathException {
        MapType attrMap = new MapType(this, context);
        for (final Map.Entry<String, String> a : attrs.entrySet()) {
            attrMap = (MapType) attrMap.put(new StringValue(this, a.getKey()), new StringValue(this, a.getValue()));
        }
        attrMap = (MapType) attrMap.put(new StringValue(this, opts.contentKey), contentValue);
        return attrMap;
    }

    private Sequence buildListContent(final List<Element> children, final Map<String, String> attrs, final Options opts) throws XPathException {
        // Array of child content values
        final List<Sequence> items = new ArrayList<>();
        for (final Element child : children) {
            items.add(convertContent(child, opts));
        }
        final ArrayType array = new ArrayType(this, context, items);

        if (attrs.isEmpty()) {
            return array;
        } else {
            MapType attrMap = new MapType(this, context);
            for (final Map.Entry<String, String> a : attrs.entrySet()) {
                attrMap = (MapType) attrMap.put(new StringValue(this, a.getKey()), new StringValue(this, a.getValue()));
            }
            attrMap = (MapType) attrMap.put(new StringValue(this, opts.contentKey), array);
            return attrMap;
        }
    }

    private Sequence buildRecordContent(final List<Element> childElements, final Map<String, String> attrs,
                                         final List<Node> allChildren, final Options opts) throws XPathException {
        MapType recordMap = new MapType(this, context);

        // Add attributes first
        for (final Map.Entry<String, String> a : attrs.entrySet()) {
            recordMap = (MapType) recordMap.put(new StringValue(this, a.getKey()), new StringValue(this, a.getValue()));
        }

        // Add comments if present
        for (final Node child : allChildren) {
            if (child.getNodeType() == Node.COMMENT_NODE) {
                recordMap = (MapType) recordMap.put(
                        new StringValue(this, opts.commentKey),
                        new StringValue(this, child.getTextContent()));
            }
        }

        // Add child elements
        for (final Element child : childElements) {
            final String childName = formatName(child, opts);
            final Sequence childValue = convertContent(child, opts);
            recordMap = (MapType) recordMap.put(new StringValue(this, childName), childValue);
        }

        return recordMap;
    }

    private Sequence buildSequenceContent(final List<Node> children, final Map<String, String> attrs, final Options opts) throws XPathException {
        // Build array of child maps/values
        final List<Sequence> items = new ArrayList<>();
        for (final Node child : children) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                items.add(convertElement((Element) child, opts));
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                final String text = child.getTextContent();
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
        final ArrayType array = new ArrayType(this, context, items);

        if (attrs.isEmpty()) {
            return array;
        } else {
            MapType attrMap = new MapType(this, context);
            for (final Map.Entry<String, String> a : attrs.entrySet()) {
                attrMap = (MapType) attrMap.put(new StringValue(this, a.getKey()), new StringValue(this, a.getValue()));
            }
            attrMap = (MapType) attrMap.put(new StringValue(this, opts.contentKey), array);
            return attrMap;
        }
    }

    private Sequence buildMixedContent(final List<Node> children, final Map<String, String> attrs, final Options opts) throws XPathException {
        final List<Sequence> items = new ArrayList<>();
        for (final Node child : children) {
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE:
                    items.add(convertElement((Element) child, opts));
                    break;
                case Node.TEXT_NODE:
                case Node.CDATA_SECTION_NODE:
                    final String text = child.getTextContent();
                    if (!text.isEmpty()) {
                        items.add(new StringValue(this, text));
                    }
                    break;
                case Node.COMMENT_NODE:
                    MapType commentMap = new MapType(this, context);
                    commentMap = (MapType) commentMap.put(
                            new StringValue(this, opts.commentKey),
                            new StringValue(this, child.getTextContent()));
                    items.add(commentMap);
                    break;
                default:
                    break;
            }
        }
        final ArrayType array = new ArrayType(this, context, items);

        if (attrs.isEmpty()) {
            return array;
        } else {
            MapType attrMap = new MapType(this, context);
            for (final Map.Entry<String, String> a : attrs.entrySet()) {
                attrMap = (MapType) attrMap.put(new StringValue(this, a.getKey()), new StringValue(this, a.getValue()));
            }
            attrMap = (MapType) attrMap.put(new StringValue(this, opts.contentKey), array);
            return attrMap;
        }
    }

    private String formatName(final Element elem, final Options opts) {
        final String ns = elem.getNamespaceURI();
        final String local = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();

        switch (opts.nameFormat) {
            case "eqname":
                if (ns != null && !ns.isEmpty()) {
                    return "Q{" + ns + "}" + local;
                }
                return local;
            case "lexical":
                final String prefix = elem.getPrefix();
                if (prefix != null && !prefix.isEmpty()) {
                    return prefix + ":" + local;
                }
                return local;
            case "local":
                return local;
            default:
                // Default to eqname
                if (ns != null && !ns.isEmpty()) {
                    return "Q{" + ns + "}" + local;
                }
                return local;
        }
    }

    private String formatAttrName(final Attr attr, final Options opts) {
        final String ns = attr.getNamespaceURI();
        final String local = attr.getLocalName() != null ? attr.getLocalName() : attr.getName();

        switch (opts.nameFormat) {
            case "eqname":
                if (ns != null && !ns.isEmpty()) {
                    return "Q{" + ns + "}" + local;
                }
                return local;
            case "lexical":
                final String prefix = attr.getPrefix();
                if (prefix != null && !prefix.isEmpty()) {
                    return prefix + ":" + local;
                }
                return local;
            case "local":
                return local;
            default:
                if (ns != null && !ns.isEmpty()) {
                    return "Q{" + ns + "}" + local;
                }
                return local;
        }
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

    private static class Options {
        final String nameFormat;
        final String attrMarker;
        final String contentKey;
        final String commentKey;

        Options(final String nameFormat, final String attrMarker, final String contentKey, final String commentKey) {
            this.nameFormat = nameFormat;
            this.attrMarker = attrMarker;
            this.contentKey = contentKey;
            this.commentKey = commentKey;
        }
    }
}
