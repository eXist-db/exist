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
package org.exist.xquery.xquf;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.ElementImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.*;
import org.exist.xquery.functions.fn.FunInScopePrefixes;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import java.util.*;
import java.util.stream.Collectors;

/**
 * W3C XQuery Update Facility 3.0 - transform (copy-modify) expression.
 *
 * <pre>
 * TransformExpr ::= "copy" CopyBinding ("," CopyBinding)* "modify" ExprSingle "return" ExprSingle
 * CopyBinding   ::= "$" VarName ":=" ExprSingle
 * </pre>
 *
 * Also supports the shorthand: transform with { ... } (XQuery Update 3.0 extension)
 */
public class XQUFTransformExpr extends AbstractExpression {

    private final List<CopyBinding> copyBindings;
    private final Expression modifyExpr;
    private final Expression returnExpr;

    public XQUFTransformExpr(final XQueryContext context, final List<CopyBinding> copyBindings,
                              final Expression modifyExpr, final Expression returnExpr) {
        super(context);
        this.copyBindings = copyBindings;
        this.modifyExpr = modifyExpr;
        this.returnExpr = returnExpr;
    }

    public static class CopyBinding {
        private final QName varName;
        private final Expression sourceExpr;

        public CopyBinding(final QName varName, final Expression sourceExpr) {
            this.varName = varName;
            this.sourceExpr = sourceExpr;
        }

        public QName getVarName() {
            return varName;
        }

        public Expression getSourceExpr() {
            return sourceExpr;
        }
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);

        // Mark variable scope so copy variables are visible to modify/return expressions
        final LocalVariable mark = context.markLocalVariables(false);
        try {
            // Copy binding source expressions are non-updating contexts (XUST0001)
            final AnalyzeContextInfo sourceInfo = new AnalyzeContextInfo(contextInfo);
            sourceInfo.addFlag(NON_UPDATING_CONTEXT);
            for (final CopyBinding binding : copyBindings) {
                binding.sourceExpr.analyze(sourceInfo);

                // Declare each copy variable so it's visible during analysis of modify/return
                final LocalVariable var = new LocalVariable(binding.varName);
                var.setSequenceType(new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE));
                context.declareVariableBinding(var);
            }

            // Modify clause is in an updating context (updating expressions allowed)
            final AnalyzeContextInfo modifyInfo = new AnalyzeContextInfo(contextInfo);
            modifyInfo.addFlag(IN_UPDATE);
            modifyInfo.removeFlag(NON_UPDATING_CONTEXT);
            modifyExpr.analyze(modifyInfo);

            // XUST0002: modify clause must be updating or vacuous (empty sequence)
            if (!modifyExpr.isUpdating() && !modifyExpr.isVacuous()) {
                throw new XPathException(this, ErrorCodes.XUST0002,
                        "The modify clause of a copy-modify expression must contain an updating expression.");
            }

