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
package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.TextImpl;
import org.exist.util.serializer.AttrList;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents an enclosed expression <code>{expr}</code> inside element
 * content. Enclosed expressions within attribute values are processed by
 * {@link org.exist.xquery.AttributeConstructor}.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class EnclosedExpr extends PathExpr {

    public EnclosedExpr(XQueryContext context) {
        super(context);
    }

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
		newContextInfo.removeFlag(IN_NODE_CONSTRUCTOR);
		super.analyze(newContextInfo);
	}


    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#eval(org.exist.xquery.StaticContext,
     * org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
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
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        // evaluate the expression
        Sequence result;
        context.enterEnclosedExpr();
        try {
            context.pushDocumentContext();

            MemTreeBuilder innerBuilder = null;
            try {
                result = super.eval(contextSequence, null);
                // Capture the builder that may have been created by direct constructors inside
                // this enclosed expression. If null, no direct constructors ran — the result
                // items are all pre-existing nodes (variable references / copies).
                innerBuilder = context.peekDocumentBuilder();
            } finally {
                context.popDocumentContext();
            }

            // Compute ancestor namespace context for no-inherit copy handling.
            // This is the union of inherited namespaces (from outer constructors) and
            // in-scope namespaces (from the immediately enclosing constructor), i.e. all
            // namespace bindings that an ancestor traversal from this element would find.
            final boolean noInherit = !context.inheritNamespaces();
            Map<String, String> ancestorNS = null;
            if (noInherit) {
                final Map<String, String> inherited = context.getAllInheritedNamespaces();
                final Map<String, String> inScope = context.getInScopeNamespaces();
                if ((inherited != null && !inherited.isEmpty()) || (inScope != null && !inScope.isEmpty())) {
                    ancestorNS = new HashMap<>();
                    if (inherited != null) { ancestorNS.putAll(inherited); }
                    if (inScope != null) { ancestorNS.putAll(inScope); }
                }
            }

            // create the output
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(this, builder);
            receiver.setCheckNS(true);
            try {
                // flatten all arrays in the input sequence
                result = ArrayType.flatten(result);
                final SequenceIterator i = result.iterate();
                Item next = i.nextItem();
                StringBuilder buf = null;
                boolean allowAttribs = true;
                while (next != null) {
                    context.proceed(this, builder);
                    if (Type.subTypeOf(next.getType(), Type.FUNCTION)) {
                        final Expression expression = ((FunctionReference) next).getExpression();
                        throw new XPathException((expression == null) ? this : expression, ErrorCodes.XQTY0105, "Enclosed expression contains function item");
                        // if item is an atomic value, collect the string values of all
                        // following atomic values and separate them by a space.
                    } else if (Type.subTypeOf(next.getType(), Type.ANY_ATOMIC_TYPE)) {
                        if (buf == null) {
                            buf = new StringBuilder();
                        } else if (!buf.isEmpty()) {
                            buf.append(' ');
                        }
                        buf.append(next.getStringValue());
                        allowAttribs = false;
                        next = i.nextItem();
                        //If the item is a node, flush any collected character data and
                        //copy the node to the target doc.
                    } else if (Type.subTypeOf(next.getType(), Type.NODE)) {

                        //It is possible for a text node constructor to construct a text node containing a zero-length string.
                        //However, if used in the content of a constructed element or document node,
                        //such a text node will be deleted or merged with another text node.
                        if (next instanceof TextImpl && ((TextImpl) next).getStringValue().isEmpty()) {
                            next = i.nextItem();
                            continue;
                        }
                        if (buf != null && !buf.isEmpty()) {
                            receiver.characters(buf);
                            buf.setLength(0);
                        }
                        if (next.getType() == Type.ATTRIBUTE && !allowAttribs) {
                            throw new XPathException(this, ErrorCodes.XQTY0024,
                                    "An attribute may not appear after another child node.");
                        }
                        try {
                            receiver.setCheckNS(false);
                            // When copy-namespaces no-inherit is active, pre-existing element nodes
                            // (i.e. nodes from variable references, not direct constructors) must have
                            // namespace undeclarations injected so that ancestor-traversal in
                            // in-scope-prefixes() is neutralized for inherited namespace bindings.
                            if (noInherit && ancestorNS != null
                                    && next.getType() == Type.ELEMENT
                                    && next instanceof NodeImpl) {
                                final NodeImpl nodeImpl = (NodeImpl) next;
                                final boolean isPreExisting = (innerBuilder == null)
                                        || (nodeImpl.getOwnerDocument() != innerBuilder.getDocument());
                                if (isPreExisting) {
                                    next.copyTo(context.getBroker(),
                                            new NoInheritCopyReceiver(this, builder, ancestorNS));
                                } else {
                                    next.copyTo(context.getBroker(), receiver);
                                }
                            } else {
                                next.copyTo(context.getBroker(), receiver);
                            }
                            receiver.setCheckNS(true);
                        } catch (DOMException e) {
                            if (e.code == DOMException.NAMESPACE_ERR) {
                                throw new XPathException(this, ErrorCodes.XQDY0102, e.getMessage());
                            } else {
                                throw new XPathException(this, e.getMessage(), e);
                            }
                        }
                        allowAttribs = next.getType() == Type.ATTRIBUTE || next.getType() == Type.NAMESPACE;
                        next = i.nextItem();
                    }
                }
                // flush remaining character data
                if (buf != null && !buf.isEmpty()) {
                    receiver.characters(buf);
                }
            } catch (final SAXException e) {
                LOG.warn("SAXException during serialization: {}", e.getMessage(), e);
                throw new XPathException(this, e);
            }
        } finally {
            context.exitEnclosedExpr();
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        return result;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("{");
        dumper.startIndent();
        super.dump(dumper);
        dumper.endIndent();
        dumper.nl().display("}");
    }

    public String toString() {
        return "{" + super.toString() + "}";
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visitPathExpr(this);
    }

    @Override
    public boolean allowMixedNodesInReturn() {
        return true;
    }

    @Override
    public Expression simplify() {
        return this;
    }

    @Override
    public boolean evalNextExpressionOnEmptyContextSequence() {
        return true;
    }

    /**
     * A {@link DocumentBuilderReceiver} that injects namespace undeclarations onto the
     * root element of a copied pre-existing node when {@code declare copy-namespaces no-inherit}
     * is active.  The undeclarations neutralize ancestor namespace bindings so that
     * {@code fn:in-scope-prefixes()} traversing the ancestor chain returns the correct result.
     *
     * <p>Only prefixes present in {@code ancestorNS} but absent from the root element's own
     * namespace declarations are undeclared (i.e. recorded as {@code xmlns:prefix=""}).</p>
     */
    private static final class NoInheritCopyReceiver extends DocumentBuilderReceiver {

        private final Map<String, String> ancestorNS;
        /** True once the root element's startElement event has been seen. */
        private boolean rootSeen = false;
        /** True once undeclarations have been flushed (happens before first non-namespace event). */
        private boolean undeclsFlushed = false;
        /** Prefixes that the root element itself declares (element prefix + xmlns:* nodes). */
        private final Set<String> rootOwnPrefixes = new HashSet<>();

        NoInheritCopyReceiver(final Expression expr, final MemTreeBuilder builder,
                final Map<String, String> ancestorNS) {
            super(expr, builder);
            this.ancestorNS = ancestorNS;
        }

        @Override
        public void startElement(final QName qname, final AttrList attribs) {
            if (!rootSeen) {
                rootSeen = true;
                // Track the element's own namespace prefix so we don't undeclare it.
                final String prefix = qname.getPrefix();
                if (prefix != null && !prefix.isEmpty()) {
                    rootOwnPrefixes.add(prefix);
                }
                // Note: namespace declaration nodes for this element come via addNamespaceNode
                // after startElement; they are collected in rootOwnPrefixes there.
            } else {
                maybeFlushUndeclarations();
            }
            super.startElement(qname, attribs);
        }

        @Override
        public void addNamespaceNode(final QName qname) throws SAXException {
            if (rootSeen && !undeclsFlushed) {
                // Collect the prefix that this namespace node declares on the root element.
                rootOwnPrefixes.add(qname.getLocalPart());
            }
            super.addNamespaceNode(qname);
        }

        @Override
        public void characters(final CharSequence seq) throws SAXException {
            maybeFlushUndeclarations();
            super.characters(seq);
        }

        @Override
        public void characters(final char[] ch, final int start, final int len) throws SAXException {
            maybeFlushUndeclarations();
            super.characters(ch, start, len);
        }

        @Override
        public void endElement(final String ns, final String local, final String qname) throws SAXException {
            maybeFlushUndeclarations();
            super.endElement(ns, local, qname);
        }

        @Override
        public void endElement(final QName qname) throws SAXException {
            maybeFlushUndeclarations();
            super.endElement(qname);
        }

        /**
         * Emit namespace undeclarations for every ancestor namespace binding whose prefix is
         * not already declared by the root element itself.  Called before the first non-namespace
         * event on the root element (child, text, endElement) to ensure the nodes attach to the
         * root element node number in the MemTree.
         */
        private void maybeFlushUndeclarations() {
            if (rootSeen && !undeclsFlushed) {
                undeclsFlushed = true;
                for (final Map.Entry<String, String> entry : ancestorNS.entrySet()) {
                    final String prefix = entry.getKey();
                    if (!rootOwnPrefixes.contains(prefix)) {
                        try {
                            super.addNamespaceNode(
                                    new QName(prefix, XMLConstants.NULL_NS_URI, XMLConstants.XMLNS_ATTRIBUTE));
                        } catch (final SAXException e) {
                            // Silently skip — undeclaration is best-effort; worst case
                            // in-scope-prefixes() returns a superset which is handled by cleanup.
                        }
                    }
                }
            }
        }
    }
}
