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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.indexing.IndexController;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.Namespaces;
import org.exist.collections.ManagedLocks;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.DocumentTriggers;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.NodeListImpl;
import org.exist.dom.QName;
import org.exist.dom.persistent.*;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.NotificationService;
import org.exist.storage.UpdateListener;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import java.util.*;

/**
 * W3C XQuery Update Facility 3.0 Pending Update List.
 *
 * Accumulates update primitives during query evaluation and applies them
 * atomically at snapshot boundaries (end of outermost expression evaluation).
 *
 * @see <a href="https://www.w3.org/TR/xquery-update-30/">XQuery Update Facility 3.0</a>
 */
public class PendingUpdateList {

    private static final Logger LOG = LogManager.getLogger(PendingUpdateList.class);

    private final List<UpdatePrimitive> primitives = new ArrayList<>();

    /**
     * Add a primitive to this PUL.
     */
    public void addPrimitive(final UpdatePrimitive primitive) {
        primitives.add(primitive);
    }

    /**
     * Merge another PUL into this one (for combining sub-expression PULs).
     */
    public void merge(final PendingUpdateList other) {
        primitives.addAll(other.primitives);
    }

    /**
     * @return true if this PUL contains no primitives
     */
    public boolean isEmpty() {
        return primitives.isEmpty();
    }

    /**
     * @return the number of primitives in this PUL
     */
    public int size() {
        return primitives.size();
    }

    /**
     * Check that all target nodes in this PUL are descendants of (or equal to)
     * one of the given copied root nodes. Used for XUDY0014 in transform expressions.
     *
     * @param copiedRoots the root nodes created by copy bindings
     * @param expr        the expression for error reporting
     * @throws XPathException if any target is not a descendant of a copy root
     */
    public void checkTransformTargets(final List<Node> copiedRoots, final Expression expr) throws XPathException {
        for (final UpdatePrimitive p : primitives) {
            final Node target = p.getTargetNode();
            if (!isDescendantOfAny(target, copiedRoots)) {
                throw new XPathException(expr, ErrorCodes.XUDY0014,
                        "Target node of update in transform expression was not created by the copy clause.");
            }
        }
    }