            // Return clause is a non-updating context
            final AnalyzeContextInfo returnInfo = new AnalyzeContextInfo(contextInfo);
            returnInfo.addFlag(NON_UPDATING_CONTEXT);
            returnExpr.analyze(returnInfo);
        } finally {
            context.popLocalVariables(mark);
        }
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
        }

        final Sequence ctxSeq = contextItem != null ? contextItem.toSequence() : contextSequence;

        // Save the outer PUL and create a new scope for the modify clause
        final PendingUpdateList outerPul = context.getPendingUpdateList();
        final PendingUpdateList innerPul = new PendingUpdateList();
        context.setPendingUpdateList(innerPul);

        try {
            // Push a new variable scope for the copy bindings
            final LocalVariable mark = context.markLocalVariables(false);

            try {
                // Evaluate each copy binding and deep-copy the source node
                final List<Node> copiedRoots = new ArrayList<>();
                for (final CopyBinding binding : copyBindings) {
                    final Sequence sourceSeq = binding.sourceExpr.eval(ctxSeq, null);
                    if (sourceSeq.getItemCount() != 1 || !Type.subTypeOf(sourceSeq.itemAt(0).getType(), Type.NODE)) {
                        throw new XPathException(this, ErrorCodes.XUTY0013,
                                "Source expression of copy binding must return a single node.");
                    }

                    // Deep copy the source node into an in-memory tree
                    final Sequence copied = deepCopyNode(sourceSeq);

                    // Track the copied root node for XUDY0014 checking
                    copiedRoots.add(((NodeValue) copied.itemAt(0)).getNode());

                    // Bind the variable to the copied node
                    final LocalVariable var = new LocalVariable(binding.varName);
                    var.setSequenceType(new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE));
                    var.setValue(copied);
                    context.declareVariableBinding(var);
                }

                // Evaluate the modify clause - updates go to innerPul
                modifyExpr.eval(ctxSeq, null);

                // XUDY0014: check that all update targets are descendants of copied nodes
                innerPul.checkTransformTargets(copiedRoots, this);

                // Apply the inner PUL to the copied nodes (in-memory updates)
                innerPul.apply(context);

                // Evaluate and return the return clause
                final Sequence result = returnExpr.eval(ctxSeq, null);

                if (context.getProfiler().isEnabled()) {
                    context.getProfiler().end(this, "", result);
                }

                return result;
            } finally {
                context.popLocalVariables(mark);
            }
        } finally {
            // Restore the outer PUL
            context.setPendingUpdateList(outerPul);
        }
    }

    /**
     * Deep copy a node sequence into an in-memory tree.
     * Reuses the serialization approach from Modification.deepCopy().
     */
    private Sequence deepCopyNode(final Sequence inSeq) throws XPathException {
        context.pushDocumentContext();
        final MemTreeBuilder builder = context.getDocumentBuilder();
        final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(this, builder);
        final Serializer serializer = context.getBroker().borrowSerializer();
        serializer.setReceiver(receiver);

        try {
            final Sequence out = new ValueSequence();
            for (final SequenceIterator i = inSeq.iterate(); i.hasNext(); ) {
                Item item = i.nextItem();
                if (item.getType() == Type.DOCUMENT) {
                    item = copyDocumentItem(item, builder, serializer, receiver);
                } else if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    item = copyNodeItem(item, builder, serializer, receiver);
                }
                out.add(item);
            }
            return out;
        } catch (final SAXException e) {
            throw new XPathException(this, e.getMessage(), e);
        } finally {
            context.getBroker().returnSerializer(serializer);
            context.popDocumentContext();
        }
    }

    /**
     * Copy a document node: copy ALL its children (comments, PIs, elements)
     * to preserve the complete document structure.
     */
    private Item copyDocumentItem(final Item item, final MemTreeBuilder builder,
            final Serializer serializer, final DocumentBuilderReceiver receiver) throws SAXException {
        if (((NodeValue) item).getImplementationType() == NodeValue.PERSISTENT_NODE) {
            final NodeProxy docProxy = (NodeProxy) item;
            serializer.toReceiver(docProxy, false, false);
        } else {
            final Document docNode = (Document) item;
            Node child = docNode.getFirstChild();
            while (child != null) {
                if (child instanceof org.exist.dom.memtree.NodeImpl) {
                    ((org.exist.dom.memtree.NodeImpl) child).copyTo(context.getBroker(), receiver);
                }
                child = child.getNextSibling();
            }
        }
        return builder.getDocument();
    }

    /**
     * Copy a non-document node, preserving or stripping namespaces per the
     * static copy-namespaces mode.
     */
    private Item copyNodeItem(final Item item, final MemTreeBuilder builder,
            final Serializer serializer, final DocumentBuilderReceiver receiver) throws SAXException, XPathException {
        // Collect inherited namespace bindings from the source node's ancestors
        // BEFORE copying, so we can add them to the copied element afterwards.
        // This implements W3C copy-namespaces preserve semantics.
        final Map<String, String> inheritedNs;
        if (item.getType() == Type.ELEMENT && context.preserveNamespaces()) {
            inheritedNs = collectInheritedNamespaces((NodeValue) item);
        } else {
            inheritedNs = null;
        }

        // Always serialize through MemTreeBuilder to create a true independent copy.
        // Using NodeImpl.deepCopy() would mutate the original in place, causing
        // the source variable and copy variable to share the same document.
        final int last = builder.getDocument().getLastNode();
        if (((NodeValue) item).getImplementationType() == NodeValue.PERSISTENT_NODE) {
            final NodeProxy p = (NodeProxy) item;
            serializer.toReceiver(p, false, false);
        } else {
            final org.exist.dom.memtree.NodeImpl memNode = (org.exist.dom.memtree.NodeImpl) item;
            memNode.copyTo(context.getBroker(), receiver);
        }
        final Item copied = item.getType() == Type.ATTRIBUTE
                ? builder.getDocument().getLastAttr()
                : builder.getDocument().getNode(last + 1);

        // Add inherited namespace bindings to the copied root element
        if (inheritedNs != null && !inheritedNs.isEmpty()
                && copied instanceof org.exist.dom.memtree.NodeImpl) {
            addInheritedNamespaces(builder.getDocument(),
                    ((org.exist.dom.memtree.NodeImpl) copied).getNodeNumber(), inheritedNs);
        }

        // W3C copy-namespaces no-preserve: strip namespace declarations
        // not used by element/attribute names from the copied subtree
        if (!context.preserveNamespaces()
                && copied instanceof org.exist.dom.memtree.NodeImpl
                && ((org.exist.dom.memtree.NodeImpl) copied).getNode().getNodeType() == Node.ELEMENT_NODE) {
            builder.getDocument().stripUnusedNamespacesInSubtree(
                    ((org.exist.dom.memtree.NodeImpl) copied).getNodeNumber());
        }
        return copied;
    }

    @Override
    public int returnsType() {
        return returnExpr.returnsType();
    }

    @Override
    public Cardinality getCardinality() {
        return returnExpr.getCardinality();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        for (final CopyBinding binding : copyBindings) {
            binding.sourceExpr.resetState(postOptimization);
        }
        modifyExpr.resetState(postOptimization);
        returnExpr.resetState(postOptimization);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("copy ");
        for (int i = 0; i < copyBindings.size(); i++) {
            if (i > 0) {
                dumper.display(", ");
            }
            final CopyBinding binding = copyBindings.get(i);
            dumper.display("$").display(binding.varName.getLocalPart()).display(" := ");
            binding.sourceExpr.dump(dumper);
        }
        dumper.display(" modify ");
        modifyExpr.dump(dumper);
        dumper.display(" return ");
        returnExpr.dump(dumper);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("copy ");
        for (int i = 0; i < copyBindings.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            final CopyBinding binding = copyBindings.get(i);
            sb.append("$").append(binding.varName.getLocalPart()).append(" := ");
            sb.append(binding.sourceExpr.toString());
        }
        sb.append(" modify ");
        sb.append(modifyExpr.toString());
        sb.append(" return ");
        sb.append(returnExpr.toString());
        return sb.toString();
    }

    /**
     * Collect namespace bindings inherited from ancestor elements of the given node.
     * These are namespaces in scope on the node via inheritance but not declared on the node itself.
     */
    private Map<String, String> collectInheritedNamespaces(final NodeValue nodeValue) {
        final Map<String, String> inherited = new LinkedHashMap<>();
        Node parent = nodeValue.getNode().getParentNode();
        // Walk up ancestors collecting namespace bindings
        final Deque<org.w3c.dom.Element> ancestors = new ArrayDeque<>();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            ancestors.push((org.w3c.dom.Element) parent);
            parent = parent.getParentNode();
        }
        // Process top-down so closer ancestors override
        while (!ancestors.isEmpty()) {
            FunInScopePrefixes.collectNamespacePrefixes(ancestors.pop(), inherited);
        }
        // Remove "xml" prefix — always implicitly in scope
        inherited.remove("xml");
        // Remove any bindings that are already declared on the element itself
        final Map<String, String> selfNs = new LinkedHashMap<>();
        FunInScopePrefixes.collectNamespacePrefixes((org.w3c.dom.Element) nodeValue.getNode(), selfNs);
        for (final String prefix : selfNs.keySet()) {
            inherited.remove(prefix);
        }
        return inherited;
    }

    /**
     * Add inherited namespace bindings to a copied element node.
     * Only adds bindings not already declared on the element.
     */
    private void addInheritedNamespaces(final org.exist.dom.memtree.DocumentImpl doc,
                                         final int elementNodeNum,
                                         final Map<String, String> inheritedNs) {
        for (final Map.Entry<String, String> entry : inheritedNs.entrySet()) {
            final String prefix = entry.getKey();
            final String uri = entry.getValue();
            if (uri != null && !uri.isEmpty()) {
                final QName nsQName = new QName(prefix, uri, XMLConstants.XMLNS_ATTRIBUTE);
                doc.addNamespace(elementNodeNum, nsQName);
            }
        }
    }
}
