/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class ExtCollection extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("collection", Function.BUILTIN_FUNCTION_NS),
            "Includes the documents contained in the specified collection " +
            "into the input sequence. eXist interprets the arguments as absolute paths " +
            "pointing to database collections, as for example, '/db/shakespeare/plays'. Documents " +
            "located in subcollections of a collection are included into the input " +
            "set.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
			true);

	private boolean includeSubCollections = false;
	
	private List cachedArgs = null;
	private Sequence cached = null;
	
	/**
	 * @param context
	 * @param signature
	 */
	public ExtCollection(XQueryContext context) {
		this(context, signature, true);
	}

	public ExtCollection(XQueryContext context, FunctionSignature signature, boolean inclusive) {
		super(context, signature);
		includeSubCollections = inclusive;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
	    List args = getParameterValues(contextSequence, contextItem);
		boolean cacheIsValid = false;
		if(cachedArgs != null)
		    cacheIsValid = compareArguments(cachedArgs, args);
		if(cacheIsValid) {
		    // if the expression occurs in a nested context, we might have cached the
            // document set
		    return cached;
        }
        
        if ( context.isProfilingEnabled() && context.getProfiler().verbosity() > 1) {
            context.getProfiler().start(this, "fn:collection: loading documents");
        }
        
		// check if the loaded documents should remain locked
        boolean lockOnLoad = context.lockDocumentsOnLoad();
        
        // build the document set
		DocumentSet docs = new DocumentSet();
		for (int i = 0; i < args.size(); i++) {
			String next = (String)args.get(i);
		    Collection coll = context.getBroker().getCollection(next);
            context.getProfiler().start(this, "fn:collection: loading collection: " + args.get(i));
		    if(coll != null)
		    	coll.allDocs(context.getBroker(), docs, includeSubCollections, true);
            context.getProfiler().end(this, "fn:collection: loading collection: " + args.get(i));
		}
        
        // iterate through all docs and create the node set
		NodeSet result = new ExtArrayNodeSet(docs.getLength(), 1);
		Lock dlock;
		DocumentImpl doc;
		for (Iterator i = docs.iterator(); i.hasNext();) {
		    doc = (DocumentImpl)i.next();
		    dlock = doc.getUpdateLock();
		    try {
		        dlock.acquire(Lock.READ_LOCK);
		        result.add(new NodeProxy(doc)); // , -1, Node.DOCUMENT_NODE));
                if(lockOnLoad) {
                    LOG.debug("Locking document: " + doc.getName());
                    context.getLockedDocuments().add(doc);
                }
		    } catch (LockException e) {
                LOG.info("Could not acquire read lock on document " + doc.getFileName());
            } finally {
                if(!lockOnLoad)
                    dlock.release(Lock.READ_LOCK);
		    }
		}
		cached = result;
		cachedArgs = args;
        
        if ( context.isProfilingEnabled() && context.getProfiler().verbosity() > 1) {
            context.getProfiler().end(this, "fn:collection: loading documents");
        }
		return result;
	}
	
	
    /**
     * @param contextSequence
     * @param contextItem
     * @throws XPathException
     */
    private List getParameterValues(Sequence contextSequence, Item contextItem) throws XPathException {
        List args = new ArrayList(getArgumentCount() + 10);
	    for(int i = 0; i < getArgumentCount(); i++) {
	        Sequence seq =
				getArgument(i).eval(contextSequence, contextItem);
			for (SequenceIterator j = seq.iterate(); j.hasNext();) {
				Item next = j.nextItem();
				args.add(next.getStringValue());
			}
	    }
	    return args;
    }

    private boolean compareArguments(List args1, List args2) {
        if(args1.size() != args2.size())
            return false;
        for(int i = 0; i < args1.size(); i++) {
            String arg1 = (String)args1.get(i);
            String arg2 = (String)args2.get(i);
            if(!arg1.equals(arg2))
                return false;
        }
        return true;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#resetState()
     */
    public void resetState() {
        cached = null;
        cachedArgs = null;
    }
}