    private static boolean isDescendantOfAny(final Node target, final List<Node> roots) {
        for (final Node root : roots) {
            if (isDescendantOrSelf(target, root)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDescendantOrSelf(final Node node, final Node ancestor) {
        // Check self first (handles standalone attribute copies where owner element is null)
        if (nodesAreSame(node, ancestor)) {
            return true;
        }
        Node current = node;
        // For attribute nodes, start from the owner element
        if (current.getNodeType() == Node.ATTRIBUTE_NODE) {
            current = ((Attr) current).getOwnerElement();
            if (current == null) {
                return false;
            }
        }
        while (current != null) {
            if (nodesAreSame(current, ancestor)) {
                return true;
            }
            current = current.getParentNode();
        }
        // Fallback for memtree: getParentNode() returns null when the parent is a
        // document that is not "explicitly created" (see NodeImpl line 232).
        // Handle two cases:
        // 1. Ancestor is a document node: check if target belongs to that document
        // 2. Ancestor is an element: check if both share the same document
        //    (the parent walk stopped at null because the doc wasn't explicitly created)
        if (node instanceof org.exist.dom.memtree.NodeImpl memNode
                && ancestor instanceof org.exist.dom.memtree.NodeImpl memAncestor) {
            if (ancestor.getNodeType() == Node.DOCUMENT_NODE
                    && ancestor instanceof org.exist.dom.memtree.DocumentImpl memDoc) {
                return memNode.getOwnerDocument() == memDoc;
            }
            // Both are elements in the same (non-explicitly-created) document —
            // verify ancestor's nodeNumber is actually an ancestor of node's
            if (memNode.getOwnerDocument() == memAncestor.getOwnerDocument()) {
                return isMemtreeAncestor(memNode, memAncestor);
            }
        }
        return false;
    }

    /**
     * Walk up the memtree parent chain using the internal parentNodeFor array,
     * bypassing the isExplicitlyCreated check in NodeImpl.getParentNode().
     */
    private static boolean isMemtreeAncestor(final org.exist.dom.memtree.NodeImpl node,
                                              final org.exist.dom.memtree.NodeImpl ancestor) {
        final org.exist.dom.memtree.DocumentImpl doc = node.getOwnerDocument();
        final int ancestorNum = ancestor.getNodeNumber();
        int current = node.getNodeNumber();
        while (current >= 0) {
            if (current == ancestorNum) {
                return true;
            }
            current = doc.getParentNodeFor(current);
        }
        return false;
    }

    private static boolean nodesAreSame(final Node a, final Node b) {
        if (a == b) {
            return true;
        }
        // memtree nodes: compare by document identity + nodeNumber
        if (a instanceof org.exist.dom.memtree.NodeImpl && b instanceof org.exist.dom.memtree.NodeImpl) {
            final org.exist.dom.memtree.NodeImpl memA = (org.exist.dom.memtree.NodeImpl) a;
            final org.exist.dom.memtree.NodeImpl memB = (org.exist.dom.memtree.NodeImpl) b;
            return memA.getOwnerDocument() == memB.getOwnerDocument()
                    && memA.getNodeNumber() == memB.getNodeNumber();
        }
        return false;
    }

    /**
     * Check the PUL for conflicts per the W3C spec before applying.
     *
     * Checks:
     * - XUDY0015: multiple renames on same node
     * - XUDY0016: multiple replace-node on same node
     * - XUDY0017: multiple replace-value on same node
     * - XUDY0021: duplicate attribute names on an element after updates
     * - XUDY0023: new namespace binding conflicts with existing binding on element
     * - XUDY0024: new namespace bindings from different primitives conflict with each other
     * - XUDY0031: multiple fn:put with same URI
     *
     * @throws XPathException if conflicting primitives are found
     */
    public void checkConflicts() throws XPathException {
        // Track nodes that have been targeted by rename, replaceNode, replaceValue.
        // Use nodeKey() string representation instead of Node identity because
        // persistent StoredNode objects don't override equals()/hashCode(), so
        // different wrapper objects for the same underlying node need to be
        // detected as duplicates.
        final Set<String> renameTargets = new HashSet<>();
        final Set<String> replaceNodeTargets = new HashSet<>();
        final Set<String> replaceValueTargets = new HashSet<>();
        final Set<String> putUris = new HashSet<>();

        for (final UpdatePrimitive p : primitives) {
            final Expression expr = p.getSourceExpression();

            switch (p.getType()) {
                case RENAME:
                    if (!renameTargets.add(nodeKey(p.getTargetNode()))) {
                        throw new XPathException(expr, ErrorCodes.XUDY0015,
                                "Multiple rename primitives applied to the same target node.");
                    }
                    break;

                case REPLACE_NODE:
                    if (!replaceNodeTargets.add(nodeKey(p.getTargetNode()))) {
                        throw new XPathException(expr, ErrorCodes.XUDY0016,
                                "Multiple replace node primitives applied to the same target node.");
                    }
                    break;

                case REPLACE_VALUE:
                    if (!replaceValueTargets.add(nodeKey(p.getTargetNode()))) {
                        throw new XPathException(expr, ErrorCodes.XUDY0017,
                                "Multiple replace value primitives applied to the same target node.");
                    }
                    break;

                case PUT:
                    if (!putUris.add(p.getUri())) {
                        throw new XPathException(expr, ErrorCodes.XUDY0031,
                                "Multiple fn:put primitives with the same URI: " + p.getUri());
                    }
                    break;

                default:
                    break;
            }
        }

        // Check XUDY0021 (duplicate attributes), XUDY0023 (conflict with existing ns),
        // and XUDY0024 (conflict between new ns bindings)
        checkAttributeAndNamespaceConflicts();
    }

    /**
     * For each element affected by attribute-modifying operations,
     * compute the resulting attribute set and check for:
     * - XUDY0021: duplicate attribute QNames
     * - XUDY0023: new namespace binding conflicts with existing element namespace binding
     * - XUDY0024: new namespace bindings from different operations conflict with each other
     */
    private void checkAttributeAndNamespaceConflicts() throws XPathException {
        // Group attribute operations by target element.
        // We use a string key for node identity because persistent DOM proxies
        // may return different Java objects for the same underlying node.
        final Map<String, ElementAttrState> elementStates = new LinkedHashMap<>();

        for (final UpdatePrimitive p : primitives) {
            switch (p.getType()) {
                case INSERT_INTO:
                case INSERT_INTO_AS_FIRST:
                case INSERT_INTO_AS_LAST: {
                    // Target is the element; content may include attributes
                    final Node target = p.getTargetNode();
                    if (target.getNodeType() == Node.ELEMENT_NODE) {
                        final ElementAttrState state = getOrCreateState(elementStates, target);
                        addContentAttributes(state, p);
                    }
                    break;
                }

                case INSERT_BEFORE:
                case INSERT_AFTER: {
                    // For attribute insertion before/after, the target's parent is the element
                    final Node target = p.getTargetNode();
                    final Node parent = target.getNodeType() == Node.ATTRIBUTE_NODE
                            ? ((Attr) target).getOwnerElement()
                            : target.getParentNode();
                    if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
                        final ElementAttrState state = getOrCreateState(elementStates, parent);
                        addContentAttributes(state, p);
                    }
                    break;
                }

                case INSERT_ATTRIBUTES: {
                    // Target is the element
                    final Node target = p.getTargetNode();
                    if (target.getNodeType() == Node.ELEMENT_NODE) {
                        final ElementAttrState state = getOrCreateState(elementStates, target);
                        addContentAttributes(state, p);
                    }
                    break;
                }

                case REPLACE_NODE: {
                    final Node target = p.getTargetNode();
                    if (target.getNodeType() == Node.ATTRIBUTE_NODE) {
                        final Node parent = ((Attr) target).getOwnerElement();
                        if (parent != null) {
                            final ElementAttrState state = getOrCreateState(elementStates, parent);
                            // Mark the old attribute as removed
                            state.removedAttrs.add(getExpandedName(target));
                            // Add new attributes from the replacement content
                            addContentAttributes(state, p);
                        }
                    }
                    break;
                }

                case DELETE: {
                    final Node target = p.getTargetNode();
                    if (target.getNodeType() == Node.ATTRIBUTE_NODE) {
                        final Node parent = ((Attr) target).getOwnerElement();
                        if (parent != null) {
                            final ElementAttrState state = getOrCreateState(elementStates, parent);
                            state.removedAttrs.add(getExpandedName(target));
                        }
                    }
                    break;
                }

                case RENAME: {
                    final Node target = p.getTargetNode();
                    final QName newName = p.getNewName();
                    if (target.getNodeType() == Node.ATTRIBUTE_NODE && newName != null) {
                        final Node parent = ((Attr) target).getOwnerElement();
                        if (parent != null) {
                            final ElementAttrState state = getOrCreateState(elementStates, parent);
                            state.removedAttrs.add(getExpandedName(target));
                            state.addedAttrs.add(new ExpandedName(
                                    newName.getNamespaceURI() == null ? "" : newName.getNamespaceURI(),
                                    newName.getLocalPart(),
                                    newName.getPrefix() == null ? "" : newName.getPrefix()));
                            addNamespaceBinding(state, newName.getPrefix(), newName.getNamespaceURI(), p);
                        }
                    } else if (target.getNodeType() == Node.ELEMENT_NODE && newName != null) {
                        // Renaming an element also introduces a namespace binding
                        final ElementAttrState state = getOrCreateState(elementStates, target);
                        addNamespaceBinding(state, newName.getPrefix(), newName.getNamespaceURI(), p);
                    }
                    break;
                }

                default:
                    break;
            }
        }

        // Now check each affected element
        for (final Map.Entry<String, ElementAttrState> entry : elementStates.entrySet()) {
            final Node element = entry.getValue().elementNode;
            final ElementAttrState state = entry.getValue();

            // Build the final attribute set: existing attrs - removed + added
            final Set<ExpandedName> finalAttrs = new HashSet<>();

            // Add existing attributes
            final org.w3c.dom.NamedNodeMap existingAttrs = element.getAttributes();
            if (existingAttrs != null) {
                for (int i = 0; i < existingAttrs.getLength(); i++) {
                    final Node attr = existingAttrs.item(i);
                    // Skip xmlns declarations
                    if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) {
                        continue;
                    }
                    final ExpandedName name = getExpandedName(attr);
                    if (!state.removedAttrs.contains(name)) {
                        finalAttrs.add(name);
                    }
                }
            }

            // Check added attributes for duplicates with existing and with each other
            for (final ExpandedName addedName : state.addedAttrs) {
                if (!finalAttrs.add(addedName)) {
                    // XUDY0021: duplicate attribute name
                    throw new XPathException(state.firstExpr, ErrorCodes.XUDY0021,
                            "Duplicate attribute name after update: " +
                                    (addedName.prefix.isEmpty() ? "" : addedName.prefix + ":") +
                                    addedName.localName);
                }
            }

            // Check namespace bindings
            // First, collect existing in-scope namespace bindings for this element
            final Map<String, String> existingNsBindings = collectInScopeNamespaces(element);

            // XUDY0023: check new bindings against existing bindings
            for (final NsBinding binding : state.newNsBindings) {
                if (binding.prefix == null || binding.prefix.isEmpty()) {
                    continue; // default namespace doesn't conflict for attributes
                }
                if (binding.uri == null || binding.uri.isEmpty()) {
                    continue; // empty URI doesn't conflict
                }
                final String existingUri = existingNsBindings.get(binding.prefix);
                if (existingUri != null && !existingUri.equals(binding.uri)) {
                    throw new XPathException(binding.expr.getSourceExpression(), ErrorCodes.XUDY0023,
                            "New namespace binding for prefix '" + binding.prefix +
                                    "' with URI '" + binding.uri +
                                    "' conflicts with existing binding to URI '" + existingUri + "'.");
                }
            }

            // XUDY0024: check new bindings against each other
            final Map<String, String> newBindingsMap = new HashMap<>();
            for (final NsBinding binding : state.newNsBindings) {
                if (binding.prefix == null || binding.prefix.isEmpty()) {
                    continue;
                }
                if (binding.uri == null || binding.uri.isEmpty()) {
                    continue;
                }
                final String prevUri = newBindingsMap.put(binding.prefix, binding.uri);
                if (prevUri != null && !prevUri.equals(binding.uri)) {
                    throw new XPathException(binding.expr.getSourceExpression(), ErrorCodes.XUDY0024,
                            "Conflicting namespace bindings for prefix '" + binding.prefix +
                                    "': URI '" + prevUri + "' vs '" + binding.uri + "'.");
                }
            }
        }
    }

    /**
     * State tracking for attribute/namespace changes to a single element.
     */
    private static class ElementAttrState {
        final Node elementNode;
        final Set<ExpandedName> removedAttrs = new HashSet<>();
        final List<ExpandedName> addedAttrs = new ArrayList<>();
        final List<NsBinding> newNsBindings = new ArrayList<>();
        Expression firstExpr;

        ElementAttrState(final Node elementNode) {
            this.elementNode = elementNode;
        }
    }

    /**
     * A namespace prefix-to-URI binding introduced by an update primitive.
     */
    private static class NsBinding {
        final String prefix;
        final String uri;
        final UpdatePrimitive expr;

        NsBinding(final String prefix, final String uri, final UpdatePrimitive expr) {
            this.prefix = prefix;
            this.uri = uri;
            this.expr = expr;
        }
    }

    /**
     * Expanded name (namespace URI + local name) for attribute identity.
     */
    private static class ExpandedName {
        final String namespaceURI;
        final String localName;
        final String prefix;

        ExpandedName(final String namespaceURI, final String localName, final String prefix) {
            this.namespaceURI = namespaceURI == null ? "" : namespaceURI;
            this.localName = localName;
            this.prefix = prefix == null ? "" : prefix;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof ExpandedName)) return false;
            final ExpandedName that = (ExpandedName) o;
            return namespaceURI.equals(that.namespaceURI) && localName.equals(that.localName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(namespaceURI, localName);
        }
    }

