/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
 *  $Id: ExtDocument.java 6320 2007-08-01 18:01:06Z ellefj $
 */
package org.exist.xquery.functions.xmldb;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.numbering.NodeId;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.UpdateListener;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements eXist's xmldb:document() function.
 * 
 * @author wolf
 */
public class FunDocument extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("document", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Returns the documents specified in the input sequence. " +  
            "The arguments are either document pathes like '" +
			DBBroker.ROOT_COLLECTION + "/shakespeare/plays/hamlet.xml' or " +
			"XMLDB URIs like 'xmldb:exist://localhost:8081/" +
			DBBroker.ROOT_COLLECTION + "/shakespeare/plays/hamlet.xml'. " +  
            "If the input sequence is empty, " +
            "the function will load all documents in the database.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
			true);

	private List cachedArgs = null;
	private Sequence cached = null;
	private DocumentSet cachedDocs = null;
	private UpdateListener listener = null;
	
	/**
	 * @param context
	 */
	public FunDocument(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
	    DocumentSet docs = null;
	    Sequence result = null;
	    // check if the loaded documents should remain locked
        boolean lockOnLoad = context.lockDocumentsOnLoad();
        boolean cacheIsValid = false;
	    if (getArgumentCount() == 0) {
	        if(cached != null) {
	            result = cached;
	            docs = cachedDocs;
	        } else {
		        docs = new DocumentSet();
		        context.getBroker().getAllXMLResources(docs);
	        }
	    } else {
		    List args = getParameterValues(contextSequence, contextItem);
			if(cachedArgs != null)
			    cacheIsValid = compareArguments(cachedArgs, args);
			if(cacheIsValid) {
			    result = cached;
			    docs = cachedDocs;
			} else {
				docs = new DocumentSet();
				for(int i = 0; i < args.size(); i++) {
					try {
						String next = (String)args.get(i);
						XmldbURI nextUri = new AnyURIValue(next).toXmldbURI();
						if(nextUri.getCollectionPath().length() == 0) {
							throw new XPathException("Invalid argument to fn:doc function: empty string is not allowed here.");
						}
	                    if(nextUri.numSegments()==1) {                     
	                    	nextUri = context.getBaseURI().toXmldbURI().resolveCollectionPath(nextUri);
	                    }
						DocumentImpl doc = (DocumentImpl) context.getBroker().getXMLResource(nextUri);
						if(doc != null) {
						    if(!doc.getPermissions().validate(context.getUser(), Permission.READ))
							    throw new XPathException("Insufficient privileges to read resource " + next);
							docs.add(doc);
						}
			        } catch (XPathException e) { //From AnyURIValue constructor
			        	e.setASTNode(getASTNode());
			            throw e;
			        } catch (PermissionDeniedException e) {
						throw new XPathException("Permission denied: unable to load document " + (String)args.get(i));
					}
				}
				cachedArgs = args;
			}
	    }
	    try {
            if(!cacheIsValid)
                // wait for pending updates
                docs.lock(context.getBroker(), lockOnLoad, true);
	        // wait for pending updates
			if(result == null) {
			    result = new ExtArrayNodeSet(docs.getLength(), 1);
                DocumentImpl doc;
				for (Iterator i = docs.iterator(); i.hasNext();) {
                    doc = (DocumentImpl) i.next();
					result.add(new NodeProxy(doc)); //, -1, Node.DOCUMENT_NODE));
                    if(lockOnLoad) {
                        context.addLockedDocument(doc);
                    }
				}
			}
	    } catch (LockException e) {
            throw new XPathException(getASTNode(), "Could not acquire lock on document set.");
        } finally {
            if(!(cacheIsValid || lockOnLoad))
                // release all locks
                docs.unlock(lockOnLoad);
	    }
		cached = result;
		cachedDocs = docs;
		registerUpdateListener();
		return result;
	}
	
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
    
    protected void registerUpdateListener() {
        if (listener == null) {
            listener = new UpdateListener() {
                public void documentUpdated(DocumentImpl document, int event) {
                    if (document == null || event == UpdateListener.ADD || event == UpdateListener.REMOVE) {
                        // clear all
                        cachedArgs = null;
                        cached = null;
                        cachedDocs = null;
                    } else {
                        if (cachedDocs != null
                                && cachedDocs.contains(document.getDocId())) {
                            cachedDocs = null;
                            cached = null;
                            cachedArgs = null;
                        }
                    }
                }

                public void unsubscribe() {
                    FunDocument.this.listener = null;
                }

                public void nodeMoved(NodeId oldNodeId, StoredNode newNode) {
                    // not relevant
                }

                public void debug() {
                	LOG.debug("UpdateListener: Line: " + getASTNode().getLine() + ": " + FunDocument.this.toString());                	
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
