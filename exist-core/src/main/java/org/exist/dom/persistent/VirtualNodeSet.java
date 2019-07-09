/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist Project
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
package org.exist.dom.persistent;

import org.exist.collections.Collection;
import org.exist.indexing.StructuralIndex;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.dom.INodeIterator;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.util.Iterator;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

/**
 * This node set is called virtual because it is just a placeholder for
 * the set of relevant nodes. For XPath expressions like //* or //node(),
 * it would be totally inefficient to actually retrieve all descendant nodes.
 * In many cases, the expression can be resolved at a later point in time
 * without retrieving the whole node set.
 *
 * VirtualNodeSet basically provides method getFirstParent to retrieve the first
 * matching descendant of its context according to the primary type axis.
 *
 * Class LocationStep will always return an instance of VirtualNodeSet
 * if it finds something like descendant::* etc..
 *
 * @author Wolfgang Meier
 * @author Timo Boehme
 */
public class VirtualNodeSet extends AbstractNodeSet {

    private static final int MAX_CHILD_COUNT_FOR_OPTIMIZE = 5;

    protected int axis = Constants.UNKNOWN_AXIS;
    protected NodeTest test;
    protected NodeSet context;
    protected NodeSet realSet = null;
    protected boolean realSetIsComplete = false;
    protected boolean inPredicate = false;
    protected boolean useSelfAsContext = false;
    protected int contextId = Expression.NO_CONTEXT_ID;

    private DocumentSet realDocumentSet = null;

    private boolean knownIsEmptyCardinality = false;
    private boolean knownHasOneCardinality = false;
    private boolean knownHasManyCardinality = false;

    protected boolean hasMany = false;

    private DBBroker broker;

    /**
     * Creates a new <code>VirtualNodeSet</code> instance.
     *
     * @param broker eXist-db DBBroker
     * @param axis      an <code>int</code> value
     * @param test      a <code>NodeTest</code> value
     * @param contextId an <code>int</code> value
     * @param context   a <code>NodeSet</code> value
     */
    public VirtualNodeSet(final DBBroker broker, final int axis, final NodeTest test, final int contextId, final NodeSet context) {
        this.isEmpty = true;
        this.hasOne = false;
        this.axis = axis;
        this.test = test;
        this.context = context;
        this.contextId = contextId;
        this.broker = broker;
    }

    @Override
    public boolean contains(final NodeProxy p) {
        final NodeProxy firstParent = getFirstParent(p, null, axis == Constants.SELF_AXIS, 0);
        // Timo Boehme: getFirstParent returns now only real parents
        // therefore test if node is child of context
        if(firstParent != null) {
            return true;
        }
        return false;
    }

    public void setInPredicate(final boolean predicate) {
        inPredicate = predicate;
    }

    @Override
    public DocumentSet getDocumentSet() {
        //If we know what are our documents, return them...
        if(realDocumentSet != null) {
            return realDocumentSet;
        }
        //... otherwise, we default to every *potentially* concerned document
        return context.getDocumentSet();
    }


    @Override
    public Iterator<Collection> getCollectionIterator() {
        return context.getCollectionIterator();
    }

    private NodeProxy getFirstParent(final NodeProxy self, final NodeProxy firstParent,
            final boolean includeSelf, final int recursions) {
        return getFirstParent(self, firstParent, includeSelf, true, recursions);
    }

