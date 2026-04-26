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

import javax.xml.XMLConstants;
import java.util.*;

/**
 * fn:element-to-map-plan($input as node()*) as map(*)
 *
 * Analyzes the structure of input elements and returns a plan map
 * describing the layout of each element type encountered.
 *
 * The plan analyzes all instances of each element name across the corpus,
 * merging their layouts into a unified plan.
 */
public class FnElementToMapPlan extends BasicFunction {

    private static final String XSI_NS = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;

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

        // Collect all instances of each element name
        final Map<String, List<Element>> elementInstances = new LinkedHashMap<>();
        final Map<String, Set<String>> attrValues = new LinkedHashMap<>();

        for (final SequenceIterator iter = args[0].iterate(); iter.hasNext(); ) {
            final Item item = iter.nextItem();
            if (item.getType() == Type.DOCUMENT) {
                final Node docNode = ((NodeValue) item).getNode();
                collectElements(docNode, elementInstances, attrValues);
            } else if (Type.subTypeOf(item.getType(), Type.ELEMENT)) {
                final Element elem = (Element) ((NodeValue) item).getNode();
                collectElements(elem, elementInstances, attrValues);
            }
        }

        // Build plan from collected instances
        MapType plan = new MapType(this, context);

        for (final Map.Entry<String, List<Element>> entry : elementInstances.entrySet()) {
            final String elemKey = entry.getKey();
            final List<Element> instances = entry.getValue();
            final MapType layoutMap = analyzeInstances(elemKey, instances);
            plan = (MapType) plan.put(new StringValue(this, elemKey), layoutMap);
        }

        // Add attribute plans
        for (final Map.Entry<String, Set<String>> entry : attrValues.entrySet()) {
            final String attrKey = entry.getKey();
            final Set<String> values = entry.getValue();
            final MapType attrMap = new MapType(this, context);
            final String type = detectAggregateType(values);
            if (type != null) {
                attrMap.add(new StringValue("type"), new StringValue(type));
            }
            plan = (MapType) plan.put(new StringValue(this, attrKey), attrMap);
        }

