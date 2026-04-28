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

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Implements XQuery 4.0 fn:in-scope-namespaces.
 *
 * Returns a map(xs:string, xs:string) where keys are namespace prefixes
 * (empty string for the default namespace) and values are namespace URIs.
 *
 * Uses nearest-ancestor-wins semantics: for each prefix, the declaration on
 * the nearest ancestor (or the element itself) takes precedence.
 */
public class FnInScopeNamespaces extends BasicFunction {

    public static final FunctionSignature FN_IN_SCOPE_NAMESPACES = new FunctionSignature(
            new QName("in-scope-namespaces", Function.BUILTIN_FUNCTION_NS),
            "Returns a map from namespace prefixes to namespace URIs for all in-scope namespaces of the given element.",
            new SequenceType[]{
                    new FunctionParameterSequenceType("element", Type.ELEMENT, Cardinality.EXACTLY_ONE, "The element node")
            },
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "A map of prefix to URI"));

    public FnInScopeNamespaces(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final NodeValue nodeValue = (NodeValue) args[0].itemAt(0);

        // Collect all in-scope namespaces with nearest-ancestor-wins semantics
        final Map<String, String> nsMap = new LinkedHashMap<>();
        nsMap.put("xml", Namespaces.XML_NS);

        // Start with static context namespaces (lowest priority)
        final Map<String, String> inScopePrefixes = context.getInScopePrefixes();
        if (inScopePrefixes != null) {
            nsMap.putAll(inScopePrefixes);
        }

        // Walk from element up to root, collecting namespace declarations.
        // Track which prefixes we've already seen from closer ancestors
        // so that nearer declarations override farther ones.
        final Set<String> seen = new HashSet<>();
        final Map<String, String> elementNs = new LinkedHashMap<>();
        Node node = nodeValue.getNode();

        if (context.preserveNamespaces()) {
            while (node != null && node.getNodeType() == Node.ELEMENT_NODE) {
                if (context.inheritNamespaces() || node == nodeValue.getNode()) {
                    collectElementNamespaces((Element) node, elementNs, seen);
                }
                node = node.getParentNode();
            }
        }

        // Element declarations override static context (merge on top)
        nsMap.putAll(elementNs);

        // Clean up: remove entries where both key and value are empty
        nsMap.entrySet().removeIf(entry ->
                (entry.getKey() == null || entry.getKey().isEmpty()) &&
                (entry.getValue() == null || entry.getValue().isEmpty()));

        // Build the result map
        MapType result = new MapType(this, context);
        for (final Map.Entry<String, String> entry : nsMap.entrySet()) {
            result = (MapType) result.put(
                    new StringValue(this, entry.getKey()),
                    new StringValue(this, entry.getValue()));
        }

        return result;
    }

    /**
     * Collect namespace declarations from a single element, respecting nearest-wins.
     * Only adds prefixes not already in the {@code seen} set.
     */
    private static void collectElementNamespaces(final Element element, final Map<String, String> nsMap, final Set<String> seen) {
        // Element's own namespace
        final String namespaceURI = element.getNamespaceURI();
        if (namespaceURI != null && !namespaceURI.isEmpty()) {
            final String prefix = element.getPrefix();
            final String key = prefix == null ? "" : prefix;
            if (seen.add(key)) {
                nsMap.put(key, namespaceURI);
            }
        }

        // Namespace declarations from the element
        if (element instanceof org.exist.dom.memtree.ElementImpl) {
            final Map<String, String> elemNs = new LinkedHashMap<>();
            ((org.exist.dom.memtree.ElementImpl) element).getNamespaceMap(elemNs);
            for (final Map.Entry<String, String> entry : elemNs.entrySet()) {
                // memtree stores default-namespace declarations under the literal
                // key "xmlns" (XMLConstants.XMLNS_ATTRIBUTE); normalize to "" so
                // the result map matches the spec.
                String key = entry.getKey();
                if ("xmlns".equals(key)) {
                    key = "";
                }
                if (seen.add(key)) {
                    nsMap.put(key, entry.getValue());
                }
            }
        } else if (element instanceof org.exist.dom.persistent.ElementImpl) {
            final org.exist.dom.persistent.ElementImpl elemImpl = (org.exist.dom.persistent.ElementImpl) element;
            if (elemImpl.declaresNamespacePrefixes()) {
                for (final java.util.Iterator<String> i = elemImpl.getPrefixes(); i.hasNext(); ) {
                    final String prefix = i.next();
                    if (seen.add(prefix)) {
                        nsMap.put(prefix, elemImpl.getNamespaceForPrefix(prefix));
                    }
                }
            }
        }

        // Handle undeclaration: if namespace URI is explicitly empty, remove the prefix
        if (namespaceURI != null && namespaceURI.isEmpty()) {
            final String prefix = element.getPrefix();
            final String key = prefix == null ? "" : prefix;
            nsMap.remove(key);
        }
    }
}