    private ElementAttrState getOrCreateState(final Map<String, ElementAttrState> states, final Node element) {
        return states.computeIfAbsent(nodeKey(element), k -> new ElementAttrState(element));
    }

    /**
     * Create a stable identity key for a DOM node that works for both
     * in-memory (memtree) and persistent nodes.
     * Both may create different Java objects for the same underlying node,
     * so we can't rely on object identity.
     */
    private static String nodeKey(final Node node) {
        if (node instanceof org.exist.dom.memtree.NodeImpl) {
            final org.exist.dom.memtree.NodeImpl memNode = (org.exist.dom.memtree.NodeImpl) node;
            return "mem:" + System.identityHashCode(memNode.getOwnerDocument()) + ":" + memNode.getNodeNumber();
        } else if (node instanceof IStoredNode) {
            final IStoredNode<?> storedNode = (IStoredNode<?>) node;
            return "db:" + storedNode.getOwnerDocument().getDocId() + ":" + storedNode.getNodeId();
        } else if (node instanceof NodeProxy) {
            final NodeProxy proxy = (NodeProxy) node;
            return "db:" + proxy.getOwnerDocument().getDocId() + ":" + proxy.getNodeId();
        } else {
            // Fallback: use identity hash
            return "id:" + System.identityHashCode(node);
        }
    }

    private static ExpandedName getExpandedName(final Node node) {
        return new ExpandedName(
                node.getNamespaceURI(),
                node.getLocalName() != null ? node.getLocalName() : node.getNodeName(),
                node.getPrefix());
    }

    /**
     * Extract attribute nodes from the content sequence of an update primitive
     * and add them to the element state.
     */
    private void addContentAttributes(final ElementAttrState state, final UpdatePrimitive p) throws XPathException {
        if (state.firstExpr == null) {
            state.firstExpr = p.getSourceExpression();
        }
        final Sequence content = p.getContent();
        if (content == null || content.isEmpty()) {
            return;
        }
        for (final SequenceIterator i = content.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (Type.subTypeOf(item.getType(), Type.ATTRIBUTE)) {
                final Node attrNode = ((NodeValue) item).getNode();
                final ExpandedName name = getExpandedName(attrNode);
                state.addedAttrs.add(name);
                addNamespaceBinding(state,
                        attrNode.getPrefix(),
                        attrNode.getNamespaceURI(),
                        p);
            } else if (Type.subTypeOf(item.getType(), Type.ELEMENT)) {
                // Collect namespace bindings from the inserted element subtree.
                // These must be checked against the target's in-scope namespaces
                // for XUDY0023 (namespace propagation conflicts).
                final Node elemNode = ((NodeValue) item).getNode();
                collectElementNamespaceBindings(elemNode, state, p);
            }
        }
    }

    /**
     * Collect namespace bindings from the top level of an inserted element for XUDY0023 checking.
     * Only the root element's bindings are collected — descendants with their own re-declarations
     * are handled by upd:propagateNamespace at each level individually.
     */
    private static void collectElementNamespaceBindings(final Node node, final ElementAttrState state,
                                                         final UpdatePrimitive p) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        // Element's own namespace
        addNamespaceBinding(state, node.getPrefix(), node.getNamespaceURI(), p);

        // Namespace declarations on this element
        if (node instanceof org.exist.dom.memtree.ElementImpl memElem) {
            final Map<String, String> nsMap = memElem.getNamespaceMap();
            for (final Map.Entry<String, String> e : nsMap.entrySet()) {
                addNamespaceBinding(state, e.getKey(), e.getValue(), p);
            }
        }