    private NodeProxy getFirstParent(NodeProxy self, NodeProxy candidateFirstParent,
            final boolean includeSelf, final boolean restrictToDirectParent, final int recursions) {
        /* if the node is a document node we still need to
         * complete this method to check if we have found a potential parent
         * in one of the iterations before.
         */
        final NodeId parentOfSelfId = self.getNodeId().getParentId();
        // check if the start-node should be included, e.g. to process an
        // expression like *[. = 'xxx']
        //TODO : investigate on expression like *[.//* = 'xxx']
        if(recursions == 0 && includeSelf && test.matches(self)) {
            // if we're on the child axis, test if
            // the node is a direct child of the context node
            if(axis == Constants.CHILD_AXIS) {
                //WARNING : get() realizes virtual node sets
                //TODO : investigate more efficient solutions
                final NodeProxy parent = context.get(self.getOwnerDocument(), parentOfSelfId);
                if(parent != null) {
                    self.copyContext(parent);
                    if(useSelfAsContext && inPredicate) {
                        self.addContextNode(contextId, self);
                    } else if(inPredicate) {
                        self.addContextNode(contextId, parent);
                    }
                    return self;
                }
            } else {
                // descendant axis: remember the node and continue 
                candidateFirstParent = self;
            }
        }
        // if this is the first call to this method, remember the first 
        // parent node and continue to evaluate the method. We can't just return 
        // the first parent as we need a parent that is *actually* contained 
        // in the context set. We will thus call the method again to complete.
        if(candidateFirstParent == null) {
            //given node was already document element -> no parent
            if(parentOfSelfId == NodeId.DOCUMENT_NODE) {
                return null;
            }
            candidateFirstParent = new NodeProxy(self.getOwnerDocument(), parentOfSelfId, Node.ELEMENT_NODE);
            // if we are on the self axis, check if the first parent can be selected
            if(axis == Constants.DESCENDANT_SELF_AXIS) {
                //WARNING : get() realizes virtual node sets
                //TODO : investigate more efficient solutions
                final NodeProxy parent = context.get(candidateFirstParent.getOwnerDocument(), parentOfSelfId);
                if(parent != null && test.matches(parent)) {
                    candidateFirstParent.copyContext(parent);
                    if(useSelfAsContext && inPredicate) {
                        candidateFirstParent.addContextNode(contextId, candidateFirstParent);
                    } else if(inPredicate) {
                        candidateFirstParent.addContextNode(contextId, parent);
                    }
                    return candidateFirstParent;
                }
            }
            // We need a real parent : keep the candidate and continue to iterate from this one
            return getFirstParent(candidateFirstParent, candidateFirstParent, false,
                restrictToDirectParent, recursions + 1);
        }
        // is the node's parent in the context set?
        //WARNING : get() realizes virtual node sets
        //TODO : investigate more efficient solutions
        NodeProxy parentOfSelf = context.get(self.getOwnerDocument(), parentOfSelfId);
        if(parentOfSelf != null && test.matches(self)) {
            if(axis != Constants.CHILD_AXIS) {
                // if we are on the descendant axis, we return the first node 
                // we found while walking bottom-up.
                // Otherwise, we return the last one (which is the node itself)
                self = candidateFirstParent;
            }
            self.copyContext(parentOfSelf);
            if(useSelfAsContext && inPredicate) {
                self.addContextNode(contextId, self);
            } else if(inPredicate) {
                self.addContextNode(contextId, parentOfSelf);
            }
            // Timo Boehme: we return the ancestor which is child of context
            return self;
        } else if(parentOfSelfId == NodeId.DOCUMENT_NODE) {
            // no matching node has been found in the context
            return null;
        } else if(restrictToDirectParent && axis == Constants.CHILD_AXIS && recursions == 1) {
            // break here if the expression is like /*/n
            return null;
        } else {
            // continue for expressions like //*/n or /*//n
            parentOfSelf = new NodeProxy(self.getOwnerDocument(), parentOfSelfId, Node.ELEMENT_NODE);
            return getFirstParent(parentOfSelf, candidateFirstParent, false, false, recursions + 1);
        }
    }

    private void addInternal(final NodeProxy p) {
        if(realSet == null) {
            realSet = new NewArrayNodeSet();
        }
        realSet.add(p);
        knownIsEmptyCardinality = true;
        knownHasOneCardinality = true;
        knownHasManyCardinality = true;
        isEmpty = realSet.isEmpty();
        hasOne = realSet.hasOne();
        hasMany = !(isEmpty || hasOne);
        //Reset the real document set
        //TODO : use realDocumentSet.add(p.getOwnerDocument()) ?
        realDocumentSet = null;
        realSetIsComplete = false;
    }

    @Override
    public NodeProxy parentWithChild(final NodeProxy proxy, final boolean restrictToDirectParent,
            final boolean includeSelf, final int level) {
        if(realSet != null && realSetIsComplete) {
            return realSet.parentWithChild(proxy, restrictToDirectParent, includeSelf, level);
        } else {
            final NodeProxy first = getFirstParent(proxy, null, includeSelf, restrictToDirectParent, 0);
            if(first != null) {
                //TODO : should we set an empty cardinality here ?
                addInternal(first);
            }
            return first;
        }
    }

    @Override
    public NodeProxy parentWithChild(final DocumentImpl doc, final NodeId nodeId,
            final boolean restrictToDirectParent, final boolean includeSelf) {
        if(realSet != null && realSetIsComplete) {
            return realSet.parentWithChild(doc, nodeId, restrictToDirectParent, includeSelf);
        } else {
            final NodeProxy first = getFirstParent(new NodeProxy(doc, nodeId), null,
                includeSelf, restrictToDirectParent, 0);
            if(first != null) {
                //TODO : should we set an empty cardinality here ?
                addInternal(first);
            }
            return first;
        }
    }

