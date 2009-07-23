/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
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
package org.exist.dom;

import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.Iterator;

/**
 * This node set is called virtual because it is just a placeholder for
 * the set of relevant nodes. For XPath expressions like //* or //node(), 
 * it would be totally unefficient to actually retrieve all descendant nodes.
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
     * @param axis an <code>int</code> value
     * @param test a <code>NodeTest</code> value
     * @param contextId an <code>int</code> value
     * @param context a <code>NodeSet</code> value
     */
    public VirtualNodeSet(DBBroker broker, int axis, NodeTest test, int contextId, NodeSet context) {
        isEmpty = true;
        hasOne = false;
        this.axis = axis;
        this.test = test;
        this.context = context;
        this.contextId = contextId;
        this.broker = broker;
    }

    /**
     * The method <code>contains</code>
     *
     * @param p a <code>NodeProxy</code> value
     * @return a <code>boolean</code> value
     */
    public boolean contains(NodeProxy p) {
        NodeProxy firstParent = getFirstParent(p, null, (axis == Constants.SELF_AXIS), 0);
        // Timo Boehme: getFirstParent returns now only real parents
        // therefore test if node is child of context
        if (firstParent != null) {
            return true;
        }
        //	if (context.get(p.getDocument(), p.getNodeId().getParentId()) != null) {
        //      return true;
        //	}
        return false; 
    }

    /**
     * The method <code>setInPredicate</code>
     *
     * @param predicate a <code>boolean</code> value
     */
    public void setInPredicate(boolean predicate) {
        inPredicate = predicate;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#getDocumentSet()
     */
    public DocumentSet getDocumentSet() {
    	//If we know what are our documents, return them...
    	if (realDocumentSet != null)
    		return realDocumentSet;
    	//... otherwise, we default to every *ptotentially* concerned document
        return context.getDocumentSet();
    }

    /**
     * The method <code>getCollectionIterator</code>
     *
     * @return an <code>Iterator</code> value
     */
    public Iterator getCollectionIterator() {
        return context.getCollectionIterator();
    }

    /**
     * The method <code>getFirstParent</code>
     *
     * @param node a <code>NodeProxy</code> value
     * @param first a <code>NodeProxy</code> value
     * @param includeSelf a <code>boolean</code> value
     * @param recursions an <code>int</code> value
     * @return a <code>NodeProxy</code> value
     */
    private NodeProxy getFirstParent(NodeProxy self, NodeProxy firstParent,
                                     boolean includeSelf, int recursions) {
        return getFirstParent(self, firstParent, includeSelf, true, recursions);
    }

    /**
     * The method <code>getFirstParent</code>
     *
     * @param node a <code>NodeProxy</code> value
     * @param first a <code>NodeProxy</code> value
     * @param includeSelf a <code>boolean</code> value
     * @param directParent a <code>boolean</code> value
     * @param recursions an <code>int</code> value
     * @return a <code>NodeProxy</code> value
     */
    private NodeProxy getFirstParent(NodeProxy self, NodeProxy candidateFirstParent,
                                     boolean includeSelf, boolean restrictToDirectParent, int recursions) {
        
        /* if the node is a doument node we still need to 
         * complete this method to check if we have found a potential parent
         * in one of the iterations before.
         */
        NodeId parentOfSelfId = self.getNodeId().getParentId();
        // check if the start-node should be included, e.g. to process an
        // expression like *[. = 'xxx']
        //TODO : investigate on expression like *[.//* = 'xxx']
        if (recursions == 0 && includeSelf && test.matches(self)) {
            // if we're on the child axis, test if
            // the node is a direct child of the context node
            if (axis == Constants.CHILD_AXIS) {
            	//WARNING : get() realizes virtual node sets
            	//TODO : investigate more efficent solutions
            	NodeProxy parent = context.get(self.getDocument(), parentOfSelfId);
                if (parent != null) {
                	self.copyContext(parent);
                    if (useSelfAsContext && inPredicate) {
                    	self.addContextNode(contextId, self);
                    } else if (inPredicate)
                    	self.addContextNode(contextId, parent);
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
        if (candidateFirstParent == null) {
        	//given node was already document element -> no parent
            if (parentOfSelfId == NodeId.DOCUMENT_NODE) {                
                return null;
            }
            candidateFirstParent = new NodeProxy(self.getDocument(), parentOfSelfId, Node.ELEMENT_NODE);
            // if we are on the self axis, check if the first parent can be selected
            if (axis == Constants.DESCENDANT_SELF_AXIS) {
            	//WARNING : get() realizes virtual node sets
            	//TODO : investigate more efficent solutions
                NodeProxy parent = context.get(candidateFirstParent.getDocument(), parentOfSelfId);
                if (parent != null && test.matches(parent)) {
                	candidateFirstParent.copyContext(parent);
                    if (useSelfAsContext && inPredicate) {
                    	candidateFirstParent.addContextNode(contextId, candidateFirstParent);
                    } else if (inPredicate)
                    	candidateFirstParent.addContextNode(contextId, parent);
                    return candidateFirstParent;
                }
            }
            // We need a real parent : keep the candidate and continue to ierate from this one
            return getFirstParent(candidateFirstParent, candidateFirstParent, false, restrictToDirectParent, recursions + 1);
        }
        
        // is the node's parent in the context set?
    	//WARNING : get() realizes virtual node sets
    	//TODO : investigate more efficent solutions
        NodeProxy parentOfSelf = context.get(self.getDocument(), parentOfSelfId);

        if (parentOfSelf != null && test.matches(self)) {
            if (axis != Constants.CHILD_AXIS) {
                // if we are on the descendant axis, we return the first node 
                // we found while walking bottom-up.
                // Otherwise, we return the last one (which is node)
            	self = candidateFirstParent;
            }
            self.copyContext(parentOfSelf);
            if (useSelfAsContext && inPredicate) {
            	self.addContextNode(contextId, self);
            } else if (inPredicate) {
            	self.addContextNode(contextId, parentOfSelf);
            }
            // Timo Boehme: we return the ancestor which is child of context			
            return self;
        } else if (parentOfSelfId == NodeId.DOCUMENT_NODE) {
            // no matching node has been found in the context
            return null;
        } else if (restrictToDirectParent && axis == Constants.CHILD_AXIS && recursions == 1) {
            // break here if the expression is like /*/n          
            return null;        
        } else {
            // continue for expressions like //*/n or /*//n
        	parentOfSelf = new NodeProxy(self.getDocument(), parentOfSelfId, Node.ELEMENT_NODE);
            return getFirstParent(parentOfSelf, candidateFirstParent, false, false, recursions + 1);
        }
    }

    private void addInternal(NodeProxy p) {
        if (realSet == null)
            realSet = new NewArrayNodeSet(256);
        realSet.add(p);

        knownIsEmptyCardinality = true;
        knownHasOneCardinality = true;
        knownHasManyCardinality = true;
        isEmpty = realSet.isEmpty();
        hasOne = realSet.hasOne();
        hasMany = !(isEmpty || hasOne);
        
        //Reset the real document set
        //TODO : use realDocumentSet.add(p.getDocument()) ?
        realDocumentSet = null;
        realSetIsComplete = false;
    }

    /**
     * The method <code>parentWithChild</code>
     *
     * @param proxy a <code>NodeProxy</code> value
     * @param restrictToDirectParent a <code>boolean</code> value
     * @param includeSelf a <code>boolean</code> value
     * @param level an <code>int</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy parentWithChild(NodeProxy proxy, boolean restrictToDirectParent, boolean includeSelf,
                                     int level) {
        if (realSet != null && realSetIsComplete)
            return realSet.parentWithChild(proxy, restrictToDirectParent, includeSelf, level);

        NodeProxy first = getFirstParent(proxy, null, includeSelf, restrictToDirectParent, 0);
        if (first != null)
            //TODO : should we set an empty cardinality here ?
            addInternal(first);
        return first;
    }

    /**
     * The method <code>parentWithChild</code>
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>NodeId</code> value
     * @param restrictToDirectParent a <code>boolean</code> value
     * @param includeSelf a <code>boolean</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy parentWithChild(DocumentImpl doc, NodeId nodeId, boolean restrictToDirectParent, boolean includeSelf) {
        if (realSet != null && realSetIsComplete)
            return realSet.parentWithChild(doc, nodeId, restrictToDirectParent, includeSelf);
        
        NodeProxy first = getFirstParent(new NodeProxy(doc, nodeId), null, includeSelf, restrictToDirectParent, 0);
        if (first != null)
            //TODO : should we set an empty cardinality here ?
            addInternal(first);
        return first;
    }

    /**
     * The method <code>getNodes</code>
     *
     * @return a <code>NodeSet</code> value
     */
    private final NodeSet getNodes() {
        NewArrayNodeSet result = new NewArrayNodeSet();
        for (Iterator i = context.iterator(); i.hasNext();) {
            NodeProxy proxy = (NodeProxy) i.next();            
            if (proxy.getNodeId() == NodeId.DOCUMENT_NODE) {
                if(proxy.getDocument().getResourceType() == DocumentImpl.BINARY_FILE) {
                    // skip binary resources
                    continue;
                }

                // Add root node if axis is either self, ancestor-self or descendant-self /ljo
                if ((axis == Constants.SELF_AXIS ||
                    axis == Constants.ANCESTOR_SELF_AXIS || 
                    axis == Constants.DESCENDANT_SELF_AXIS) &&
                    test.matches(proxy)) {
                    result.add(proxy);
                }
                if ((axis == Constants.CHILD_AXIS || axis == Constants.ATTRIBUTE_AXIS) &&
                        proxy.getDocument().getChildCount() == 1) {
                    // Optimization: if the document has just 1 child node, we know that
                    // it has to be an element. Instead of calling Document.getChildNodes(),
                    // we just create a NodeProxy for the first child and return it if the
                    // test matches
                    NodeProxy p = proxy.getDocument().getFirstChildProxy();
                    if (test.matches(p)) {
                        if (useSelfAsContext && inPredicate) {
                            p.addContextNode(contextId, p);
                        }
                        result.add(p);
                    }
                } else {
                    NodeList cl = proxy.getDocument().getChildNodes();
                    for (int j = 0; j < cl.getLength(); j++) {
                        StoredNode node = (StoredNode) cl.item(j);
                        NodeProxy p = new NodeProxy(node);
                        if (test.matches(p)) {
                            // fixme! check for unwanted
                            // side effects. /ljo
                            //p.deepCopyContext(proxy);

                            if (useSelfAsContext && inPredicate) {
                                p.addContextNode(contextId, p);
                            }
                            result.add(p);
                        }
                        if (node.getNodeType() == Node.ELEMENT_NODE &&
                            (axis == Constants.DESCENDANT_AXIS ||
                             axis == Constants.DESCENDANT_SELF_AXIS ||
                             axis == Constants.DESCENDANT_ATTRIBUTE_AXIS)) {
                            // note: we create a copy of the docElemProxy here to
                            // be used as context when traversing the tree.
                            NodeProxy contextNode = new NodeProxy(p);
                            contextNode.deepCopyContext(proxy);
                            //TODO : is this StoredNode construction necessary ?
                            Iterator domIter = broker.getNodeIterator(new StoredNode(contextNode));
                            domIter.next();
                            contextNode.setMatches(proxy.getMatches());
                            addChildren(contextNode, result, node, domIter, 0);
                        }
                        if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE
                            && (axis == Constants.CHILD_AXIS ||
                                axis == Constants.DESCENDANT_AXIS ||
                                axis == Constants.DESCENDANT_SELF_AXIS ||
                                // fixme! self axis probably not needed /ljo
                                axis == Constants.SELF_AXIS ||
                                axis == Constants.PRECEDING_AXIS ||
                                axis == Constants.FOLLOWING_AXIS)) {
                            if (test.matches(node)) {
                                result.add(p);
                            }
                        }
                    }
                }
                continue;
            } else {
                if((axis == Constants.SELF_AXIS ||
                    axis == Constants.ANCESTOR_SELF_AXIS ||
                    axis == Constants.DESCENDANT_SELF_AXIS) &&
                    test.matches(proxy)) {
                        if(useSelfAsContext && inPredicate) {
                            proxy.addContextNode(contextId, proxy);
                        }
                        result.add(proxy);
                }
                if (test.getType() == Type.PROCESSING_INSTRUCTION ||
                    test.getType() == Type.COMMENT ||
                    test.getType() == Type.CDATA_SECTION) {
                    DocumentImpl doc = proxy.getDocument();
                    if (axis == Constants.PRECEDING_AXIS) {
                        StoredNode ps = (StoredNode) doc.getFirstChild();
                        StoredNode pe = (StoredNode) doc.getDocumentElement();
                        while (ps != null && !ps.equals(pe)) {
                            if (test.matches(ps)) {
                                result.add(new NodeProxy(ps));
                            }
                            ps = (StoredNode) doc.getFollowingSibling(ps);
                        }
                    }
                    if (axis == Constants.FOLLOWING_AXIS) {
                        StoredNode pe = (StoredNode) doc.getDocumentElement();
                        StoredNode pf = (StoredNode) doc.getFollowingSibling(pe); 
                        while (pf != null) {
                            if (test.matches(pf)) {
                                result.add(new NodeProxy(pf));
                            }
                            pf = (StoredNode) doc.getFollowingSibling(pf);
                        }
                    }
                    if (axis == Constants.SELF_AXIS ||
                        axis == Constants.ANCESTOR_SELF_AXIS || 
                        axis == Constants.DESCENDANT_SELF_AXIS) {
                        result.add(proxy);
                    }
                }
                if (axis != Constants.SELF_AXIS) {
                    //TODO : is this StroredNode construction necessary ?
//                    Iterator domIter = proxy.getDocument().getBroker().getNodeIterator(new StoredNode(proxy));
//                    StoredNode node = (StoredNode) domIter.next();
//                    node.setOwnerDocument(proxy.getDocument());
//                    node.setNodeId(proxy.getNodeId());
//                    addChildren(proxy, result, node, domIter, 0);
                    addChildren(proxy,result);
                }
            }
        }
        realDocumentSet = result.getDocumentSet();
        return result;
    }
	
    /**
     * recursively adds child nodes
     * @param contextNode a <code>NodeProxy</code> value
     * @param result a <code>NodeSet</code> value
     * @param node a <code>StoredNode</code> value
     * @param iter an <code>Iterator</code> value
     * @param recursions an <code>int</code> value
     */
    private void addChildren(NodeProxy contextNode, NodeSet result,
                                   StoredNode node, Iterator iter,
                                   int recursions) {
        if (node.hasChildNodes()) {
            for (int i = 0; i < node.getChildCount(); i++) {
                StoredNode child = (StoredNode) iter.next();
                if(child == null)
                    LOG.debug("CHILD == NULL; doc = " + 
                              ((DocumentImpl)node.getOwnerDocument()).getURI());
                if(node.getOwnerDocument() == null)
                    LOG.debug("DOC == NULL");
                child.setOwnerDocument((DocumentImpl)node.getOwnerDocument());
                NodeProxy p = new NodeProxy(child);
                p.setMatches(contextNode.getMatches());
                if (test.matches(child)) {
                    if (((axis == Constants.CHILD_AXIS
                          || axis == Constants.ATTRIBUTE_AXIS)
                         && recursions == 0) ||
                        (axis == Constants.DESCENDANT_AXIS
                         || axis == Constants.DESCENDANT_SELF_AXIS
                         || axis == Constants.DESCENDANT_ATTRIBUTE_AXIS)) {
                        p.deepCopyContext(contextNode);
                        if (useSelfAsContext && inPredicate) {
                            p.addContextNode(contextId, p);
                        } else if (inPredicate) {
                            p.addContextNode(contextId, contextNode);
                        }
                        result.add(p);
                    }
                }
                addChildren(contextNode, result, child, iter, recursions + 1);
            }
        }
    }

    private void addChildren(NodeProxy contextNode, NodeSet result) {
        try {
            EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(contextNode, true);
            int status = reader.next();
            int level = 0;
            if (status == XMLStreamReader.START_ELEMENT) {
                while (reader.hasNext()) {
                    status = reader.next();
                    if (axis == Constants.ATTRIBUTE_AXIS && status != XMLStreamReader.ATTRIBUTE)
                        break;
                    switch (status) {
                        case XMLStreamReader.END_ELEMENT:
                            if (--level < 0)
                                return;
                            break;
                        case XMLStreamReader.ATTRIBUTE:
                            if ((axis == Constants.ATTRIBUTE_AXIS && level == 0) ||
                                axis == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
                                AttrImpl attr = (AttrImpl) reader.getNode();
                                if (test.matches(attr)) {
                                    NodeProxy p = new NodeProxy(attr);
                                    p.deepCopyContext(contextNode);
                                    if (useSelfAsContext && inPredicate) {
                                        p.addContextNode(contextId, p);
                                    } else if (inPredicate) {
                                        p.addContextNode(contextId, contextNode);
                                    }
                                    result.add(p);
                                }
                            }
                            break;
                        default:
                            if (((axis == Constants.CHILD_AXIS && level == 0) ||
                                axis == Constants.DESCENDANT_AXIS ||
                                axis == Constants.DESCENDANT_SELF_AXIS) &&
                                test.matches(reader)) {
                                NodeId nodeId = (NodeId) reader.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
                                NodeProxy p = new NodeProxy(contextNode.getDocument(), nodeId,
                                        reader.getNodeType(), reader.getCurrentPosition());
                                p.deepCopyContext(contextNode);
                                if (useSelfAsContext && inPredicate) {
                                    p.addContextNode(contextId, p);
                                } else if (inPredicate) {
                                    p.addContextNode(contextId, contextNode);
                                }
                                result.add(p);
                            }
                            break;
                    }
                    if (status == XMLStreamReader.START_ELEMENT)
                        ++level;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
    }

    /**
     * The method <code>realize</code>
     *
     */
    public final void realize() {
        if (realSet != null && realSetIsComplete)
            return;
        realSet = getNodes();
        knownIsEmptyCardinality = true;
        knownHasOneCardinality = true;
        knownHasManyCardinality = true;
        isEmpty = realSet.isEmpty();
        hasOne = realSet.hasOne();
        hasMany = realSet.hasMany();        
        realSetIsComplete = true;
    }

    /**
     * The method <code>preferTreeTraversal</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean preferTreeTraversal() {
        if (realSet != null && realSetIsComplete)
            return true;
        if (axis != Constants.CHILD_AXIS)
            return false;
        int contextLen = context.getLength();
        int docs = context.getDocumentSet().getDocumentCount();
        if (contextLen > docs * MAX_CHILD_COUNT_FOR_OPTIMIZE)
            return false;   // more than 5 nodes per document
        for (Iterator i = context.iterator(); i.hasNext();) {
            NodeProxy p = (NodeProxy) i.next();
            if (p.getNodeId() == NodeId.DOCUMENT_NODE)
                return false;
            NodeImpl n = (NodeImpl) p.getNode();
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getChildCount() > MAX_CHILD_COUNT_FOR_OPTIMIZE)
                return false;
        }
        return true;
    }

    /**
     * The method <code>setSelfIsContext</code>
     *
     */
    public void setSelfIsContext() {
        useSelfAsContext = true;
        if (realSet != null && realSetIsComplete) {
            for (Iterator i = realSet.iterator(); i.hasNext();) {
                NodeProxy p = (NodeProxy) i.next();
                p.addContextNode(contextId, p);
            }
        }
    }

    /**
     * The method <code>setContextId</code>
     *
     * @param contextId an <code>int</code> value
     */
    public void setContextId(int contextId) {
        this.contextId = contextId;
    }
	
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#hasIndex()
     */
    public boolean hasIndex() {
        // Always return false: there's no index
        return false;
    }

    /* the following methods are normally never called in this context,
     * we just provide them because they are declared abstract
     * in the super class
     */	

    /**
     * The method <code>isEmpty</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean isEmpty() {		
        if (knownIsEmptyCardinality)
            return isEmpty;
        return getLength() == 0;
    }

    /**
     * The method <code>hasOne</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean hasOne() {
        if (knownHasOneCardinality)
            return hasOne;
        return getLength() == 1;
    }

    /**
     * The method <code>hasMany</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean hasMany() {
        if (knownHasManyCardinality)
            return hasMany;
        return getLength() > 1;
    }    

    /**
     * The method <code>add</code>
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>long</code> value
     */
    public void add(DocumentImpl doc, long nodeId) {
    }

    /**
     * The method <code>add</code>
     *
     * @param node a <code>Node</code> value
     */
    public void add(Node node) {
    }

    /**
     * The method <code>add</code>
     *
     * @param proxy a <code>NodeProxy</code> value
     */
    public void add(NodeProxy proxy) {
    }

    /**
     * The method <code>addAll</code>
     *
     * @param other a <code>NodeList</code> value
     */
    public void addAll(NodeList other) {
    }

    /**
     * The method <code>addAll</code>
     *
     * @param other a <code>NodeSet</code> value
     */
    public void addAll(NodeSet other) {
    }

    /**
     * The method <code>set</code>
     *
     * @param position an <code>int</code> value
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>long</code> value
     */
    public void set(int position, DocumentImpl doc, long nodeId) {
    }

    /**
     * The method <code>remove</code>
     *
     * @param node a <code>NodeProxy</code> value
     */
    public void remove(NodeProxy node) {
    }

    /**
     * The method <code>getLength</code>
     *
     * @return an <code>int</code> value
     */
    public int getLength() {
        realize();
        return realSet.getLength();
    }

    public int getItemType() {
        if (realSet != null && realSetIsComplete)
            return realSet.getItemType();
        return Type.NODE;
    }

    /**
     * The method <code>getItemCount</code>
     *
     * @return an <code>int</code> value
     */
    public int getItemCount() {
        //TODO : evaluate both semantics
        realize();
        return realSet.getItemCount();
    }

    /**
     * The method <code>item</code>
     *
     * @param pos an <code>int</code> value
     * @return a <code>Node</code> value
     */
    public Node item(int pos) {
        realize();
        return realSet.item(pos);
    }

    /**
     * The method <code>get</code>
     *
     * @param pos an <code>int</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy get(int pos) {
        realize();
        return realSet.get(pos);
    }

    /**
     * The method <code>itemAt</code>
     *
     * @param pos an <code>int</code> value
     * @return an <code>Item</code> value
     */
    public Item itemAt(int pos) {
        realize();
        return realSet.itemAt(pos);
    }

    /**
     * The method <code>get</code>
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>NodeId</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy get(DocumentImpl doc, NodeId nodeId) {
        realize();
        return realSet.get(doc, nodeId);
    }

    /**
     * The method <code>get</code>
     *
     * @param proxy a <code>NodeProxy</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy get(NodeProxy proxy) {
        realize();
        return realSet.get(proxy);
    }

    /**
     * The method <code>iterator</code>
     *
     * @return a <code>NodeSetIterator</code> value
     */
    public NodeSetIterator iterator() {
        realize();
        return realSet.iterator();
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#iterate()
     */
    public SequenceIterator iterate() throws XPathException {
        realize();
        return realSet.iterate();
    }

    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#unorderedIterator()
     */
    public SequenceIterator unorderedIterator() throws XPathException {
        realize();
        return realSet.unorderedIterator();
    }
	
    /**
     * The method <code>intersection</code>
     *
     * @param other a <code>NodeSet</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet intersection(NodeSet other) {
        realize();
        return realSet.intersection(other);
    }

    /**
     * The method <code>union</code>
     *
     * @param other a <code>NodeSet</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet union(NodeSet other) {
        realize();
        return realSet.union(other);
    }

    /**
     * The method <code>filterDocuments</code>
     *
     * @param otherSet a <code>NodeSet</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet filterDocuments(NodeSet otherSet) {
        return this;
    }

    /**
     * The method <code>clearContext</code>
     *
     */
    public void clearContext() throws XPathException {
		// ignored for a virtual set
	}
    
    /**
     * The method <code>toString</code>
     *
     * @return a <code>String</code> value
     */
    public String toString() {
        if (realSet == null)
            return "Virtual#unknown";
        StringBuilder result = new StringBuilder();
//        result.append("Virtual#").append(super.toString());
        return result.toString();
    }      
}
