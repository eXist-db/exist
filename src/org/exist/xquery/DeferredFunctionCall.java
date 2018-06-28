/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.MemoryNodeSet;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

import java.util.Iterator;

public abstract class DeferredFunctionCall implements Sequence {
    
    private final static Logger LOG = LogManager.getLogger(DeferredFunctionCall.class);
    
    private FunctionSignature signature;
    private Sequence sequence = null;
    private XPathException caughtException = null;

    protected DeferredFunctionCall(FunctionSignature signature) {
        this.signature = signature;
    }
    
    private void realize() throws XPathException {
        if (caughtException != null) {
            throw caughtException;
        }
        if (sequence == null) {
            sequence = execute();
        }
    }
    
    protected FunctionSignature getSignature() {
        return signature;
    }
    
    protected abstract Sequence execute() throws XPathException;
    
    public void add(Item item) throws XPathException {
        realize();
        sequence.add(item);
    }

    public void addAll(Sequence other) throws XPathException {
        realize();
        sequence.addAll(other);
    }

    public void clearContext(int contextId) throws XPathException {
        if (sequence != null) {sequence.clearContext(contextId);}
    }

    public int conversionPreference(Class<?> javaClass) {
        if (sequence != null) 
            {return sequence.conversionPreference(javaClass);}
        else
            {return Integer.MAX_VALUE;}
    }

    public AtomicValue convertTo(int requiredType) throws XPathException {
        realize();
        return sequence.convertTo(requiredType);
    }

    public boolean effectiveBooleanValue() throws XPathException {
        realize();
        return sequence.effectiveBooleanValue();
    }

    public int getCardinality() {
        try {
            realize();
            return sequence.getCardinality();
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
            return 0;
        }
    }

    public DocumentSet getDocumentSet() {
        try {
            realize();
            return sequence.getDocumentSet();
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
            return null;
        }
    }

    public Iterator<Collection> getCollectionIterator() {
        try {
            realize();
            return sequence.getCollectionIterator();
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
            return null;
        }
    }

    public int getItemType() {
        try {
            realize();
            return sequence.getItemType();
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
            return Type.ANY_TYPE;
        }
    }

    @Override
    public long getItemCountLong() {
        try {
            realize();
            return sequence.getItemCountLong();
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
            return 0;
        }
    }

    public String getStringValue() throws XPathException {
        realize();
        return sequence.getStringValue();
    }

    @Override
    public Sequence tail() throws XPathException {
    	realize();
    	return sequence.tail();
    }
    
    public boolean hasMany() {
        try {
            realize();
            return sequence.hasMany();
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
            return false;
        }
    }

    public boolean hasOne() {
        try {
            realize();
            return sequence.hasOne();
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
            return false;
        }
    }

    public boolean isCached() {
        try {
            realize();
            return sequence.isCached();
        } catch (XPathException e) {
            caughtException = e;
            return false;
        }
    }

    public boolean isEmpty() {
        try {
            realize();
            return sequence.isEmpty();
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
            return false;
        }
    }

    public boolean isPersistentSet() {
        try {
            realize();
            return sequence.isPersistentSet();
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
            return false;
        }
    }

    public Item itemAt(int pos) {
        try {
            realize();
            return sequence.itemAt(pos);
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
            return null;
        }
    }

    public SequenceIterator iterate() throws XPathException {
        realize();
        return sequence.iterate();
    }

    public void removeDuplicates() {
        try {
            realize();
            sequence.removeDuplicates();
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
        }
    }

    public void setIsCached(boolean cached) {
        try {
            realize();
            sequence.setIsCached(cached);
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
        }
    }

    public void setSelfAsContext(int contextId) throws XPathException {
        try {
            realize();
            sequence.setSelfAsContext(contextId);
        } catch (XPathException e) {
            caughtException = e;
            LOG.error("Exception in deferred function: " + e.getMessage());
        }
    }

    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        realize();
        return sequence.toJavaObject(target);
    }

    public NodeSet toNodeSet() throws XPathException {
        realize();
        return sequence.toNodeSet();
    }

    public MemoryNodeSet toMemNodeSet() throws XPathException {
        realize();
        return sequence.toMemNodeSet();
    }

    public SequenceIterator unorderedIterator() throws XPathException {
        //try {
            realize();
            return sequence.unorderedIterator();
        
        /*} catch (XPathException e) {
            LOG.error("Exception in deferred function: " + e.getMessage());
            return null;
        }*/
    }


    @Override
    public void nodeMoved(NodeId oldNodeId, NodeHandle newNode) {
        // not applicable
    }

    public int getState() {
        return 0;
    }

    public boolean hasChanged(int previousState) {
        return true;
    }

    public boolean isCacheable() {
        return false;
    }

    @Override
    public void destroy(XQueryContext context, Sequence contextSequence) {
        // nothing to do
    }
}
