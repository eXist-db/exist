/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
package org.exist.xquery.functions.xmldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.UpdateListener;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
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
 * Implements eXist's xmldb:document() function.
 * 
 * @author wolf
 */
public class XMLDBDocument extends Function {
    private static final Logger logger = LogManager.getLogger(XMLDBDocument.class);
 

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("document", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Returns the documents $document-uris in the input sequence. " +  
            XMLDBModule.COLLECTION_URI +
            "If the input sequence is empty, " +
            "the function will load all documents in the database.",
			new SequenceType[] {
			    new FunctionParameterSequenceType("document-uris", Type.STRING, Cardinality.ONE_OR_MORE, "The document URIs")
			},
			new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the documents"),
			true, "See the standard fn:doc() function");

	private List<String> cachedArgs = null;
	private Sequence cached = null;
	private DocumentSet cachedDocs = null;
	private UpdateListener listener = null;
	
	/**
	 * @param context
	 */
	public XMLDBDocument(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
    public Sequence eval(Sequence contextSequence, Item contextItem)
	throws XPathException {
	
	DocumentSet docs = null;
	Sequence result = null;
	// check if the loaded documents should remain locked
        final boolean lockOnLoad = context.lockDocumentsOnLoad();
        boolean cacheIsValid = false;
	if (getArgumentCount() == 0) {
            // TODO: disabled cache for now as it may cause concurrency issues
            // better use compile-time inspection and maybe a pragma to mark those
            // sections in the query that can be safely cached
	    //	        if(cached != null) {
	    //	            result = cached;
	    //	            docs = cachedDocs;
	    //	        } else {
	    MutableDocumentSet mdocs = new DefaultDocumentSet();
            try {
                context.getBroker().getAllXMLResources(mdocs);
            } catch(final PermissionDeniedException pde) {
                LOG.error(pde.getMessage(), pde);
                throw new XPathException(this, pde);
            }
	    docs = mdocs;
	    //	        }
	} else {
	    List<String> args = getParameterValues(contextSequence, contextItem);
	    if(cachedArgs != null)
		{cacheIsValid = compareArguments(cachedArgs, args);}
	    if(cacheIsValid) {
		result = cached;
		docs = cachedDocs;
	    } else {
                MutableDocumentSet mdocs = new DefaultDocumentSet();
		for(int i = 0; i < args.size(); i++) {
		    try {
			final String next = (String)args.get(i);
			XmldbURI nextUri = new AnyURIValue(next).toXmldbURI();
			if(nextUri.getCollectionPath().length() == 0) {
			    throw new XPathException(this, "Invalid argument to " + XMLDBModule.PREFIX + ":document() function: empty string is not allowed here.");
			}
			if(nextUri.numSegments()==1) {                     
			    nextUri = context.getBaseURI().toXmldbURI().resolveCollectionPath(nextUri);
			}
			final DocumentImpl doc = context.getBroker().getResource(nextUri, Permission.READ);
			if(doc == null) { 
			    if (context.isRaiseErrorOnFailedRetrieval()) {
				throw new XPathException(this, ErrorCodes.FODC0002, "can not access '" + nextUri + "'");
			    }						
			}else {
			    mdocs.add(doc);
			}
		    } catch (final XPathException e) { //From AnyURIValue constructor
                        e.setLocation(line, column);
			logger.error("From AnyURIValue constructor:", e);

			throw e;
		    } catch (final PermissionDeniedException e) {
			logger.error("Permission denied", e);

			throw new XPathException(this, "Permission denied: unable to load document " + (String) args.get(i));
		    }
		}
                docs = mdocs;
                cachedArgs = args;
	    }
	}
	try {
            if(!cacheIsValid)
                // wait for pending updates
                {docs.lock(context.getBroker(), lockOnLoad);}
	    // wait for pending updates
	    if(result == null) {
		result = new ExtArrayNodeSet(docs.getDocumentCount(), 1);
                DocumentImpl doc;
		for (final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext();) {
                    doc = i.next();
		    result.add(new NodeProxy(doc)); //, -1, Node.DOCUMENT_NODE));
                    if(lockOnLoad) {
                        context.addLockedDocument(doc);
                    }
		}
	    }
	} catch (final LockException e) {
	    logger.error("Could not acquire lock on document set", e);

            throw new XPathException(this, "Could not acquire lock on document set.");
        } finally {
            if(!(cacheIsValid || lockOnLoad))
                // release all locks
                {docs.unlock();}
	}
	cached = result;
	cachedDocs = docs;
	registerUpdateListener();

	return result;
    }
	
	private List<String> getParameterValues(Sequence contextSequence, Item contextItem) throws XPathException {
        final List<String> args = new ArrayList<String>(getArgumentCount() + 10);
	    for(int i = 0; i < getArgumentCount(); i++) {
	        final Sequence seq =
				getArgument(i).eval(contextSequence, contextItem);
			for (final SequenceIterator j = seq.iterate(); j.hasNext();) {
				final Item next = j.nextItem();
				args.add(next.getStringValue());
			}
	    }
	    return args;
    }

    private boolean compareArguments(List<String> args1, List<String> args2) {
        if(args1.size() != args2.size())
            {return false;}
        for(int i = 0; i < args1.size(); i++) {
            final String arg1 = args1.get(i);
            final String arg2 = args2.get(i);
            if(!arg1.equals(arg2))
                {return false;}
        }
        return true;
    }
    
    protected void registerUpdateListener() {
        if (listener == null) {
            listener = new UpdateListener() {
                @Override
                public void documentUpdated(DocumentImpl document, int event) {
                    // clear all
                    cachedArgs = null;
                    cached = null;
                    cachedDocs = null;
                }

                @Override
                public void unsubscribe() {
                    XMLDBDocument.this.listener = null;
                }

                public void nodeMoved(NodeId oldNodeId, NodeHandle newNode) {
                    // not relevant
                }

                @Override
                public void debug() {
		    logger.debug("UpdateListener: Line: " + getLine() + ": " + XMLDBDocument.this.toString());
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
        if (!postOptimization) {
            cached = null;
            cachedArgs = null;
            cachedDocs = null;
            listener = null;
        }
    }
}