        // Namespace bindings from attributes
        final org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                final Node attr = attrs.item(i);
                if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) {
                    addNamespaceBinding(state, attr.getPrefix(), attr.getNamespaceURI(), p);
                }
            }
        }
    }

    /**
     * Add a namespace binding to the element state if the prefix is non-empty and URI is non-empty.
     */
    private static void addNamespaceBinding(final ElementAttrState state,
                                            final String prefix, final String uri,
                                            final UpdatePrimitive p) {
        if (prefix != null && !prefix.isEmpty() && uri != null && !uri.isEmpty()) {
            state.newNsBindings.add(new NsBinding(prefix, uri, p));
        }
    }

    /**
     * Collect the in-scope namespace bindings for an element node.
     */
    private static Map<String, String> collectInScopeNamespaces(final Node element) {
        final Map<String, String> nsBindings = new HashMap<>();

        // Walk up the ancestor chain to collect inherited namespace bindings
        Node current = element;
        while (current != null && current.getNodeType() == Node.ELEMENT_NODE) {
            // Check element's own namespace
            final String nsUri = current.getNamespaceURI();
            final String prefix = current.getPrefix();
            if (nsUri != null && !nsUri.isEmpty()) {
                final String p = prefix == null ? "" : prefix;
                nsBindings.putIfAbsent(p, nsUri);
            }

            // Check namespace declarations on this element
            if (current instanceof org.exist.dom.memtree.ElementImpl) {
                final Map<String, String> map = new LinkedHashMap<>();
                ((org.exist.dom.memtree.ElementImpl) current).getNamespaceMap(map);
                for (final Map.Entry<String, String> e : map.entrySet()) {
                    nsBindings.putIfAbsent(e.getKey(), e.getValue());
                }
            } else if (current instanceof ElementImpl) {
                final ElementImpl elemImpl =
                        (ElementImpl) current;
                if (elemImpl.declaresNamespacePrefixes()) {
                    for (final Iterator<String> iter = elemImpl.getPrefixes(); iter.hasNext(); ) {
                        final String p = iter.next();
                        nsBindings.putIfAbsent(p, elemImpl.getNamespaceForPrefix(p));
                    }
                }
            } else {
                // Generic DOM: check attributes for xmlns declarations
                final org.w3c.dom.NamedNodeMap attrs = current.getAttributes();
                if (attrs != null) {
                    for (int i = 0; i < attrs.getLength(); i++) {
                        final Node attr = attrs.item(i);
                        if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) {
                            final String attrLocal = attr.getLocalName();
                            final String p = XMLConstants.XMLNS_ATTRIBUTE.equals(attrLocal) ? "" : attrLocal;
                            nsBindings.putIfAbsent(p, attr.getNodeValue());
                        }
                    }
                }
            }

            current = current.getParentNode();
        }

        return nsBindings;
    }

    /**
     * Apply all accumulated update primitives.
     *
     * The W3C spec defines a specific application order:
     * 1. Insert before/after/into (merging compatible inserts)
     * 2. Rename
     * 3. Replace value
     * 4. Replace node
     * 5. Delete
     * 6. Put
     *
     * This method handles both persistent and in-memory nodes.
     *
     * @param context the XQuery context
     * @throws XPathException on update errors
     */
    public void apply(final XQueryContext context) throws XPathException {
        if (primitives.isEmpty()) {
            return;
        }

        checkConflicts();

        // Separate into persistent and in-memory primitives
        final List<UpdatePrimitive> persistentPrimitives = new ArrayList<>();
        final List<UpdatePrimitive> inMemoryPrimitives = new ArrayList<>();

        for (final UpdatePrimitive p : primitives) {
            if (isPersistentNode(p.getTargetNode())) {
                persistentPrimitives.add(p);
            } else {
                inMemoryPrimitives.add(p);
            }
        }

        // Apply in-memory updates (for copy-modify)
        if (!inMemoryPrimitives.isEmpty()) {
            applyInMemory(context, inMemoryPrimitives);
        }

        // Apply persistent updates
        if (!persistentPrimitives.isEmpty()) {
            applyPersistent(context, persistentPrimitives);
        }
    }

    /**
     * Apply updates to in-memory nodes (used in copy-modify expressions).
     */
    private void applyInMemory(final XQueryContext context, final List<UpdatePrimitive> prims) throws XPathException {
        // W3C XQuery Update Facility 3.0, Section 3.3.3 — Application order:
        // Phase 1: upd:insertInto, upd:insertAttributes, upd:replaceValue (non-element), upd:rename
        // Phase 2: upd:insertBefore, upd:insertAfter, upd:insertIntoAsFirst, upd:insertIntoAsLast
        // Phase 3: upd:replaceNode
        // Phase 4: upd:replaceElementContent (replaceValue on elements)
        // Phase 5: upd:delete
        final List<UpdatePrimitive> inserts = new ArrayList<>();
        final List<UpdatePrimitive> renames = new ArrayList<>();
        final List<UpdatePrimitive> replaceValues = new ArrayList<>();
        final List<UpdatePrimitive> replaceElementContents = new ArrayList<>();
        final List<UpdatePrimitive> replaceNodes = new ArrayList<>();
        final List<UpdatePrimitive> deletes = new ArrayList<>();

        for (final UpdatePrimitive p : prims) {
            switch (p.getType()) {
                case INSERT_INTO, INSERT_INTO_AS_FIRST, INSERT_INTO_AS_LAST,
                     INSERT_BEFORE, INSERT_AFTER, INSERT_ATTRIBUTES:
                    inserts.add(p);
                    break;
                case RENAME:
                    renames.add(p);
                    break;
                case REPLACE_VALUE:
                    // W3C spec distinguishes replaceValue (attr/text/comment/PI)
                    // from replaceElementContent (element) — different application phase
                    if (p.getTargetNode().getNodeType() == Node.ELEMENT_NODE) {
                        replaceElementContents.add(p);
                    } else {
                        replaceValues.add(p);
                    }
                    break;
                case REPLACE_NODE:
                    replaceNodes.add(p);
                    break;
                case DELETE:
                    deletes.add(p);
                    break;
                default:
                    break;
            }
        }

        // Collect elements targeted by replaceElementContent — per W3C spec,
        // replaceElementContent replaces ALL children, so inserts of non-attribute
        // children into these elements are redundant.
        final Set<String> replaceElementContentTargets = new HashSet<>();
        for (final UpdatePrimitive p : replaceElementContents) {
            replaceElementContentTargets.add(nodeKey(p.getTargetNode()));
        }

        // Sort inserts by type priority per W3C spec Section 3.3.3:
        // INSERT_INTO_AS_FIRST first, then BEFORE/AFTER/ATTRIBUTES/INTO, then INSERT_INTO_AS_LAST last.
        inserts.sort((a, b) -> {
            final int pa = insertPriority(a.getType());
            final int pb = insertPriority(b.getType());
            return Integer.compare(pa, pb);
        });
        for (final UpdatePrimitive p : inserts) {
            // Skip non-attribute inserts into elements whose content will be replaced
            if (!replaceElementContentTargets.isEmpty()) {
                final Node insertTarget = p.getTargetNode();
                final boolean isInsertIntoElement =
                        (p.getType() == UpdatePrimitive.Type.INSERT_INTO ||
                         p.getType() == UpdatePrimitive.Type.INSERT_INTO_AS_FIRST ||
                         p.getType() == UpdatePrimitive.Type.INSERT_INTO_AS_LAST) &&
                        insertTarget.getNodeType() == Node.ELEMENT_NODE;
                if (isInsertIntoElement && replaceElementContentTargets.contains(nodeKey(insertTarget))) {
                    continue;
                }
            }
            applyInMemoryInsert(p);
        }
        // Pre-capture original attr indices for Phase 3 replaceNode BEFORE Phase 1
        // renames, which can change attribute QNames and create ambiguity.
        // We capture the index here (before any renames or removals).
        final Map<UpdatePrimitive, Integer> attrReplaceIndices = new HashMap<>();
        for (final UpdatePrimitive p : replaceNodes) {
            if (p.getTargetNode().getNodeType() == Node.ATTRIBUTE_NODE) {
                attrReplaceIndices.put(p, ((org.exist.dom.memtree.AttrImpl) p.getTargetNode()).getNodeNumber());
            }
        }

        // Phase 1: renames and non-element replaceValues
        for (final UpdatePrimitive p : renames) {
            applyInMemoryRename(p);
        }
        for (final UpdatePrimitive p : replaceValues) {
            applyInMemoryReplaceValue(p);
        }
        // Phase 3: replaceNode — skip if the target's parent is targeted by
        // replaceElementContent (which will replace ALL children anyway)
        //
        // For attribute replaceNode: use pre-captured original indices and process
        // in descending index order. This handles two problems:
        // 1. Phase 1 renames can change attribute QNames, making name-based lookup ambiguous
        // 2. removeAttribute shifts the attr arrays, invalidating stored indices
        // By processing highest index first, each removal only shifts indices above
        // the removed position, which we've already processed.

        // Separate attribute and non-attribute replaceNodes
        final List<UpdatePrimitive> attrReplaceNodes = new java.util.ArrayList<>();
        final List<UpdatePrimitive> nonAttrReplaceNodes = new java.util.ArrayList<>();
        for (final UpdatePrimitive p : replaceNodes) {
            if (!replaceElementContentTargets.isEmpty()) {
                final Node replTarget = p.getTargetNode();
                final Node parent = replTarget.getNodeType() == Node.ATTRIBUTE_NODE
                        ? ((Attr) replTarget).getOwnerElement()
                        : replTarget.getParentNode();
                if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE
                        && replaceElementContentTargets.contains(nodeKey(parent))) {
                    continue;
                }
            }
            if (p.getTargetNode().getNodeType() == Node.ATTRIBUTE_NODE) {
                attrReplaceNodes.add(p);
            } else {
                nonAttrReplaceNodes.add(p);
            }
        }
        // Sort attribute replaces by descending original index
        attrReplaceNodes.sort((a, b) -> Integer.compare(
                attrReplaceIndices.getOrDefault(b, 0),
                attrReplaceIndices.getOrDefault(a, 0)));
        for (final UpdatePrimitive p : attrReplaceNodes) {
            applyInMemoryReplaceNode(p, attrReplaceIndices.get(p));
        }
        for (final UpdatePrimitive p : nonAttrReplaceNodes) {
            applyInMemoryReplaceNode(p, null);
        }
        // Phase 4: replaceElementContent (after replaceNode, so node references are still valid)
        // Apply in reverse document order to prevent cross-contamination when
        // insertChildren appends text nodes at the end of the flat array for empty elements.
        // Without reverse order, an appended text node for an earlier element may fall
        // in the positional subtree range of a later sibling element.
        replaceElementContents.sort((a, b) -> {
            final int aNum = ((org.exist.dom.memtree.NodeImpl) a.getTargetNode()).getNodeNumber();
            final int bNum = ((org.exist.dom.memtree.NodeImpl) b.getTargetNode()).getNodeNumber();
            return Integer.compare(bNum, aNum);  // reverse order
        });
        for (final UpdatePrimitive p : replaceElementContents) {
            applyInMemoryReplaceValue(p);
        }
        // Phase 5: deletes in reverse document order
        for (int i = deletes.size() - 1; i >= 0; i--) {
            applyInMemoryDelete(deletes.get(i));
        }

        // Per W3C XQuery Update Facility spec: after applying all updates,
        // merge adjacent text nodes and remove empty text nodes.
        // Collect all affected documents, tracking which had structural changes.
        final Set<org.exist.dom.memtree.DocumentImpl> affectedDocs = new HashSet<>();
        final Set<org.exist.dom.memtree.DocumentImpl> structurallyChanged = new HashSet<>();
        for (final UpdatePrimitive p : prims) {
            final org.exist.dom.memtree.NodeImpl target = (org.exist.dom.memtree.NodeImpl) p.getTargetNode();
            final org.exist.dom.memtree.DocumentImpl doc = getDocument(target);
            affectedDocs.add(doc);
            // Inserts, deletes, replaceNode, and replaceElementContent on elements
            // change tree structure. replaceValue on element nodes may call insertChildren
            // to add text nodes, which appends to the flat array and requires compact().
            if (p.getType() != UpdatePrimitive.Type.RENAME
                    && !(p.getType() == UpdatePrimitive.Type.REPLACE_VALUE
                         && p.getTargetNode().getNodeType() != Node.ELEMENT_NODE)) {
                structurallyChanged.add(doc);
            }
        }
        for (final org.exist.dom.memtree.DocumentImpl doc : affectedDocs) {
            doc.mergeAdjacentTextNodes();
            // Only compact documents with structural changes — compact rebuilds
            // the tree from scratch and would lose standalone attributes/nodes
            // that aren't children of the document node.
            if (structurallyChanged.contains(doc)) {
                doc.compact();
            }
        }
    }

    private void applyInMemoryInsert(final UpdatePrimitive p) throws XPathException {
        final org.exist.dom.memtree.NodeImpl target = (org.exist.dom.memtree.NodeImpl) p.getTargetNode();
        final org.exist.dom.memtree.DocumentImpl doc = getDocument(target);
        final Sequence content = p.getContent();
        if (content == null || content.isEmpty()) {
            return;
        }

        switch (p.getType()) {
            case INSERT_INTO, INSERT_INTO_AS_LAST:
                doc.insertChildren(target.getNodeNumber(), content, false);
                break;
            case INSERT_INTO_AS_FIRST:
                doc.insertChildren(target.getNodeNumber(), content, true);
                break;
            case INSERT_BEFORE:
                doc.insertSiblings(target.getNodeNumber(), content, true);
                break;
            case INSERT_AFTER:
                doc.insertSiblings(target.getNodeNumber(), content, false);
                break;
            case INSERT_ATTRIBUTES:
                doc.insertAttributes(target.getNodeNumber(), content, false);
                break;
            default:
                break;
        }
    }

    /**
     * Validate content constraints for node values per the XML specification.
     * Called during PUL application for replace value of node.
     *
     * @param nodeType the type of the target node
     * @param value the new value to validate
     * @param expr the source expression for error reporting
     * @throws XPathException if the value violates XML constraints
     */
    private static void validateNodeContent(final short nodeType, final String value,
                                            final Expression expr) throws XPathException {
        switch (nodeType) {
            case Node.COMMENT_NODE:
                // XML spec: comment content must not contain "--" or end with "-"
                if (value.contains("--") || value.endsWith("-")) {
                    throw new XPathException(expr, ErrorCodes.XQDY0072,
                            "Comment content must not contain '--' or end with '-'.");
                }
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                // XML spec: PI content must not contain "?>"
                if (value.contains("?>")) {
                    throw new XPathException(expr, ErrorCodes.XQDY0026,
                            "Processing instruction content must not contain '?>'.");
                }
                break;
            default:
                break;
        }
    }

    /**
     * Atomize a sequence and join the resulting string values with a single space.
     * Per W3C XQuery Update Facility spec Section 2.4.4:
     * "The string value is computed by atomizing the expression and joining
     * the resulting values with a single space separator."
     */
    public static String atomizeAndJoin(final Sequence content) throws XPathException {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (content.getItemCount() == 1) {
            return content.itemAt(0).atomize().getStringValue();
        }
        final StringBuilder sb = new StringBuilder();
        for (final SequenceIterator i = content.iterate(); i.hasNext(); ) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(i.nextItem().atomize().getStringValue());
        }
        return sb.toString();
    }

    /**
     * Get the memtree DocumentImpl for a node. Handles the case where the node
     * IS the document node (getOwnerDocument() returns null for document nodes).
     */
    private static org.exist.dom.memtree.DocumentImpl getDocument(final org.exist.dom.memtree.NodeImpl node) {
        if (node instanceof org.exist.dom.memtree.DocumentImpl) {
            return (org.exist.dom.memtree.DocumentImpl) node;
        }
        return node.getOwnerDocument();
    }

    private void applyInMemoryRename(final UpdatePrimitive p) throws XPathException {
        final org.exist.dom.memtree.NodeImpl target = (org.exist.dom.memtree.NodeImpl) p.getTargetNode();
        final org.exist.dom.memtree.DocumentImpl doc = getDocument(target);
        if (target.getNodeType() == Node.ATTRIBUTE_NODE) {
            // For attribute nodes, getNodeNumber() returns an index into the attr arrays
            doc.renameAttribute(target.getNodeNumber(), p.getNewName());
        } else {
            doc.renameNode(target.getNodeNumber(), p.getNewName());
        }
    }

    private void applyInMemoryReplaceValue(final UpdatePrimitive p) throws XPathException {
        final org.exist.dom.memtree.NodeImpl target = (org.exist.dom.memtree.NodeImpl) p.getTargetNode();
        final org.exist.dom.memtree.DocumentImpl doc = getDocument(target);
        // Per W3C spec: atomize content, join with single space separator
        final String value = atomizeAndJoin(p.getContent());

        // Validate content constraints per XML spec
        validateNodeContent(target.getNodeType(), value, p.getSourceExpression());

        if (target.getNodeType() == Node.ATTRIBUTE_NODE) {
            // For attribute nodes, getNodeNumber() returns an index into the attr arrays
            doc.replaceAttributeValue(target.getNodeNumber(), value);
        } else {
            doc.replaceValue(target.getNodeNumber(), value);
        }
    }

    /**
     * @param preCapIndex for attribute targets, the original attr array index captured
     *                    before Phase 1 renames. Attribute replaces are processed in
     *                    descending index order so each removeAttribute only shifts
     *                    indices above the removed position (already processed).
     *                    null for non-attribute targets.
     */
    private void applyInMemoryReplaceNode(final UpdatePrimitive p, final Integer preCapIndex) throws XPathException {
        final org.exist.dom.memtree.NodeImpl target = (org.exist.dom.memtree.NodeImpl) p.getTargetNode();
        final org.exist.dom.memtree.DocumentImpl doc = getDocument(target);
        if (target.getNodeType() == Node.ATTRIBUTE_NODE && preCapIndex != null) {
            // For attribute nodes: use pre-captured index directly.
            // Attribute replaces are sorted by descending index, so removeAttribute
            // on this index won't affect any remaining (lower) indices.
            final org.exist.dom.memtree.AttrImpl attrTarget = (org.exist.dom.memtree.AttrImpl) target;
            final org.exist.dom.memtree.NodeImpl parentElement =
                    (org.exist.dom.memtree.NodeImpl) attrTarget.getOwnerElement();
            final int parentElementNum = parentElement.getNodeNumber();
            doc.removeAttribute(preCapIndex);
            final Sequence content = p.getContent();
            if (content != null && !content.isEmpty()) {
                doc.insertAttributes(parentElementNum, content, false);
            }
        } else {
            doc.replaceNode(target.getNodeNumber(), p.getContent());
        }
    }

    private void applyInMemoryDelete(final UpdatePrimitive p) throws XPathException {
        final org.exist.dom.memtree.NodeImpl target = (org.exist.dom.memtree.NodeImpl) p.getTargetNode();
        if (target instanceof org.exist.dom.memtree.DocumentImpl) {
            // Per W3C spec, deleting a parentless node (document node) is a no-op
            return;
        }
        final org.exist.dom.memtree.DocumentImpl doc = target.getOwnerDocument();
        if (target.getNodeType() == Node.ATTRIBUTE_NODE) {
            doc.removeAttribute(target.getNodeNumber());
        } else {
            doc.removeNode(target.getNodeNumber());
        }
    }

    /**
     * Apply updates to persistent (stored) database nodes.
     * Follows locking and transaction patterns from existing Modification class.
     */
    private void applyPersistent(final XQueryContext context, final List<UpdatePrimitive> prims) throws XPathException {
        final DBBroker broker = context.getBroker();
        final MutableDocumentSet modifiedDocuments = new DefaultDocumentSet();
        final Int2ObjectMap<DocumentTrigger> triggers = new Int2ObjectOpenHashMap<>();

        // Collect all affected documents
        final Set<DocumentImpl> affectedDocs = new LinkedHashSet<>();
        for (final UpdatePrimitive p : prims) {
            final Node node = p.getTargetNode();
            if (node instanceof StoredNode) {
                affectedDocs.add(((StoredNode) node).getOwnerDocument());
            }
        }

        ManagedLocks<ManagedDocumentLock> lockedDocumentsLocks = null;

        try {
            // Acquire global update lock and then document-level write locks
            final java.util.concurrent.locks.Lock globalLock = broker.getBrokerPool().getGlobalUpdateLock();
            globalLock.lock();
            try {
                final DefaultDocumentSet docSet = new DefaultDocumentSet();
                for (final DocumentImpl doc : affectedDocs) {
                    docSet.add(doc);
                }
                lockedDocumentsLocks = docSet.lock(broker, true);

                // Prepare triggers
                for (final DocumentImpl doc : affectedDocs) {
                    prepareTrigger(broker, triggers, doc);
                }
            } finally {
                globalLock.unlock();
            }

            // Apply within a transaction
            try (final Txn transaction = broker.continueOrBeginTransaction()) {

                // W3C XQuery Update Facility 3.0, Section 3.3.3 — Application order:
                // Phase 1: inserts, replaceValue (non-element), renames
                // Phase 3: replaceNode
                // Phase 4: replaceElementContent (replaceValue on elements)
                // Phase 5: deletes
                // Phase 6: puts
                final List<UpdatePrimitive> inserts = new ArrayList<>();
                final List<UpdatePrimitive> renames = new ArrayList<>();
                final List<UpdatePrimitive> replaceValues = new ArrayList<>();
                final List<UpdatePrimitive> replaceElementContents = new ArrayList<>();
                final List<UpdatePrimitive> replaceNodes = new ArrayList<>();
                final List<UpdatePrimitive> deletes = new ArrayList<>();
                final List<UpdatePrimitive> puts = new ArrayList<>();

                for (final UpdatePrimitive p : prims) {
                    switch (p.getType()) {
                        case INSERT_INTO, INSERT_INTO_AS_FIRST, INSERT_INTO_AS_LAST,
                             INSERT_BEFORE, INSERT_AFTER, INSERT_ATTRIBUTES:
                            inserts.add(p);
                            break;
                        case RENAME:
                            renames.add(p);
                            break;
                        case REPLACE_VALUE:
                            if (p.getTargetNode().getNodeType() == Node.ELEMENT_NODE) {
                                replaceElementContents.add(p);
                            } else {
                                replaceValues.add(p);
                            }
                            break;
                        case REPLACE_NODE:
                            replaceNodes.add(p);
                            break;
                        case DELETE:
                            deletes.add(p);
                            break;
                        case PUT:
                            puts.add(p);
                            break;
                        default:
                            break;
                    }
                }

                // Collect elements targeted by replaceElementContent — per W3C spec,
                // replaceElementContent replaces ALL children, so inserts into these
                // elements and replaceNode of their children are redundant.
                final Set<String> replaceElementContentTargets = new HashSet<>();
                for (final UpdatePrimitive p : replaceElementContents) {
                    replaceElementContentTargets.add(nodeKey(p.getTargetNode()));
                }

                for (final UpdatePrimitive p : inserts) {
                    // Skip non-attribute inserts into elements whose content will be replaced
                    if (!replaceElementContentTargets.isEmpty()) {
                        final Node insertTarget = p.getTargetNode();
                        final boolean isInsertIntoElement =
                                (p.getType() == UpdatePrimitive.Type.INSERT_INTO ||
                                 p.getType() == UpdatePrimitive.Type.INSERT_INTO_AS_FIRST ||
                                 p.getType() == UpdatePrimitive.Type.INSERT_INTO_AS_LAST) &&
                                insertTarget.getNodeType() == Node.ELEMENT_NODE;
                        if (isInsertIntoElement && replaceElementContentTargets.contains(nodeKey(insertTarget))) {
                            continue;
                        }
                    }
                    applyPersistentInsert(context, transaction, p, modifiedDocuments);
                }
                for (final UpdatePrimitive p : renames) {
                    applyPersistentRename(context, transaction, p, modifiedDocuments);
                }
                for (final UpdatePrimitive p : replaceValues) {
                    applyPersistentReplaceValue(context, transaction, p, modifiedDocuments);
                }
                // Phase 3: replaceNode — skip if the target's parent is targeted by
                // replaceElementContent (which will replace ALL children anyway)
                for (final UpdatePrimitive p : replaceNodes) {
                    if (!replaceElementContentTargets.isEmpty()) {
                        final Node replTarget = p.getTargetNode();
                        final Node parent = replTarget.getNodeType() == Node.ATTRIBUTE_NODE
                                ? ((Attr) replTarget).getOwnerElement()
                                : replTarget.getParentNode();
                        if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE
                                && replaceElementContentTargets.contains(nodeKey(parent))) {
                            continue;
                        }
                    }
                    applyPersistentReplaceNode(context, transaction, p, modifiedDocuments);
                }
                // Phase 4: replaceElementContent (after replaceNode)
                for (final UpdatePrimitive p : replaceElementContents) {
                    applyPersistentReplaceValue(context, transaction, p, modifiedDocuments);
                }
                // Delete in reverse document order
                for (int i = deletes.size() - 1; i >= 0; i--) {
                    applyPersistentDelete(context, transaction, deletes.get(i), modifiedDocuments);
                }
                for (final UpdatePrimitive p : puts) {
                    applyPersistentPut(context, transaction, p);
                }

                // Store all modified documents and send notifications
                final NotificationService notifier2 = broker.getBrokerPool().getNotificationService();
                final Iterator<DocumentImpl> storeIter = modifiedDocuments.getDocumentIterator();
                while (storeIter.hasNext()) {
                    final DocumentImpl doc = storeIter.next();
                    broker.storeXMLResource(transaction, doc);
                    notifier2.notifyUpdate(doc, UpdateListener.UPDATE);
                }

                // Finish triggers
                final Iterator<DocumentImpl> iterator = modifiedDocuments.getDocumentIterator();
                while (iterator.hasNext()) {
                    final DocumentImpl doc = iterator.next();
                    context.addModifiedDoc(doc);
                    finishTrigger(broker, triggers, doc);
                }
                triggers.clear();

                transaction.commit();
            } catch (final TriggerException | org.exist.storage.txn.TransactionException e) {
                throw new XPathException((Expression) null, e.getMessage(), e);
            }

        } catch (final LockException | TriggerException e) {
            throw new XPathException((Expression) null, e.getMessage(), e);
        } finally {
            if (lockedDocumentsLocks != null) {
                lockedDocumentsLocks.close();
            }
        }
    }

    private void applyPersistentInsert(final XQueryContext context, final Txn transaction,
                                        final UpdatePrimitive p, final MutableDocumentSet modifiedDocuments) throws XPathException {
        final StoredNode<?> node = (StoredNode<?>) p.getTargetNode();
        final DocumentImpl doc = node.getOwnerDocument();
        checkWritePermission(context, doc, p.getSourceExpression());

        final Sequence contentSeq = deepCopy(context, p.getContent());
        final NodeList contentList = sequenceToNodeList(contentSeq);

        try {
            switch (p.getType()) {
                case INSERT_INTO, INSERT_INTO_AS_LAST:
                    node.appendChildren(transaction, contentList, -1);
                    break;
                case INSERT_INTO_AS_FIRST:
                    node.appendChildren(transaction, contentList, 1);
                    break;
                case INSERT_BEFORE: {
                    final NodeImpl<?> parent = (NodeImpl<?>) getParent(node);
                    if (parent != null) {
                        parent.insertBefore(transaction, contentList, node);
                    }
                    break;
                }
                case INSERT_AFTER: {
                    final NodeImpl<?> parent = (NodeImpl<?>) getParent(node);
                    if (parent != null) {
                        parent.insertAfter(transaction, contentList, node);
                    }
                    break;
                }
                case INSERT_ATTRIBUTES: {
                    final ElementImpl elem = (ElementImpl) node;
                    for (int i = 0; i < contentList.getLength(); i++) {
                        final Node attrNode = contentList.item(i);
                        if (attrNode.getNodeType() == Node.ATTRIBUTE_NODE) {
                            final Attr attr = (Attr) attrNode;
                            final String nsUri = attr.getNamespaceURI();
                            if (nsUri != null && !nsUri.isEmpty()) {
                                elem.setAttributeNS(nsUri,
                                        (attr.getPrefix() != null ? attr.getPrefix() + ":" : "")
                                                + attr.getLocalName(),
                                        attr.getValue());
                            } else {
                                elem.setAttribute(attr.getName(), attr.getValue());
                            }
                        }
                    }
                    break;
                }
                default:
                    break;
            }

            doc.setLastModified(System.currentTimeMillis());
            modifiedDocuments.add(doc);
        } catch (final Exception e) {
            throw new XPathException(p.getSourceExpression(), e.getMessage(), e);
        }
    }

    private void applyPersistentRename(final XQueryContext context, final Txn transaction,
                                        final UpdatePrimitive p, final MutableDocumentSet modifiedDocuments) throws XPathException {
        final StoredNode<?> node = (StoredNode<?>) p.getTargetNode();
        final DocumentImpl doc = node.getOwnerDocument();
        checkWritePermission(context, doc, p.getSourceExpression());

        try {
            final NamedNode newNode;
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE:
                    newNode = new ElementImpl(node.getExpression(), (ElementImpl) node);
                    break;
                case Node.ATTRIBUTE_NODE:
                    newNode = new AttrImpl(node.getExpression(),
                            (AttrImpl) node);
                    break;
                default:
                    throw new XPathException(p.getSourceExpression(), ErrorCodes.XUTY0012,
                            "Target of rename must be an element, attribute, or processing instruction node.");
            }
            newNode.setNodeName(p.getNewName(), context.getBroker().getBrokerPool().getSymbols());

            final Node parent = getParent(node);
            if (parent instanceof ElementImpl parentElem) {
                parentElem.updateChild(transaction, node, newNode);
            }

            doc.setLastModified(System.currentTimeMillis());
            modifiedDocuments.add(doc);
        } catch (final XPathException e) {
            throw e;
        } catch (final Exception e) {
            throw new XPathException(p.getSourceExpression(), e.getMessage(), e);
        }
    }

    private void applyPersistentReplaceValue(final XQueryContext context, final Txn transaction,
                                              final UpdatePrimitive p, final MutableDocumentSet modifiedDocuments) throws XPathException {
        final StoredNode<?> node = (StoredNode<?>) p.getTargetNode();
        final DocumentImpl doc = node.getOwnerDocument();
        checkWritePermission(context, doc, p.getSourceExpression());

        try {
            // Per W3C spec: atomize content, join with single space separator
            final String newValue = atomizeAndJoin(p.getContent());

            // Validate content constraints per XML spec
            validateNodeContent(node.getNodeType(), newValue, p.getSourceExpression());

            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE: {
                    // Replace all children of element with a single text node
                    final NodeListImpl content = new NodeListImpl();
                    content.add(new TextImpl(node.getExpression(), newValue));
                    ((ElementImpl) node).update(transaction, content);
                    break;
                }
                case Node.TEXT_NODE: {
                    final ElementImpl parent = (ElementImpl) node.getParentNode();
                    final TextImpl text =
                            new TextImpl(node.getExpression(), newValue);
                    text.setOwnerDocument(doc);
                    parent.updateChild(transaction, node, text);
                    break;
                }
                case Node.ATTRIBUTE_NODE: {
                    final AttrImpl oldAttr =
                            (AttrImpl) node;
                    final ElementImpl parent = (ElementImpl) ((Attr) node).getOwnerElement();
                    if (parent != null) {
                        final AttrImpl newAttr =
                                new AttrImpl(node.getExpression(),
                                        oldAttr.getQName(), newValue, context.getBroker().getBrokerPool().getSymbols());
                        newAttr.setOwnerDocument(doc);
                        parent.updateChild(transaction, node, newAttr);
                    }
                    break;
                }
                case Node.COMMENT_NODE: {
                    final Node parent = node.getParentNode();
                    final CommentImpl newComment =
                            new CommentImpl(node.getExpression(), newValue);
                    if (parent instanceof ElementImpl parentElem) {
                        parentElem.updateChild(transaction, node, newComment);
                    } else if (parent instanceof DocumentImpl parentDoc) {
                        newComment.setOwnerDocument(doc);
                        parentDoc.updateChild(transaction, node, newComment);
                    }
                    break;
                }
                case Node.PROCESSING_INSTRUCTION_NODE: {
                    final Node parent = node.getParentNode();
                    final ProcessingInstructionImpl newPI =
                            new ProcessingInstructionImpl(
                                    node.getExpression(), node.getNodeName(), newValue);
                    if (parent instanceof ElementImpl parentElem) {
                        parentElem.updateChild(transaction, node, newPI);
                    } else if (parent instanceof DocumentImpl parentDoc) {
                        newPI.setOwnerDocument(doc);
                        parentDoc.updateChild(transaction, node, newPI);
                    }
                    break;
                }
                default:
                    throw new XPathException(p.getSourceExpression(), ErrorCodes.XUTY0007,
                            "Target of replace value must be an element, attribute, text, comment, or processing instruction node.");
            }

            doc.setLastModified(System.currentTimeMillis());
            modifiedDocuments.add(doc);
        } catch (final XPathException e) {
            throw e;
        } catch (final Exception e) {
            throw new XPathException(p.getSourceExpression(), e.getMessage(), e);
        }
    }

    private void applyPersistentReplaceNode(final XQueryContext context, final Txn transaction,
                                             final UpdatePrimitive p, final MutableDocumentSet modifiedDocuments) throws XPathException {
        final StoredNode<?> node = (StoredNode<?>) p.getTargetNode();
        final DocumentImpl doc = node.getOwnerDocument();
        checkWritePermission(context, doc, p.getSourceExpression());

        try {
            final StoredNode parent = node.getParentStoredNode();
            if (parent == null) {
                throw new XPathException(p.getSourceExpression(), ErrorCodes.XUDY0009,
                        "Target node of replace has no parent.");
            }

            final Sequence contentSeq = deepCopy(context, p.getContent());

            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE: {
                    if (contentSeq.getItemCount() > 0) {
                        final Item newItem = contentSeq.itemAt(0);
                        if (Type.subTypeOf(newItem.getType(), Type.NODE)) {
                            final Node newNode = ((NodeValue) newItem).getNode();
                            ((ElementImpl) parent).replaceChild(transaction, newNode, node);
                        }
                    }
                    break;
                }
                case Node.TEXT_NODE: {
                    final TextImpl text =
                            new TextImpl(node.getExpression(), contentSeq.getStringValue());
                    ((ElementImpl) parent).updateChild(transaction, node, text);
                    break;
                }
                case Node.ATTRIBUTE_NODE: {
                    if (contentSeq.getItemCount() > 0) {
                        final Item newItem = contentSeq.itemAt(0);
                        if (Type.subTypeOf(newItem.getType(), Type.NODE)) {
                            final Node newNode = ((NodeValue) newItem).getNode();
                            ((ElementImpl) parent).replaceChild(transaction, newNode, node);
                        }
                    }
                    break;
                }
                default: {
                    if (contentSeq.getItemCount() > 0) {
                        final Item newItem = contentSeq.itemAt(0);
                        if (Type.subTypeOf(newItem.getType(), Type.NODE)) {
                            final Node newNode = ((NodeValue) newItem).getNode();
                            ((ElementImpl) parent).replaceChild(transaction, newNode, node);
                        }
                    }
                    break;
                }
            }

            doc.setLastModified(System.currentTimeMillis());
            modifiedDocuments.add(doc);
        } catch (final XPathException e) {
            throw e;
        } catch (final Exception e) {
            throw new XPathException(p.getSourceExpression(), e.getMessage(), e);
        }
    }

    private void applyPersistentDelete(final XQueryContext context, final Txn transaction,
                                        final UpdatePrimitive p, final MutableDocumentSet modifiedDocuments) throws XPathException {
        final StoredNode<?> node = (StoredNode<?>) p.getTargetNode();
        final DocumentImpl doc = node.getOwnerDocument();
        checkWritePermission(context, doc, p.getSourceExpression());

        try {
            final Node parent = getParent(node);
            if (parent == null) {
                // Per W3C spec, deleting a parentless node is a no-op
                return;
            }
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                ((ElementImpl) parent).removeChild(transaction, node);
            } else if (parent.getNodeType() == Node.DOCUMENT_NODE) {
                // Document-level node (comment, PI) — remove directly via broker
                final DBBroker broker = context.getBroker();
                final NodePath nodePath = node.getPath();
                final IndexController indexes = broker.getIndexController();
                indexes.setDocument(doc);
                indexes.setMode(ReindexMode.REMOVE_SOME_NODES);
                broker.removeAllNodes(transaction, node, nodePath, indexes.getStreamListener());
                broker.endRemove(transaction);
                broker.flush();
            } else {
                throw new XPathException(p.getSourceExpression(),
                        "Cannot delete node: parent is neither element nor document node.");
            }

            doc.setLastModified(System.currentTimeMillis());
            modifiedDocuments.add(doc);
        } catch (final XPathException e) {
            throw e;
        } catch (final Exception e) {
            throw new XPathException(p.getSourceExpression(), e.getMessage(), e);
        }
    }

    private void applyPersistentPut(final XQueryContext context, final Txn transaction,
                                     final UpdatePrimitive p) throws XPathException {
        // fn:put implementation - store a document at the given URI
        // TODO: implement fn:put for persistent storage
        LOG.warn("fn:put is not yet fully implemented for persistent storage. Target: {}",
                p.getTargetNode());
    }

    /**
     * Reset this PUL (clear all primitives).
     */
    public void clear() {
        primitives.clear();
    }

    // Utility methods

    /**
     * Return a priority for insert primitive types, controlling application order.
     * Per W3C spec: INSERT_INTO_AS_FIRST first, INSERT_INTO_AS_LAST last,
     * INSERT_INTO and others in between.
     */
    private static int insertPriority(final UpdatePrimitive.Type type) {
        return switch (type) {
            case INSERT_INTO_AS_FIRST -> 0;
            case INSERT_BEFORE, INSERT_AFTER, INSERT_ATTRIBUTES -> 1;
            case INSERT_INTO -> 2;
            case INSERT_INTO_AS_LAST -> 3;
            default -> 1;
        };
    }

    private static boolean isPersistentNode(final Node node) {
        return node instanceof StoredNode;
    }

    private static void checkWritePermission(final XQueryContext context, final DocumentImpl doc,
                                              final Expression expr) throws XPathException {
        try {
            if (!doc.getPermissions().validate(context.getSubject(), Permission.WRITE)) {
                throw new PermissionDeniedException("User '" + context.getSubject().getName()
                        + "' does not have permission to write to the document '" + doc.getDocumentURI() + "'!");
            }
        } catch (final PermissionDeniedException e) {
            throw new XPathException(expr, e.getMessage(), e);
        }
    }

    private static Node getParent(final Node node) {
        if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
            return ((Attr) node).getOwnerElement();
        }
        return node.getParentNode();
    }

    /**
     * Deep copy a sequence, detaching nodes from their source documents.
     * Reuses the pattern from Modification.deepCopy().
     */
    private static Sequence deepCopy(final XQueryContext context, final Sequence inSeq) throws XPathException {
        if (inSeq == null || inSeq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        context.pushDocumentContext();
        final MemTreeBuilder builder = context.getDocumentBuilder();
        final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(context.getRootExpression(), builder);
        final Serializer serializer = context.getBroker().borrowSerializer();
        serializer.setReceiver(receiver);

        try {
            final Sequence out = new ValueSequence();
            for (final SequenceIterator i = inSeq.iterate(); i.hasNext(); ) {
                Item item = i.nextItem();
                if (item.getType() == Type.DOCUMENT) {
                    if (((NodeValue) item).getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        final NodeHandle root = (NodeHandle) ((NodeProxy) item).getOwnerDocument().getDocumentElement();
                        item = new NodeProxy(context.getRootExpression(), root);
                    } else {
                        item = (Item) ((org.w3c.dom.Document) item).getDocumentElement();
                    }
                }
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    if (((NodeValue) item).getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        final int last = builder.getDocument().getLastNode();
                        final NodeProxy p = (NodeProxy) item;
                        serializer.toReceiver(p, false, false);
                        if (p.getNodeType() == Node.ATTRIBUTE_NODE) {
                            item = builder.getDocument().getLastAttr();
                        } else {
                            item = builder.getDocument().getNode(last + 1);
                        }
                    } else {
                        ((org.exist.dom.memtree.NodeImpl) item).deepCopy();
                    }
                }
                out.add(item);
            }
            return out;
        } catch (final SAXException e) {
            throw new XPathException(context.getRootExpression(), e.getMessage(), e);
        } finally {
            context.getBroker().returnSerializer(serializer);
            context.popDocumentContext();
        }
    }

    private static NodeList sequenceToNodeList(final Sequence seq) throws XPathException {
        final NodeListImpl nl = new NodeListImpl();
        for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (Type.subTypeOf(item.getType(), Type.NODE)) {
                nl.add(((NodeValue) item).getNode());
            }
        }
        return nl;
    }

    private static void prepareTrigger(final DBBroker broker, final Int2ObjectMap<DocumentTrigger> triggers,
                                        final DocumentImpl doc) throws TriggerException {
        final org.exist.collections.Collection col = doc.getCollection();
        final DocumentTrigger trigger = new DocumentTriggers(broker, null, col);
        trigger.beforeUpdateDocument(broker, broker.getCurrentTransaction(), doc);
        triggers.put(doc.getDocId(), trigger);
    }

    private static void finishTrigger(final DBBroker broker, final Int2ObjectMap<DocumentTrigger> triggers,
                                       final DocumentImpl doc) throws TriggerException {
        final DocumentTrigger trigger = triggers.get(doc.getDocId());
        if (trigger != null) {
            trigger.afterUpdateDocument(broker, broker.getCurrentTransaction(), doc);
        }
    }
}