    /**
     * Realize the node set by recursively scanning the
     * DOM.
     */
    private NodeSet getNodes() {
        final NewArrayNodeSet result = new NewArrayNodeSet();
        for(final Iterator<NodeProxy> i = context.iterator(); i.hasNext(); ) {
            final NodeProxy proxy = i.next();
            if(proxy.getNodeId() == NodeId.DOCUMENT_NODE) {
                if(proxy.getOwnerDocument().getResourceType() == DocumentImpl.BINARY_FILE) {
                    // skip binary resources
                    continue;
                }
                // Add root node if axis is either self, ancestor-self or descendant-self /ljo
                if((axis == Constants.SELF_AXIS ||
                    axis == Constants.ANCESTOR_SELF_AXIS ||
                    axis == Constants.DESCENDANT_SELF_AXIS) &&
                    test.matches(proxy)) {
                    result.add(proxy);
                }
                if((axis == Constants.CHILD_AXIS || axis == Constants.ATTRIBUTE_AXIS) &&
                    proxy.getOwnerDocument().getChildCount() == 1) {
                    // Optimization: if the document has just 1 child node, we know that
                    // it has to be an element. Instead of calling Document.getChildNodes(),
                    // we just create a NodeProxy for the first child and return it if the
                    // test matches
                    final NodeProxy p = proxy.getOwnerDocument().getFirstChildProxy();
                    if(test.matches(p)) {
                        if(useSelfAsContext && inPredicate) {
                            p.addContextNode(contextId, p);
                        }
                        p.addMatches(proxy);
                        result.add(p);
                    }
                } else {
                    final NodeList cl = proxy.getOwnerDocument().getChildNodes();
                    for(int j = 0; j < cl.getLength(); j++) {
                        final IStoredNode<?> node = (IStoredNode<?>) cl.item(j);
                        final NodeProxy p = new NodeProxy(node);
                        if(test.matches(p)) {
                            // fixme! check for unwanted
                            // side effects. /ljo
                            //p.deepCopyContext(proxy);
                            if(useSelfAsContext && inPredicate) {
                                p.addContextNode(contextId, p);
                            }
                            result.add(p);
                        }
                        if(node.getNodeType() == Node.ELEMENT_NODE &&
                            (axis == Constants.DESCENDANT_AXIS ||
                                axis == Constants.DESCENDANT_SELF_AXIS ||
                                axis == Constants.DESCENDANT_ATTRIBUTE_AXIS)) {
                            // note: we create a copy of the docElemProxy here to
                            // be used as context when traversing the tree.
                            final NodeProxy contextNode = new NodeProxy(p);
                            contextNode.deepCopyContext(proxy);
                            //TODO : is this StoredNode construction necessary ?
                            try(final INodeIterator domIter = broker.getNodeIterator(contextNode.asStoredNode())) {
                                domIter.next();
                                contextNode.setMatches(proxy.getMatches());
                                addChildren(contextNode, result, node, domIter, 0);
                            } catch(final IOException ioe) {
                                LOG.warn("Unable to close iterator", ioe);
                            }
                        }
                        if(node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE
                            && (axis == Constants.CHILD_AXIS ||
                            axis == Constants.DESCENDANT_AXIS ||
                            axis == Constants.DESCENDANT_SELF_AXIS ||
                            // fixme! self axis probably not needed /ljo
                            axis == Constants.SELF_AXIS ||
                            axis == Constants.PRECEDING_AXIS ||
                            axis == Constants.FOLLOWING_AXIS)
                            && test.matches(node)) {

                            result.add(p);
                        }
                    }
                }
                continue;
            }
            if((axis == Constants.SELF_AXIS ||
                axis == Constants.ANCESTOR_SELF_AXIS ||
                axis == Constants.DESCENDANT_SELF_AXIS) &&
                test.matches(proxy)) {
                if(useSelfAsContext && inPredicate) {
                    proxy.addContextNode(contextId, proxy);
                }
                result.add(proxy);
            }
            if(test.getType() == Type.PROCESSING_INSTRUCTION ||
                test.getType() == Type.COMMENT ||
                test.getType() == Type.CDATA_SECTION) {
                final DocumentImpl doc = proxy.getOwnerDocument();
                if(axis == Constants.PRECEDING_AXIS) {
                    IStoredNode<?> ps = (IStoredNode<?>) doc.getFirstChild();
                    final IStoredNode<?> pe = (IStoredNode<?>) doc.getDocumentElement();
                    while(ps != null && !ps.equals(pe)) {
                        if(test.matches(ps)) {
                            result.add(new NodeProxy(ps));
                        }
                        ps = (IStoredNode<?>) doc.getFollowingSibling(ps);
                    }
                }
                if(axis == Constants.FOLLOWING_AXIS) {
                    final IStoredNode<?> pe = (IStoredNode<?>) doc.getDocumentElement();
                    IStoredNode<?> pf = (IStoredNode<?>) doc.getFollowingSibling(pe);
                    while(pf != null) {
                        if(test.matches(pf)) {
                            result.add(new NodeProxy(pf));
                        }
                        pf = (IStoredNode<?>) doc.getFollowingSibling(pf);
                    }
                }
                if(axis == Constants.SELF_AXIS ||
                    axis == Constants.ANCESTOR_SELF_AXIS ||
                    axis == Constants.DESCENDANT_SELF_AXIS) {
                    result.add(proxy);
                }
            }
            if(axis != Constants.SELF_AXIS) {
                addChildren(proxy, result);
            }
        }
        realDocumentSet = result.getDocumentSet();
        return result;
    }

