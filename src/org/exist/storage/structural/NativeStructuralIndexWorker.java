/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.structural;

import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.indexing.*;
import org.exist.numbering.NodeId;
import org.exist.storage.*;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.util.ByteConversion;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.Occurrences;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.NodeTest;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

import org.exist.security.PermissionDeniedException;

/**
 * Internal default implementation of the structural index. It uses a single btree, in which
 * each key represents a sequence of: [type, qname, documentId, nodeId]. The btree value is just a
 * long pointing to the storage address of the actual node in dom.dbx.
 */
public class NativeStructuralIndexWorker implements IndexWorker, StructuralIndex {

    private NativeStructuralIndex index;
    private int mode = 0;
    private DocumentImpl document;
    private Map<QName, List<NodeProxy>> pending = new TreeMap<QName, List<NodeProxy>>();

    public NativeStructuralIndexWorker(NativeStructuralIndex index) {
        this.index = index;
    }

    public boolean matchElementsByTagName(byte type, DocumentSet docs, QName qname, NodeSelector selector) {
        return false;
    }

    public boolean matchDescendantsByTagName(byte type, QName qname, int axis, DocumentSet docs, ExtNodeSet contextSet, int contextId) {
        return false;
    }

    /**
     * Find all nodes in the index matching a given QName. If a match is selected and returned depends on
     * the specified {@link org.exist.xquery.NodeSelector}.
     *
     * This implementation does a scan through the index for a range of document ids in the input set.
     * It will be fast for bulk-loading a large node set, but slow if you need to operate on a small
     * context set.
     *
     * @param type
     * @param docs
     * @param qname
     * @param selector
     * @return nodeset
     */
    public NodeSet findElementsByTagName(byte type, DocumentSet docs, QName qname, NodeSelector selector) {
        final Lock lock = index.btree.getLock();
        final NewArrayNodeSet result = new NewArrayNodeSet(docs.getDocumentCount(), 256);
        final FindElementsCallback callback = new FindElementsCallback(type, result, docs, selector);
        // scan the document set to find document id ranges to query
        final List<Range> ranges = new ArrayList<Range>();
        Range next = null;
        for (final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl doc = i.next();
            if (next == null)
                {next = new Range(doc.getDocId());}
            else if (next.end + 1 == doc.getDocId())
                {next.end++;}
            else {
                ranges.add(next);
                next = new Range(doc.getDocId());
            }
        }
        if (next != null)
            {ranges.add(next);}

        // for each document id range, scan the index to find matches
        for (final Range range : ranges) {
            final byte[] fromKey = computeKey(type, qname, range.start);
            final byte[] toKey = computeKey(type, qname, range.end + 1);
            final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(toKey));
            try {
                lock.acquire(Lock.READ_LOCK);
                index.btree.query(query, callback);
            } catch (final LockException e) {
                NativeStructuralIndex.LOG.warn("Lock problem while searching structural index: " + e.getMessage(), e);
            } catch (final TerminatedException e) {
                NativeStructuralIndex.LOG.warn("Query was terminated while searching structural index: " + e.getMessage(), e);
            } catch (final Exception e) {
                NativeStructuralIndex.LOG.error("Error while searching structural index: " + e.getMessage(), e);
            } finally {
                lock.release(Lock.READ_LOCK);
            }
        }
        return result;
    }

    /**
     * Internal helper class used by
     * {@link NativeStructuralIndexWorker#findElementsByTagName(byte, org.exist.dom.DocumentSet, org.exist.dom.QName, org.exist.xquery.NodeSelector)}.
     */
    private static class Range {
        int start = -1;
        int end = -1;

        private Range(int start) {
            this.start = start;
            this.end = start;
        }
    }
    
    /**
     * Find all descendants (or children) of the specified node set matching the given QName.
     *
     * This implementation does one btree lookup for every node in contextSet. It offers superior performance
     * if the number of nodes in contextSet is rather small compared to the overall number of nodes in
     * the index.
     */
    public NodeSet findDescendantsByTagName(byte type, QName qname, int axis, DocumentSet docs, NodeSet contextSet, int contextId) {
        final Lock lock = index.btree.getLock();
        final NewArrayNodeSet result = new NewArrayNodeSet(docs.getDocumentCount(), 256);
        final FindDescendantsCallback callback = new FindDescendantsCallback(type, axis, contextId, result);
        try {
            lock.acquire(Lock.READ_LOCK);
            for (final NodeProxy ancestor : contextSet) {
                final DocumentImpl doc = ancestor.getDocument();
                final NodeId ancestorId = ancestor.getNodeId();
                callback.setAncestor(doc, ancestor);
                byte[] fromKey, toKey;
                if (ancestorId == NodeId.DOCUMENT_NODE) {
                    fromKey = computeKey(type, qname, doc.getDocId());
                    toKey = computeKey(type, qname, doc.getDocId() + 1);
                } else {
                    fromKey = computeKey(type, qname, doc.getDocId(), ancestorId);
                    toKey = computeKey(type, qname, doc.getDocId(), ancestorId.nextSibling());
                }
                final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(toKey));
                try {
                    index.btree.query(query, callback);
                } catch (final Exception e) {
                    NativeStructuralIndex.LOG.error("Error while searching structural index: " + e.getMessage(), e);
                }
            }
        } catch (final LockException e) {
            NativeStructuralIndex.LOG.warn("Lock problem while searching structural index: " + e.getMessage(), e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
        result.updateNoSort();
        return result;
    }

    public NodeSet findAncestorsByTagName(byte type, QName qname, int axis, DocumentSet docs, NodeSet contextSet,
                                          int contextId) {
        final Lock lock = index.btree.getLock();
        final NewArrayNodeSet result = new NewArrayNodeSet(docs.getDocumentCount(), 256);
        try {
            lock.acquire(Lock.READ_LOCK);
            for (final NodeProxy descendant : contextSet) {
                NodeId parentId;
                if (axis == Constants.ANCESTOR_SELF_AXIS || axis == Constants.SELF_AXIS)
                    {parentId = descendant.getNodeId();}
                else
                    {parentId = descendant.getNodeId().getParentId();}
                final DocumentImpl doc = descendant.getDocument();
                while (parentId != NodeId.DOCUMENT_NODE) {
                    final byte[] key = computeKey(type, qname, doc.getDocId(), parentId);
                    final long address = index.btree.findValue(new Value(key));
                    if (address != -1) {
                        final NodeProxy storedNode = new NodeProxy(doc, parentId,
                            type == ElementValue.ATTRIBUTE ? Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE, address);
                        result.add(storedNode);
                        if (Expression.NO_CONTEXT_ID != contextId) {
                            storedNode.deepCopyContext(descendant, contextId);
                        } else
                            {storedNode.copyContext(descendant);}
                        if (contextSet.getTrackMatches())
                        	{storedNode.addMatches(descendant);}
                    }
                    // stop after first iteration if we are on the self axis
                    if (axis == Constants.SELF_AXIS || axis == Constants.PARENT_AXIS)
                        {break;}
                    // continue with the parent of the parent
                    parentId = parentId.getParentId();
                }
            }
        } catch (final LockException e) {
            NativeStructuralIndex.LOG.warn("Lock problem while searching structural index: " + e.getMessage(), e);
        } catch (final Exception e) {
            NativeStructuralIndex.LOG.error("Error while searching structural index: " + e.getMessage(), e);
        } finally {
            lock.release(Lock.READ_LOCK);
        }
        result.sort(true);
        return result;
    }

    public NodeSet scanByType(byte type, int axis, NodeTest test, boolean useSelfAsContext, DocumentSet docs, 
    		NodeSet contextSet, int contextId) {
        final Lock lock = index.btree.getLock();
        final NewArrayNodeSet result = new NewArrayNodeSet(docs.getDocumentCount(), 256);
        final FindDescendantsCallback callback = new FindDescendantsCallback(type, axis, contextId, useSelfAsContext, result);
        for (final NodeProxy ancestor : contextSet) {
            final DocumentImpl doc = ancestor.getDocument();
            final NodeId ancestorId = ancestor.getNodeId();
            final List<QName> qnames = getQNamesForDoc(doc);
            try {
	            lock.acquire(Lock.READ_LOCK);
	            for (final QName qname : qnames) {
	            	if (test.getName() == null || test.matches(qname)) {
	            		callback.setAncestor(doc, ancestor);
	            		byte[] fromKey, toKey;
	                    if (ancestorId == NodeId.DOCUMENT_NODE) {
	                        fromKey = computeKey(type, qname, doc.getDocId());
	                        toKey = computeKey(type, qname, doc.getDocId() + 1);
	                    } else {
	                        fromKey = computeKey(type, qname, doc.getDocId(), ancestorId);
	                        toKey = computeKey(type, qname, doc.getDocId(), ancestorId.nextSibling());
	                    }
	                    final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(toKey));
	                    try {
	                        index.btree.query(query, callback);
	                    } catch (final Exception e) {
	                        NativeStructuralIndex.LOG.error("Error while searching structural index: " + e.getMessage(), e);
	                    }
	            	}
	            }
            } catch (final LockException e) {
                NativeStructuralIndex.LOG.warn("Lock problem while searching structural index: " + e.getMessage(), e);
            } finally {
                lock.release(Lock.READ_LOCK);
            }
        }
//        result.updateNoSort();
        return result;
    }
    
    private class FindElementsCallback implements BTreeCallback {
        byte type;
        DocumentSet docs;
        NewArrayNodeSet result;
        NodeSelector selector;

        FindElementsCallback(byte type, NewArrayNodeSet result, DocumentSet docs, NodeSelector selector) {
            this.type = type;
            this.result = result;
            this.docs = docs;
            this.selector = selector;
        }

        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            final byte[] key = value.getData();
            final NodeId nodeId = readNodeId(key, pointer);
            final DocumentImpl doc = docs.getDoc(readDocId(key));
            if (doc != null) {
                if (selector == null) {
                    final NodeProxy storedNode = new NodeProxy(doc, nodeId,
                        type == ElementValue.ATTRIBUTE ? Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE, pointer);
                    result.add(storedNode);
                } else {
                    final NodeProxy storedNode = selector.match(doc, nodeId);
                    if (storedNode != null) {
                        storedNode.setNodeType(type == ElementValue.ATTRIBUTE ? Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE);
                        storedNode.setInternalAddress(pointer);
                        result.add(storedNode);
                    }
                }
            }
            return true;
        }
    }

    private class FindDescendantsCallback implements BTreeCallback {
        int axis;
        byte type;
        NodeProxy ancestor;
        DocumentImpl doc;
        int contextId;
        NewArrayNodeSet result;
        boolean selfAsContext = false;

        FindDescendantsCallback(byte type, int axis, int contextId, NewArrayNodeSet result) {
        	this(type, axis, contextId, false, result);
        };
        
        FindDescendantsCallback(byte type, int axis, int contextId, boolean selfAsContext, NewArrayNodeSet result) {
            this.type = type;
            this.axis = axis;
            this.contextId = contextId;
            this.result = result;
            this.selfAsContext = selfAsContext;
        }

        void setAncestor(DocumentImpl doc, NodeProxy ancestor) {
            this.doc = doc;
            this.ancestor = ancestor;
        }

        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            final NodeId nodeId = readNodeId(value.getData(), pointer);

            boolean match = axis == Constants.DESCENDANT_SELF_AXIS || axis == Constants.DESCENDANT_ATTRIBUTE_AXIS;
            if (!match) {
                final int relation = nodeId.computeRelation(ancestor.getNodeId());
                match = (((axis == Constants.CHILD_AXIS) || (axis == Constants.ATTRIBUTE_AXIS)) && (relation == NodeId.IS_CHILD)) ||
                    ((axis == Constants.DESCENDANT_AXIS) && ((relation == NodeId.IS_DESCENDANT) || (relation == NodeId.IS_CHILD)));
            }
            if (match) {
                final NodeProxy storedNode =
                    new NodeProxy(doc, nodeId, type == ElementValue.ATTRIBUTE ? Node.ATTRIBUTE_NODE : Node.ELEMENT_NODE, pointer);
                result.add(storedNode);
                if (Expression.NO_CONTEXT_ID != contextId) {
                	if (selfAsContext)
                		{storedNode.addContextNode(contextId, storedNode);}
                	else
                		{storedNode.deepCopyContext(ancestor, contextId);}
                } else {
            		storedNode.copyContext(ancestor);
                }
                storedNode.addMatches(ancestor);
            }
            return true;
        }
    }
    
    public String getIndexId() {
        return NativeStructuralIndex.ID;
    }

    public String getIndexName() {
        return index.getIndexName();
    }

    public Object configure(IndexController controller, NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        return null;
    }

    public void setDocument(DocumentImpl doc) {
        setDocument(doc, StreamListener.UNKNOWN);
    }

    public void setDocument(DocumentImpl doc, int mode) {
        this.document = doc;
        this.mode = mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public DocumentImpl getDocument() {
        return document;
    }

    public int getMode() {
        return mode;
    }

    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean insert, boolean includeSelf) {
        // if a node is inserted, we do not need to reindex the parent
        return insert ? null : node;
    }

    private NativeStructuralStreamListener listener = new NativeStructuralStreamListener();
    
    public StreamListener getListener() {
        return listener;
    }

    public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
        // not applicable to this index
        return null;
    }

    public void flush() {
        switch (mode) {
            case StreamListener.STORE:
                processPending();
                break;
            case StreamListener.REMOVE_ALL_NODES:
                removeDocument(document);
                break;
            case StreamListener.REMOVE_SOME_NODES:
                removeSome();
        }
    }

    protected void removeSome() {
        if (pending.size() == 0)
            {return;}

        try {
            final Lock lock = index.btree.getLock();
            for (final Map.Entry<QName,List<NodeProxy>> entry: pending.entrySet()) {
                final QName qname = entry.getKey();
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    final List<NodeProxy> nodes = entry.getValue();
                    for (final NodeProxy proxy : nodes) {
                        final NodeId nodeId = proxy.getNodeId();
                        final byte[] key = computeKey(qname.getNameType(), qname, document.getDocId(), nodeId);
                        index.btree.removeValue(new Value(key));
                    }
                } catch (final LockException e) {
                    NativeStructuralIndex.LOG.warn("Failed to lock structural index: " + e.getMessage(), e);
                } catch (final Exception e) {
                    NativeStructuralIndex.LOG.warn("Exception caught while writing to structural index: " + e.getMessage(), e);
                } finally {
                    lock.release(Lock.WRITE_LOCK);
                }
            }
        } finally {
            pending.clear();
        }
    }

    protected void removeDocument(DocumentImpl docToRemove) {
        if (index.btree == null)
            {return;}
        final List<QName> qnames = getQNamesForDoc(docToRemove);
        for (final QName qname : qnames) {
            final byte[] fromKey = computeKey(qname.getNameType(), qname, docToRemove.getDocId());
            final byte[] toKey = computeKey(qname.getNameType(), qname, docToRemove.getDocId() + 1);
            final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(toKey));
            final Lock lock = index.btree.getLock();
            try {
                lock.acquire(Lock.WRITE_LOCK);
                index.btree.remove(query, null);
            } catch (final LockException e) {
                NativeStructuralIndex.LOG.warn("Failed to lock structural index: " + e.getMessage(), e);
            } catch (final Exception e) {
                NativeStructuralIndex.LOG.warn("Exception caught while removing structural index for document " +
                    docToRemove.getURI() + ": " + e.getMessage(), e);
            } finally {
                lock.release(Lock.WRITE_LOCK);
            }
        }
        removeQNamesForDoc(docToRemove);
    }

    protected void removeQNamesForDoc(DocumentImpl doc) {
        final byte[] fromKey = computeDocKey(doc.getDocId());
        final byte[] toKey = computeDocKey(doc.getDocId() + 1);
        final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(toKey));
        final Lock lock = index.btree.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            index.btree.remove(query, null);
        } catch (final LockException e) {
            NativeStructuralIndex.LOG.warn("Failed to lock structural index: " + e.getMessage(), e);
        } catch (final Exception e) {
            NativeStructuralIndex.LOG.warn("Exception caught while reading structural index for document " +
                doc.getURI() + ": " + e.getMessage(), e);
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }

    protected List<QName> getQNamesForDoc(DocumentImpl doc) {
        final List<QName> qnames = new ArrayList<QName>();
        if (index.btree == null)
            {return qnames;}
        final byte[] fromKey = computeDocKey(doc.getDocId());
        final byte[] toKey = computeDocKey(doc.getDocId() + 1);
        final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(toKey));
        final Lock lock = index.btree.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            index.btree.query(query, new BTreeCallback() {
                public boolean indexInfo(Value value, long pointer) throws TerminatedException {
                    final QName qname = readQName(value.getData());
                    qnames.add(qname);
                    return true;
                }
            });
        } catch (final LockException e) {
            NativeStructuralIndex.LOG.warn("Failed to lock structural index: " + e.getMessage(), e);
        } catch (final Exception e) {
            NativeStructuralIndex.LOG.warn("Exception caught while reading structural index for document " +
                doc.getURI() + ": " + e.getMessage(), e);
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
        return qnames;
    }

    @Override
    public void removeCollection(Collection collection, DBBroker broker, boolean reindex) throws PermissionDeniedException {
        for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
            final DocumentImpl doc = i.next();
            removeDocument(doc);
        }
    }

    public boolean checkIndex(DBBroker broker) {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * Collect index statistics. Used by functions like util:index-keys.
     *
     * @param context
     * @param docs The documents to which the index entries belong
     * @param contextSet ignored by this index
     * @param hints Some "hints" for retrieving the index entries. See such hints in
     * {@link org.exist.indexing.OrderedValuesIndex} and {@link org.exist.indexing.QNamedKeysIndex}.
     * @return
     */
    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map hints) {
        final Map<String, Occurrences> occurrences = new TreeMap<String, Occurrences>();
        for (final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl doc = i.next();
            final List<QName> qnames = getQNamesForDoc(doc);
            for (final QName qname : qnames) {
                final String name;
                if (qname.getNameType() == ElementValue.ATTRIBUTE) {
                    name = "@" + qname.getLocalName();
                } else {
                    name = qname.getLocalName();
                }
                final byte[] fromKey = computeKey(qname.getNameType(), qname, doc.getDocId());
                final byte[] toKey = computeKey(qname.getNameType(), qname, doc.getDocId() + 1);
                final IndexQuery query = new IndexQuery(IndexQuery.RANGE, new Value(fromKey), new Value(toKey));

                final Lock lock = index.btree.getLock();
                try {
                    lock.acquire(Lock.READ_LOCK);
                    index.btree.query(query, new BTreeCallback() {
                        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
                            Occurrences oc = occurrences.get(name);
                            if (oc == null) {
                                oc = new Occurrences(name);
                                occurrences.put(name, oc);
                                oc.addDocument(doc);
                                oc.addOccurrences(1);
                            } else {
                                oc.addOccurrences(1);
                                oc.addDocument(doc);
                            }
                            return true;
                        }
                    });
                } catch (final LockException e) {
                    NativeStructuralIndex.LOG.warn("Failed to lock structural index: " + e.getMessage(), e);
                } catch (final Exception e) {
                    NativeStructuralIndex.LOG.warn("Exception caught while reading structural index for document " +
                            doc.getURI() + ": " + e.getMessage(), e);
                } finally {
                    lock.release(Lock.READ_LOCK);
                }
            }
        }
        final Occurrences[] result = new Occurrences[occurrences.size()];
        int i = 0;
        for (Occurrences occ: occurrences.values()) {
            result[i++] = occ;
        }
        return result;
    }

    @Override
    public QueryRewriter getQueryRewriter(XQueryContext context) {
        return null;
    }

    public BTree getStorage() {
        return index.btree;
    }

    private void addNode(QName qname, NodeProxy proxy) {
        if (document.getDocId() != proxy.getDocument().getDocId()) {
    		throw new IllegalArgumentException("Document id ('" + document.getDocId() + "') and proxy id ('" +
    				proxy.getDocument().getDocId() + "') differ !");
    	}
        //Is this qname already pending ?
        List<NodeProxy> buf = pending.get(qname);
        if (buf == null) {
            //Create a node list
            buf = new ArrayList<NodeProxy>(50);
            pending.put(qname, buf);
        }
        //Add node's proxy to the list
        buf.add(proxy);
    }

    /**
     * Process the map of pending entries and store them into the btree.
     */
    private void processPending() {
        if (pending.size() == 0)
            {return;}

        try {
            final Lock lock = index.btree.getLock();
            for (final Map.Entry<QName,List<NodeProxy>> entry: pending.entrySet()) {
                final QName qname = entry.getKey();
                try {
                    lock.acquire(Lock.WRITE_LOCK);
                    final List<NodeProxy> nodes = entry.getValue();
                    for (final NodeProxy proxy : nodes) {
                        final NodeId nodeId = proxy.getNodeId();
                        final byte[] key = computeKey(qname.getNameType(), qname, document.getDocId(), nodeId);
                        index.btree.addValue(new Value(key), computeValue(proxy));
                    }
                    final Value docKey = new Value(computeDocKey(qname.getNameType(), document.getDocId(), qname));
                    if (index.btree.findValue(docKey) == -1) {
                        index.btree.addValue(docKey, 0);
                    }
                } catch (final LockException e) {
                    NativeStructuralIndex.LOG.warn("Failed to lock structural index: " + e.getMessage(), e);
                // } catch (ReadOnlyException e) {
                //    NativeStructuralIndex.LOG.warn("Read-only error: " + e.getMessage(), e);
                } catch (final Exception e) {
                    NativeStructuralIndex.LOG.warn("Exception caught while writing to structural index: " + e.getMessage(), e);
                } finally {
                    lock.release(Lock.WRITE_LOCK);
                }
            }
        } finally {
            pending.clear();
        }
    }

    private byte[] computeKey(byte type, QName qname, int documentId, NodeId nodeId) {
        final SymbolTable symbols = index.getDatabase().getSymbols();
        final short sym = symbols.getSymbol(qname.getLocalName());
        final short nsSym = symbols.getNSSymbol(qname.getNamespaceURI());
        final byte[] data = new byte[9 + nodeId.size()];

        data[0] = type;
        ByteConversion.shortToByteH(sym, data, 1);
        ByteConversion.shortToByteH(nsSym, data, 3);
        ByteConversion.intToByteH(documentId, data, 5);
        nodeId.serialize(data, 9);
        return data;
    }

    private byte[] computeKey(byte type, QName qname, int documentId) {
        final SymbolTable symbols = index.getDatabase().getSymbols();
        final short sym = symbols.getSymbol(qname.getLocalName());
        final short nsSym = symbols.getNSSymbol(qname.getNamespaceURI());
        final byte[] data = new byte[9];

        data[0] = type;
        ByteConversion.shortToByteH(sym, data, 1);
        ByteConversion.shortToByteH(nsSym, data, 3);
        ByteConversion.intToByteH(documentId, data, 5);
        return data;
    }

    private byte[] computeKey(byte type, int documentId) {
    	final byte[] data = new byte[5];

        data[0] = type;
        ByteConversion.intToByteH(documentId, data, 1);
        return data;
    }
    
    private byte[] computeDocKey(byte type, int documentId, QName qname) {
        final SymbolTable symbols = index.getDatabase().getSymbols();
        final short sym = symbols.getSymbol(qname.getLocalName());
        final short nsSym = symbols.getNSSymbol(qname.getNamespaceURI());
        final byte[] data = new byte[10];

        data[0] = 2;
        ByteConversion.intToByteH(documentId, data, 1);
        data[5] = type;
        ByteConversion.shortToByteH(sym, data, 6);
        ByteConversion.shortToByteH(nsSym, data, 8);
        return data;
    }

    private byte[] computeDocKey(int documentId) {
        final byte[] data = new byte[5];

        data[0] = 2;
        ByteConversion.intToByteH(documentId, data, 1);
        return data;
    }

    private long computeValue(NodeProxy proxy) {
        // dirty hack: encode the extra number of bits needed for the node id into the
        // storage address. this way, everything fits into the long address and
        // we don't need to change the btree.
        final long address = proxy.getInternalAddress();
        final short nodeIdLen = (short)(proxy.getNodeId().units() % 8);
        return address | ((long)(nodeIdLen << 24) & 0xFF000000L);
    }

    private int readDocId(byte[] key) {
        return ByteConversion.byteToIntH(key, 5);
    }

    private NodeId readNodeId(byte[] key, long value) {
        // extra number of bits of the node id is encoded in the long address
        short bits = (short)((value >>> 24) & 0xFFL);
        if (bits == 0)
            {bits = 8;}
        // compute total number of bits for node id
        final int units = (key.length - 10) * 8 + bits;
        return index.getDatabase().getNodeFactory().createFromData(units, key, 9);
    }

    private QName readQName(byte[] key) {
        final SymbolTable symbols = index.getDatabase().getSymbols();
        final byte type = key[5];
        final short sym = ByteConversion.byteToShortH(key, 6);
        final short nsSym = ByteConversion.byteToShortH(key, 8);
        final QName qname = new QName(symbols.getName(sym), symbols.getNamespace(nsSym));
        qname.setNameType(type);
        return qname;
    }

    private class NativeStructuralStreamListener extends AbstractStreamListener {

        private NativeStructuralStreamListener() {
            //Nothing to do
        }

        @Override
        public void startElement(Txn transaction, ElementImpl element, NodePath path) {
            super.startElement(transaction, element, path);
            if (mode == StreamListener.STORE || mode == StreamListener.REMOVE_SOME_NODES) {
                short indexType = RangeIndexSpec.NO_INDEX;
                if (element.getIndexType() != RangeIndexSpec.NO_INDEX)
                    {indexType = (short) element.getIndexType();}
                final NodeProxy proxy = new NodeProxy(document, element.getNodeId(), Node.ELEMENT_NODE, element.getInternalAddress());
                proxy.setIndexType(indexType);
                addNode(element.getQName(), proxy);
            }
        }

        @Override
        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            super.endElement(transaction, element, path);
        }

        @Override
        public void attribute(Txn transaction, AttrImpl attrib, NodePath path) {
            if (mode == StreamListener.STORE || mode == StreamListener.REMOVE_SOME_NODES) {
                short indexType = RangeIndexSpec.NO_INDEX;
                if (attrib.getIndexType() != RangeIndexSpec.NO_INDEX)
                    {indexType = (short) attrib.getIndexType();}
                final NodeProxy proxy = new NodeProxy(document, attrib.getNodeId(), Node.ATTRIBUTE_NODE,
                    attrib.getInternalAddress());
                proxy.setIndexType(indexType);
                addNode(attrib.getQName(), proxy);
            }
            super.attribute(transaction, attrib, path);
        }

        @Override
        public IndexWorker getWorker() {
            return NativeStructuralIndexWorker.this;
        }
    }

    @Override
    public void indexCollection(Collection col) {
    }

    @Override
    public void indexBinary(BinaryDocument doc) {
    }

    @Override
    public void removeIndex(XmldbURI url) {
    }
}