        return plan;
    }

    private void collectElements(final Node node, final Map<String, List<Element>> elementInstances,
                                  final Map<String, Set<String>> attrValues) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            final Element elem = (Element) node;
            final String key = getElementKey(elem);
            elementInstances.computeIfAbsent(key, k -> new ArrayList<>()).add(elem);

            // Collect attribute values
            final NamedNodeMap attrs = elem.getAttributes();
            if (attrs != null) {
                for (int i = 0; i < attrs.getLength(); i++) {
                    final Attr attr = (Attr) attrs.item(i);
                    final String attrName = attr.getName();
                    if (attrName.startsWith("xmlns") && (attrName.length() == 5 || attrName.charAt(5) == ':')) {
                        continue;
                    }
                    final String attrNs = attr.getNamespaceURI();
                    if (XSI_NS.equals(attrNs)) {
                        continue;
                    }
                    final String local = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
                    final String ns = attr.getNamespaceURI();
                    final String attrKey = "@" + (ns != null && !ns.isEmpty() ?
                            "Q{" + ns + "}" + local : local);
                    attrValues.computeIfAbsent(attrKey, k -> new HashSet<>()).add(attr.getValue());
                }
            }
        }

        final NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectElements(children.item(i), elementInstances, attrValues);
        }
    }

    private MapType analyzeInstances(final String elemKey, final List<Element> instances) throws XPathException {
        final MapType layoutMap = new MapType(this, context);

        // Analyze each instance and merge
        boolean anyHasAttrs = false;
        boolean anyHasChildren = false;
        boolean anyHasText = false;
        boolean anyHasMixedContent = false;
        boolean anyIsEmpty = false;
        String listChildName = null;
        boolean allSameListChild = true;
        boolean anyHasNonUniqueChildren = false;

        // Track all child element names across instances for list detection
        final Set<String> allChildNames = new LinkedHashSet<>();
        boolean allInstancesHaveSameChildName = true;
        String commonChildName = null;

        for (final Element elem : instances) {
            final boolean hasAttrs = hasSignificantAttributes(elem);
            if (hasAttrs) anyHasAttrs = true;

            final List<Element> childElements = getChildElements(elem);
            final boolean hasText = hasSignificantTextContent(elem);

            if (childElements.isEmpty() && !hasText) {
                anyIsEmpty = true;
            }
            if (!childElements.isEmpty()) {
                anyHasChildren = true;
            }
            if (hasText) {
                anyHasText = true;
            }
            if (hasText && !childElements.isEmpty()) {
                anyHasMixedContent = true;
            }

            // Check list pattern for this instance
            if (!childElements.isEmpty() && !hasText) {
                final Set<String> names = new LinkedHashSet<>();
                for (final Element child : childElements) {
                    names.add(getElementKey(child));
                }
                allChildNames.addAll(names);

                if (names.size() == 1) {
                    final String name = names.iterator().next();
                    if (commonChildName == null) {
                        commonChildName = name;
                    } else if (!commonChildName.equals(name)) {
                        allInstancesHaveSameChildName = false;
                    }
                } else {
                    allInstancesHaveSameChildName = false;
                    // Check for non-unique names
                    final Map<String, Integer> counts = new LinkedHashMap<>();
                    for (final Element child : childElements) {
                        counts.merge(getElementKey(child), 1, Integer::sum);
                    }
                    if (counts.values().stream().anyMatch(c -> c > 1)) {
                        anyHasNonUniqueChildren = true;
                    }
                }
            }
        }

        // Determine layout
        String layout;
        if (anyHasMixedContent) {
            layout = "mixed";
        } else if (anyHasChildren && anyHasText) {
            layout = "mixed";
        } else if (anyHasChildren) {
            if (allInstancesHaveSameChildName && commonChildName != null) {
                // All instances have children all with the same name
                layout = anyHasAttrs ? "list-plus" : "list";
                listChildName = commonChildName;
            } else if (anyHasNonUniqueChildren) {
                layout = "sequence";
            } else {
                // Check if any instance has non-unique children
                layout = "sequence";
                // Actually, if all instances have unique children → record
                boolean allInstancesUnique = true;
                for (final Element elem : instances) {
                    final List<Element> childElements = getChildElements(elem);
                    if (!childElements.isEmpty()) {
                        final Set<String> names = new HashSet<>();
                        boolean unique = true;
                        for (final Element child : childElements) {
                            if (!names.add(getElementKey(child))) {
                                unique = false;
                                break;
                            }
                        }
                        if (!unique) {
                            allInstancesUnique = false;
                            break;
                        }
                    }
                }
                if (allInstancesUnique && !anyIsEmpty) {
                    layout = "record";
                } else if (allInstancesUnique) {
                    // Some empty, some with children — use sequence to be safe
                    layout = "sequence";
                }
            }
        } else if (anyHasText) {
            layout = anyHasAttrs ? "simple-plus" : "simple";
        } else {
            layout = anyHasAttrs ? "empty-plus" : "empty";
        }

        // Handle mixed state: if some instances empty and some have children
        if (anyIsEmpty && anyHasChildren && !layout.startsWith("list")) {
            layout = "sequence";
        }

        layoutMap.add(new StringValue("layout"), new StringValue(layout));

        if (listChildName != null && (layout.equals("list") || layout.equals("list-plus"))) {
            layoutMap.add(new StringValue("child"), new StringValue(listChildName));
        }

        // Detect type for simple content
        if (layout.equals("simple") || layout.equals("simple-plus")) {
            final Set<String> textValues = new HashSet<>();
            for (final Element elem : instances) {
                final String text = elem.getTextContent();
                if (text != null && !text.trim().isEmpty()) {
                    textValues.add(text.trim());
                }
            }
            final String type = detectAggregateType(textValues);
            if (type != null) {
                layoutMap.add(new StringValue("type"), new StringValue(type));
            }
        }

        return layoutMap;
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
            if (name.startsWith("xmlns") && (name.length() == 5 || name.charAt(5) == ':')) {
                continue;
            }
            final String ns = attr instanceof Attr ? ((Attr) attr).getNamespaceURI() : null;
            if (XSI_NS.equals(ns)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private List<Element> getChildElements(final Node elem) {
        final List<Element> result = new ArrayList<>();
        final NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) children.item(i));
            }
        }
        return result;
    }

    private boolean hasSignificantTextContent(final Node elem) {
        final NodeList children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                final String text = child.getNodeValue();
                if (text != null && !text.trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String detectAggregateType(final Set<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        boolean allNumeric = true;
        boolean allBoolean = true;
        for (final String value : values) {
            if (allNumeric) {
                try {
                    Double.parseDouble(value);
                } catch (final NumberFormatException e) {
                    if (!"NaN".equals(value) && !"INF".equals(value) && !"-INF".equals(value)) {
                        allNumeric = false;
                    }
                }
            }
            if (allBoolean) {
                if (!"true".equals(value) && !"false".equals(value) &&
                        !"1".equals(value) && !"0".equals(value)) {
                    allBoolean = false;
                }
            }
        }
        if (allNumeric) return "numeric";
        if (allBoolean) return "boolean";
        return null;
    }
}