    /**
     * Realize the node set by scanning the structural index.
     * This is usually cheaper than calling {@link #getNodes()}.
     *
     * Not used right now because the method seems to dramatically slow down
     * some expressions instead of improving performance. To be checked.
     */
    private NodeSet getNodesFromIndex() {
        final StructuralIndex index = broker.getStructuralIndex();
        final byte type = test.getType() == Type.ELEMENT ? ElementValue.ELEMENT : ElementValue.ATTRIBUTE;
        final NodeSet result = index.scanByType(type, axis, test, useSelfAsContext && inPredicate,
            context.getDocumentSet(), context, contextId);
        realDocumentSet = result.getDocumentSet();
        return result;
    }

    /**
     * recursively adds child nodes
     *
     * @param contextNode a <code>NodeProxy</code> value
     * @param result      a <code>NodeSet</code> value
     * @param node        a <code>IStoredNode</code> value
     * @param iter        an <code>Iterator</code> value
     * @param recursions  an <code>int</code> value
     */
    private void addChildren(final NodeProxy contextNode, final NodeSet result,
            final IStoredNode node, final INodeIterator iter, final int recursions) {
        if(node.hasChildNodes() || node.hasAttributes()) {
            for(int i = 0; i < node.getChildCount(); i++) {
                final IStoredNode child = iter.next();
                if(child == null) {
                    LOG.debug("CHILD == NULL; doc = " +
                        ((DocumentImpl) node.getOwnerDocument()).getURI());
                    //TODO : throw exception ? -pb
                    return;
                }
                if(node.getOwnerDocument() == null) {
                    LOG.debug("DOC == NULL");
                    //TODO : throw exception ? -pb
                    return;
                }
                child.setOwnerDocument((DocumentImpl) node.getOwnerDocument());
                final NodeProxy p = new NodeProxy(child);
                p.setMatches(contextNode.getMatches());
                if(test.matches(child) && (
                    ((axis == Constants.CHILD_AXIS || axis == Constants.ATTRIBUTE_AXIS) && recursions == 0)
                    || (axis == Constants.DESCENDANT_AXIS || axis == Constants.DESCENDANT_SELF_AXIS || axis == Constants.DESCENDANT_ATTRIBUTE_AXIS))
                ) {

                    p.deepCopyContext(contextNode);

                    if(useSelfAsContext && inPredicate) {
                        p.addContextNode(contextId, p);
                    } else if(inPredicate) {
                        p.addContextNode(contextId, contextNode);
                    }
                    result.add(p);
                }
                addChildren(contextNode, result, child, iter, recursions + 1);
            }
        }
    }

