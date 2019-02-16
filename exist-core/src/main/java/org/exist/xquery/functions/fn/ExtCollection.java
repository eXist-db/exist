/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2009 The eXist Project
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.collections.Collection;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NewArrayNodeSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.UpdateListener;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.xmldb.XMLDBModule;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author wolf
 */
public class ExtCollection extends Function {

    protected static final Logger logger = LogManager.getLogger(ExtCollection.class);

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("collection", Function.BUILTIN_FUNCTION_NS),
            "Returns the documents contained in the collections specified in " +
            "the input sequence. " + XMLDBModule.COLLECTION_URI +
            " Documents contained in subcollections are also included. If no value is supplied, the statically know documents are used, for the REST Server this could be the addressed collection.",
            new SequenceType[] {
                //Different from the offical specs
                new FunctionParameterSequenceType("collection-uris", Type.STRING,
                    Cardinality.ZERO_OR_MORE, "The collection-URIs for which to include the documents")},
                new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, 
                    "The document nodes contained in or under the given collections"),
            true);

    private boolean includeSubCollections = false;
    private UpdateListener listener = null;

    /**
     * @param context
     */
    public ExtCollection(XQueryContext context) {
        this(context, signature, true);
    }

	public ExtCollection(XQueryContext context, FunctionSignature signature, boolean inclusive) {
		super(context, signature);
		includeSubCollections = inclusive;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        final List<String> args = getParameterValues(contextSequence, contextItem);
        // TODO: disabled cache for now as it may cause concurrency issues
        // better use compile-time inspection and maybe a pragma to mark those
        // sections in the query that can be safely cached
        // boolean cacheIsValid = false;
        // if (cachedArgs != null)
        //    cacheIsValid = compareArguments(cachedArgs, args);
        // if (cacheIsValid) {
             //If the expression occurs in a nested context, we might have cached the
             //document set
             //if (context.getProfiler().isEnabled())
                 //context.getProfiler().end(this, "fn:collection: loading documents", cached);
             //return cached;
        // }
        //Build the document set
        DocumentSet docs = null;
        try {
            if (args.size() == 0) {
                //TODO : add default collection to the context
                //If the value of the default collection is undefined an error is raised [err:FODC0002].
                docs = context.getStaticallyKnownDocuments();
            } else {
                MutableDocumentSet ndocs = new DefaultDocumentSet();
                for (final String next : args) {
                    final XmldbURI uri = new AnyURIValue(next).toXmldbURI();
                    final Collection coll = context.getBroker().getCollection(uri);
                    if (coll == null) {
                        if (context.isRaiseErrorOnFailedRetrieval()) {
                            throw new XPathException("FODC0002: can not access collection '" + uri + "'");
                        }
                    } else {
                        if (context.inProtectedMode())
                            {context.getProtectedDocs().getDocsByCollection(coll, ndocs);}
                        else
                            {coll.allDocs(context.getBroker(), ndocs,
                                includeSubCollections, context.getProtectedDocs());}
                    }
                }
                docs = ndocs;
            }
        } catch (final XPathException e) { //From AnyURIValue constructor
            e.setLocation(line, column);
            throw new XPathException("FODC0002: " + e.getMessage());
        } catch(final PermissionDeniedException pde) {
            throw new XPathException("FODC0002: can not access collection '" + pde.getMessage() + "'");
            
        }
        // iterate through all docs and create the node set
        final NodeSet result = new NewArrayNodeSet();
        Lock dlock;
        DocumentImpl doc;
        for (final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext();) {
            doc = i.next();
            dlock = doc.getUpdateLock();
            boolean lockAcquired = false;
            try {
                if (!context.inProtectedMode() && !dlock.hasLock()) {
                    dlock.acquire(LockMode.READ_LOCK);
                    lockAcquired = true;
                }
                result.add(new NodeProxy(doc)); // , -1, Node.DOCUMENT_NODE));
            } catch (final LockException e) {
                throw new XPathException(e.getMessage());
            } finally {
                if (lockAcquired)
                    {dlock.release(LockMode.READ_LOCK);}
            }
        }
        registerUpdateListener();
        if (context.getProfiler().isEnabled())
               {context.getProfiler().end(this, "", result);}
        return result;
    }

    /**
     * @param contextSequence
     * @param contextItem
     * @throws XPathException
     */
    private List<String> getParameterValues(Sequence contextSequence,
            Item contextItem) throws XPathException {
        final List<String> args = new ArrayList<String>(getArgumentCount() + 10);
        for (int i = 0; i < getArgumentCount(); i++) {
            final Sequence seq = getArgument(i).eval(contextSequence, contextItem);
            for (final SequenceIterator j = seq.iterate(); j.hasNext();) {
                final Item next = j.nextItem();
                args.add(next.getStringValue());
            }
        }
        return args;
    }

    protected void registerUpdateListener() {
        if (listener == null) {
            listener = new UpdateListener() {

                @Override
                public void documentUpdated(DocumentImpl document, int event) {
                    //Nothing to do (previously was cache management)
                }

                @Override
                public void unsubscribe() {
                    ExtCollection.this.listener = null;
                }

                @Override
                public void nodeMoved(NodeId oldNodeId, NodeHandle newNode) {
                    // not relevant
                }

                @Override
                public void debug() {
                    LOG.debug("UpdateListener: Line: " + getLine() + ": " +
                        ExtCollection.this.toString());
                }
            };
            context.registerUpdateListener(listener);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#resetState()
     */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        //Nothing more to do (previously was cache management)
    }
}
