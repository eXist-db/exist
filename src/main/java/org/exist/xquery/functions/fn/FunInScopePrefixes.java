/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2005-2009 The eXist Project
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

package org.exist.xquery.functions.fn;

import java.util.*;
import java.util.Map.Entry;

import org.exist.Namespaces;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;

public class FunInScopePrefixes extends BasicFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("in-scope-prefixes", Function.BUILTIN_FUNCTION_NS),
                    "Returns the prefixes of the in-scope namespaces for $element. " +
                            "For namespaces that have a prefix, it returns the prefix as an " +
                            "xs:NCName. For the default namespace, which has no prefix, " +
                            "it returns the zero-length string.",
                    new SequenceType[]{new FunctionParameterSequenceType("element", Type.ELEMENT, Cardinality.EXACTLY_ONE, "The element")},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "the prefixes"));

    public FunInScopePrefixes(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
        }

        final Map<String, String> prefixes = collectPrefixes(context, (NodeValue) args[0].itemAt(0));

        final ValueSequence result = new ValueSequence();

        for (final String prefix : prefixes.keySet()) {
            //The predefined namespaces (e.g. "exist" for temporary nodes) could have been removed from the static context
            if (!(context.getURIForPrefix(prefix) == null &&
                    ("exist".equals(prefix) || "xs".equals(prefix) || "xsi".equals(prefix) ||
                            "wdt".equals(prefix) || "fn".equals(prefix) || "local".equals(prefix)))) {
                result.add(new StringValue(prefix));
            }
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }

    public static Map<String, String> collectPrefixes(XQueryContext context, NodeValue nodeValue) {
        final Map<String, String> prefixes = new LinkedHashMap<String, String>();
        prefixes.put("xml", Namespaces.XML_NS);

        final Map<String, String> inScopePrefixes = context.getInScopePrefixes();
        if (inScopePrefixes != null) {
            prefixes.putAll(inScopePrefixes);
        }

        if (nodeValue.getImplementationType() == NodeValue.PERSISTENT_NODE) {
            //NodeProxy proxy = (NodeProxy) node;
            Node node = nodeValue.getNode();
            if (context.preserveNamespaces()) {
                //Horrible hacks to work-around bad in-scope NS : we reconstruct a NS context !
                if (context.inheritNamespaces()) {
                    //Grab ancestors' NS
                    final Deque<Element> stack = new ArrayDeque<>();
                    do {
                        stack.add((Element) node);
                        node = node.getParentNode();
                    } while (node != null && node.getNodeType() == Node.ELEMENT_NODE);

                    while (!stack.isEmpty()) {
                        collectNamespacePrefixes(stack.pop(), prefixes);
                    }
					/*
					NodeSet ancestors = nodeValue.getAncestors(contextId, true);
					for (Iterator i = ancestors.iterator(); i.hasNext(); ) {
						proxy = (NodeProxy) i.next();
						collectNamespacePrefixes((ElementImpl)node, prefixes);
					}
					*/
                } else {
                    //Grab self's NS
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        collectNamespacePrefixes((Element) node, prefixes);
                    }
                }
            } else {
                //untested : copied from below
                if (context.inheritNamespaces()) {
                    //get the top-most ancestor
                    final Deque<Element> stack = new ArrayDeque<>();
                    do {
                        if (node.getParentNode() == null || node.getParentNode() instanceof DocumentImpl) {
                            stack.add((Element) node);
                        }
                        node = node.getParentNode();
                    } while (node != null && node.getNodeType() == Node.ELEMENT_NODE);

                    while (!stack.isEmpty()) {
                        collectNamespacePrefixes(stack.pop(), prefixes);
                    }
                }
            }
        } else { // In-memory node
            //NodeImpl nodeImpl = (NodeImpl) node;
            Node node = nodeValue.getNode();
            if (context.preserveNamespaces()) {
                //Horrible hacks to work-around bad in-scope NS : we reconstruct a NS context !
                if (context.inheritNamespaces()) {
                    //Grab ancestors' NS
                    final Deque<Element> stack = new ArrayDeque<>();
                    do {
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            stack.add((Element) node);
                        }
                        node = node.getParentNode();
                    } while (node != null && node.getNodeType() == Node.ELEMENT_NODE);

                    while (!stack.isEmpty()) {
                        collectNamespacePrefixes(stack.pop(), prefixes);
                    }

                } else {
                    //Grab self's NS
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        collectNamespacePrefixes((Element) node, prefixes);
                    }
                }
            } else {
                if (context.inheritNamespaces()) {
                    //get the top-most ancestor
                    final Deque<Element> stack = new ArrayDeque<>();
                    do {
                        if (node.getParentNode() == null || node.getParentNode() instanceof org.exist.dom.memtree.DocumentImpl) {
                            stack.add((Element) node);
                        }
                        node = node.getParentNode();
                    } while (node != null && node.getNodeType() == Node.ELEMENT_NODE);

                    while (!stack.isEmpty()) {
                        collectNamespacePrefixes(stack.pop(), prefixes);
                    }
                }
            }
        }

        //clean up
        String key = null;
        String value = null;
        for (final Entry<String, String> entry : prefixes.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();

            if ((key == null || key.isEmpty()) && (value == null || value.isEmpty())) {
                prefixes.remove(key);
            }

        }

        return prefixes;
    }

    public static void collectNamespacePrefixes(final Element element, final Map<String, String> prefixes) {
        final String namespaceURI = element.getNamespaceURI();
        if (namespaceURI != null && !namespaceURI.isEmpty()) {
            final String prefix = element.getPrefix();
            prefixes.put(prefix == null ? XMLConstants.DEFAULT_NS_PREFIX : prefix, namespaceURI);
        }

        if (element instanceof org.exist.dom.memtree.ElementImpl) {
            ((org.exist.dom.memtree.ElementImpl) element).getNamespaceMap(prefixes);

        } else {
            final ElementImpl elementImpl = (org.exist.dom.persistent.ElementImpl) element;
            if (elementImpl.declaresNamespacePrefixes()) {
                for (final Iterator<String> i = elementImpl.getPrefixes(); i.hasNext(); ) {
                    final String prefix = i.next();
                    prefixes.put(prefix, elementImpl.getNamespaceForPrefix(prefix));
                }
            }
        }

        if (namespaceURI != null && namespaceURI.isEmpty()) {
            final String prefix = element.getPrefix();
            prefixes.remove(prefix == null ? XMLConstants.DEFAULT_NS_PREFIX : prefix);
        }
    }

}