    private void addChildren(final NodeProxy contextNode, final NodeSet result) {
        try {
            final EmbeddedXMLStreamReader reader = (EmbeddedXMLStreamReader)broker.getXMLStreamReader(contextNode, true);
            int status = reader.next();
            if (status != XMLStreamConstants.START_ELEMENT) {
                return;
            }

            int level = 0;
            while(reader.hasNext()) {
                status = reader.next();
                if(axis == Constants.ATTRIBUTE_AXIS && status != XMLStreamConstants.ATTRIBUTE) {
                    break;
                }
                switch(status) {
                    case XMLStreamConstants.END_ELEMENT:
                        if(--level < 0) {
                            return;
                        }
                        break;
                    case XMLStreamConstants.ATTRIBUTE:
                        if((axis == Constants.ATTRIBUTE_AXIS && level == 0) ||
                            axis == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
                            final AttrImpl attr = (AttrImpl) reader.getNode();
                            if(test.matches(attr)) {
                                final NodeProxy p = new NodeProxy(attr);
                                p.deepCopyContext(contextNode);
                                if(useSelfAsContext && inPredicate) {
                                    p.addContextNode(contextId, p);
                                } else if(inPredicate) {
                                    p.addContextNode(contextId, contextNode);
                                }
                                result.add(p);
                            }
                        }
                        break;
                    default:
                        if(((axis == Constants.CHILD_AXIS && level == 0) ||
                            axis == Constants.DESCENDANT_AXIS ||
                            axis == Constants.DESCENDANT_SELF_AXIS) &&
                            test.matches(reader)) {
                            final NodeId nodeId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                            final NodeProxy p = new NodeProxy(contextNode.getOwnerDocument(), nodeId,
                                reader.getNodeType(), reader.getCurrentPosition());
                            p.deepCopyContext(contextNode);
                            if(useSelfAsContext && inPredicate) {
                                p.addContextNode(contextId, p);
                            } else if(inPredicate) {
                                p.addContextNode(contextId, contextNode);
                            }
                            result.add(p);
                        }
                        break;
                }
                if(status == XMLStreamConstants.START_ELEMENT) {
                    ++level;
                }
            }
        } catch(final IOException e) {
            LOG.error(e);
            //TODO : throw exception ,
        } catch(final XMLStreamException e) {
            LOG.error(e);
            //TODO : throw exception ? -pb
        }
    }

    /**
     * Realize the node set. This should only be done if the
     * wildcard step is the last step in a path expression.
     */
    private final void realize() {
        if(realSet != null && realSetIsComplete) {
            return;
        }
        realSet = getNodes();
        knownIsEmptyCardinality = true;
        knownHasOneCardinality = true;
        knownHasManyCardinality = true;
        isEmpty = realSet.isEmpty();
        hasOne = realSet.hasOne();
        hasMany = realSet.hasMany();
        realSetIsComplete = true;
    }

    public void setSelfIsContext() {
        useSelfAsContext = true;
        if(realSet != null && realSetIsComplete) {
            for(final Iterator<NodeProxy> i = realSet.iterator(); i.hasNext(); ) {
                final NodeProxy p = i.next();
                p.addContextNode(contextId, p);
            }
        }
    }

    public void setContextId(final int contextId) {
        this.contextId = contextId;
    }

    /* the following methods are normally never called in this context,
     * we just provide them because they are declared abstract
     * in the super class
     */

    @Override
    public boolean isEmpty() {
        if(knownIsEmptyCardinality) {
            return isEmpty;
        }
        return getLength() == 0;
    }

    @Override
    public boolean hasOne() {
        if(knownHasOneCardinality) {
            return hasOne;
        }
        return getLength() == 1;
    }

    @Override
    public boolean hasMany() {
        if(knownHasManyCardinality) {
            return hasMany;
        }
        return getLength() > 1;
    }

    @Override
    public void add(final NodeProxy proxy) {
        //Nothing to do
    }

    @Override
    public void addAll(final NodeSet other) {
        //Nothing to do
    }

    @Override
    public int getLength() {
        realize();
        return realSet.getLength();
    }

    @Override
    public int getItemType() {
        if(realSet != null && realSetIsComplete) {
            return realSet.getItemType();
        }
        return Type.NODE;
    }

    @Override
    public long getItemCountLong() {
        //TODO : evaluate both semantics
        realize();
        return realSet.getItemCountLong();
    }

    @Override
    public Node item(final int pos) {
        realize();
        return realSet.item(pos);
    }

    @Override
    public NodeProxy get(final int pos) {
        realize();
        return realSet.get(pos);
    }

    @Override
    public Item itemAt(final int pos) {
        realize();
        return realSet.itemAt(pos);
    }

    @Override
    public NodeProxy get(final DocumentImpl doc, final NodeId nodeId) {
        realize();
        return realSet.get(doc, nodeId);
    }

    @Override
    public NodeProxy get(final NodeProxy proxy) {
        realize();
        return realSet.get(proxy);
    }

    @Override
    public NodeSetIterator iterator() {
        realize();
        return realSet.iterator();
    }

    @Override
    public SequenceIterator iterate() throws XPathException {
        realize();
        return realSet.iterate();
    }

    @Override
    public SequenceIterator unorderedIterator() throws XPathException {
        realize();
        return realSet.unorderedIterator();
    }

    @Override
    public NodeSet intersection(final NodeSet other) {
        realize();
        return realSet.intersection(other);
    }

    @Override
    public NodeSet union(final NodeSet other) {
        realize();
        return realSet.union(other);
    }

    @Override
    public NodeSet filterDocuments(final NodeSet otherSet) {
        return this;
    }

    @Override
    public String toString() {
        if(realSet == null) {
            return "Virtual#unknown";
        }
        return "";
    }
}
