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
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.w3c.dom.*;

import java.util.*;

/**
 * fn:element-to-map-plan($input as node()*) as map(*)
 *
 * Analyzes the structure of input elements and returns a plan map
 * describing the layout of each element type encountered.
 *
 * Layout values: empty, empty-plus, simple, simple-plus, list, list-plus,
 * record, mixed.
 */
public class FnElementToMapPlan extends BasicFunction {

    public static final FunctionSignature FN_ELEMENT_TO_MAP_PLAN = new FunctionSignature(
            new QName("element-to-map-plan", Function.BUILTIN_FUNCTION_NS),
            "Analyzes the structure of input elements and returns a plan map.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.NODE,
                            Cardinality.ZERO_OR_MORE, "The input nodes to analyze")
            },
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE,
                    "A map describing the element layouts"));

    public FnElementToMapPlan(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return new MapType(this, context);
        }

        final MapType plan = new MapType(this, context);
        final Set<String> processed = new HashSet<>();

        // Analyze each input node
        for (final SequenceIterator iter = args[0].iterate(); iter.hasNext(); ) {
            final Item item = iter.nextItem();
            if (item.getType() == Type.DOCUMENT) {
                // For document nodes, analyze the document element
                final Node docNode = ((NodeValue) item).getNode();
                analyzeNode(docNode, plan, processed);
            } else if (Type.subTypeOf(item.getType(), Type.ELEMENT)) {
                final Node elemNode = ((NodeValue) item).getNode();
                analyzeElement(elemNode, plan, processed);
            }
        }

        return plan;
    }

    private void analyzeNode(final Node node, final MapType plan, final Set<String> processed) throws XPathException {
        final NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                analyzeElement(child, plan, processed);
            }
        }
    }

    private void analyzeElement(final Node elem, final MapType plan, final Set<String> processed) throws XPathException {
        final String elemKey = getElementKey(elem);
        if (processed.contains(elemKey)) {
            return; // Already analyzed this element type
        }
        processed.add(elemKey);

        final MapType layoutMap = new MapType(this, context);

        // Determine layout
        final boolean hasAttributes = hasSignificantAttributes(elem);
        final List<Node> childElements = getChildElements(elem);
        final boolean hasTextContent = hasSignificantTextContent(elem);

        if (childElements.isEmpty() && !hasTextContent) {
            // Empty element
            layoutMap.add(new StringValue("layout"),
                    new StringValue(hasAttributes ? "empty-plus" : "empty"));
        } else if (childElements.isEmpty() && hasTextContent) {
            // Simple content (text only)
            final String type = detectContentType(elem);
            layoutMap.add(new StringValue("layout"),
                    new StringValue(hasAttributes ? "simple-plus" : "simple"));
            if (type != null) {
                layoutMap.add(new StringValue("type"), new StringValue(type));
            }
        } else if (!hasTextContent && allChildrenSameName(childElements)) {
            // List of same-named elements
            final String childName = getElementKey(childElements.get(0));
            layoutMap.add(new StringValue("layout"),
                    new StringValue(hasAttributes ? "list-plus" : "list"));
            layoutMap.add(new StringValue("child"), new StringValue(childName));
        } else if (hasTextContent || hasMixedContent(elem)) {
            // Mixed content
            layoutMap.add(new StringValue("layout"), new StringValue("mixed"));
        } else {
            // Record (distinct child element names)
            layoutMap.add(new StringValue("layout"), new StringValue("record"));
        }

        plan.add(new StringValue(elemKey), layoutMap);

        // Analyze attribute types
        if (hasAttributes) {
            final NamedNodeMap attrs = elem.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                final Node attr = attrs.item(i);
                final String attrName = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
                final String ns = attr.getNamespaceURI();
                // Skip xmlns declarations
                if ("http://www.w3.org/2000/xmlns/".equals(ns) || attrName.startsWith("xmlns")) {
                    continue;
                }
                final String attrKey = "@" + (ns != null && !ns.isEmpty() ?
                        "Q{" + ns + "}" + attrName : attrName);
                if (!processed.contains(attrKey)) {
                    processed.add(attrKey);
                    final MapType attrMap = new MapType(this, context);
                    final String type = detectValueType(attr.getNodeValue());
                    if (type != null) {
                        attrMap.add(new StringValue("type"), new StringValue(type));
                    }
                    plan.add(new StringValue(attrKey), attrMap);
                }
            }
        }

        // Recursively analyze child elements
        for (final Node child : childElements) {
            analyzeElement(child, plan, processed);
        }
    }

    private String getElementKey(final Node elem) {
        final String ns = elem.getNamespaceURI();
        final String local = elem.getLocalName() != null ? elem.getLocalName() : elem.getNodeName();
        if (ns != null && !ns.isEmpty()) {
            return "Q{" + ns + "}" + local;
        }
        return local;
    }

    private boolean hasSignificantAttributes(final Node elem) {
        final NamedNodeMap attrs = elem.getAttributes();
        if (attrs == null) return false;
        for (int i = 0; i < attrs.getLength(); i++) {
            final Node attr = attrs.item(i);
            final String name = attr.getNodeName();
            if (!name.startsWith("xmlns")) {
                return true;
            }
        }
        return false;
    }

    private List<Node> getChildElements(final Node elem) {
        final List<Node> result = new ArrayList<>();
        final NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                result.add(children.item(i));
            }
        }
        return result;
    }

    private boolean hasSignificantTextContent(final Node elem) {
        final NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                final String text = child.getNodeValue();
                if (text != null && !text.trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasMixedContent(final Node elem) {
        boolean hasElements = false;
        boolean hasText = false;
        final NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                hasElements = true;
            } else if (child.getNodeType() == Node.TEXT_NODE) {
                if (child.getNodeValue() != null && !child.getNodeValue().trim().isEmpty()) {
                    hasText = true;
                }
            }
        }
        return hasElements && hasText;
    }

    private boolean allChildrenSameName(final List<Node> children) {
        if (children.isEmpty()) return false;
        final String firstName = getElementKey(children.get(0));
        for (int i = 1; i < children.size(); i++) {
            if (!firstName.equals(getElementKey(children.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private String detectContentType(final Node elem) {
        final String text = elem.getTextContent();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return detectValueType(text.trim());
    }

    private String detectValueType(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            Double.parseDouble(value);
            return "numeric";
        } catch (final NumberFormatException e) {
            // Not numeric
        }
        if ("true".equals(value) || "false".equals(value)) {
            return "boolean";
        }
        return null; // default: string (not annotated)
    }
}
