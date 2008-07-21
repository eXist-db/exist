/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *
 *  Modifications Copyright (C) 2004 Luigi P. Bai
 *  finder@users.sf.net
 *  Licensed as below under the LGPL.
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
 */
package org.exist.xquery.functions.xmldb;

import org.exist.dom.NodeProxy;
import org.exist.xmldb.LocalCollection;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

public abstract class XMLDBAbstractCollectionManipulator extends BasicFunction {
	private final boolean errorIfAbsent;
	
	public XMLDBAbstractCollectionManipulator(XQueryContext context, FunctionSignature signature) {
		this(context, signature, true);
	}
	public XMLDBAbstractCollectionManipulator(XQueryContext context, FunctionSignature signature, boolean errorIfAbsent) {
		super(context, signature);
		this.errorIfAbsent = errorIfAbsent;
	}
	
	protected LocalCollection createLocalCollection(String name) throws XMLDBException {
		try {
			return new LocalCollection(context.getUser(), context.getBroker().getBrokerPool(), new AnyURIValue(name).toXmldbURI(), context.getAccessContext());
		} catch(XPathException e) {
			throw new XMLDBException(ErrorCodes.INVALID_URI,e);
		}
	}
	
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
        
        if (0 == args.length)
            throw new XPathException(getASTNode(), "Expected a collection as the first argument.");
        
        boolean collectionNeedsClose = false;
        
        
        Collection collection = null;
        Item item = args[0].itemAt(0);
        if(Type.subTypeOf(item.getType(), Type.NODE))
        {
        	NodeValue node = (NodeValue)item;
        	LOG.debug("Found node");
        	if(node.getImplementationType() == NodeValue.PERSISTENT_NODE)
        	{
        		org.exist.collections.Collection internalCol = ((NodeProxy)node).getDocument().getCollection();
        		LOG.debug("Found node");
        		try
        		{
        			//TODO: use xmldbURI
					collection = createLocalCollection(internalCol.getURI().toString());
					LOG.debug("Loaded collection " + collection.getName());
				}
        		catch(XMLDBException e)
        		{
					throw new XPathException(getASTNode(), "Failed to access collection: " + internalCol.getURI(), e);
				}
        	}
        	else
        		return Sequence.EMPTY_SEQUENCE;
        }
        
        if(collection == null)
        {
        	//Otherwise, just extract the name as a string:
            String collectionURI = args[0].getStringValue();
            if(collectionURI != null)
            {
                try
                {
                	if (!collectionURI.startsWith("xmldb:"))
                    {
                        // Must be a LOCAL collection
                        collection = createLocalCollection(collectionURI);
                    }
                    else if(collectionURI.startsWith("xmldb:exist:///"))
                    {
                    	// Must be a LOCAL collection
                        collection = createLocalCollection(collectionURI.replaceFirst("xmldb:exist://", ""));
                    }
                    else if(collectionURI.startsWith("xmldb:exist://localhost"))
                    {
                    	// Must be a LOCAL collection
                        collection = createLocalCollection(collectionURI.replaceFirst("xmldb:exist://localhost", ""));
                    }
                    else if(collectionURI.startsWith("xmldb:exist://127.0.0.1"))
                    {
                    	// Must be a LOCAL collection
                        collection = createLocalCollection(collectionURI.replaceFirst("xmldb:exist://127.0.0.1", ""));
                    }
                    else
                    {
                        // Right now, the collection is retrieved as GUEST. Need to figure out how to
                        // get user information into the URL?
                        collection = org.xmldb.api.DatabaseManager.getCollection(collectionURI);
                    }
                }
                catch(XMLDBException xe)
                {
                    if(errorIfAbsent)
                        throw new XPathException(getASTNode(), "Could not locate collection: "+collectionURI, xe);
                    collection = null;
                }
            }
            if(collection == null && errorIfAbsent)
            {
                throw new XPathException(getASTNode(), "Unable to find collection: " + collectionURI);
            }
        }
        
        Sequence s = Sequence.EMPTY_SEQUENCE;
        try
        {
            s = evalWithCollection(collection, args, contextSequence);
        }
        finally
        {
            if(collectionNeedsClose && collection != null)
            {
                try
            	{
                	collection.close();
            	}
            	catch(Exception e)
            	{
            		throw new XPathException(getASTNode(), "Unable to close collection", e);
        		}
            }
        }
        return s;
	}

    abstract protected Sequence evalWithCollection(Collection c, Sequence[] args, Sequence contextSequence) throws XPathException;
}
