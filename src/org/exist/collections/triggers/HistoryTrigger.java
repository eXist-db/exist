/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2004-2010 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.collections.triggers;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.DateTimeValue;

/**
 * This collection trigger will save all old versions of documents before
 * they are overwritten or removed. The old versions are kept in the
 * 'history root' which is by default '<code>/db/history</code>', but can be 
 * changed with the parameter '<code>root</code>'.
 * You need to configure this trigger for every collection whose history you
 * want to preserve, by modifying '<code>collection.xconf</code>' such that it
 * resembles this:
 *
 * <pre>
 *   &lt;?xml version='1.0'?>
 *   &lt;collection xmlns='http://exist-db.org/collection-config/1.0'>
 *     &lt;triggers>
 *       &lt;trigger 
 *         event='update'
 *         class='org.exist.collections.triggers.HistoryTrigger'
 *       />
 *       &lt;trigger
 *         event='remove'
 *         class='org.exist.collections.triggers.HistoryTrigger'
 *       />
 *     &lt;/triggers>
 *   &lt;/collection>
 * </pre>
 *
 * @author Mark Spanbroek
 * @see org.exist.collections.triggers.Trigger
 */
public class HistoryTrigger extends FilteringTrigger implements DocumentTrigger
{

    protected XmldbURI rootPath = XmldbURI.ROOT_COLLECTION_URI.append("history");

    public void configure(DBBroker broker, Collection parent, Map<String, List<?>> parameters) throws TriggerException
    {
        super.configure(broker, parent, parameters);
        
        if(parameters.containsKey("root"))
        {
            try
            {
            	rootPath = XmldbURI.xmldbUriFor(parameters.get("root").get(0).toString());
            }
            catch(URISyntaxException e)
            {
            	throw new TriggerException(e);
            }
        }
    }
    
    public void prepare(int event, DBBroker broker, Txn transaction, XmldbURI documentName, DocumentImpl doc) throws TriggerException {
    	makeCopy(broker, transaction, doc);
    }

    public void makeCopy(DBBroker broker, Txn transaction, DocumentImpl doc) throws TriggerException
    {
    	if(doc == null)
    		return;
        
    	// construct the destination path
   	  	XmldbURI path = rootPath.append(doc.getURI());
        
   	  	try
   	  	{
   	  		//construct the destination document name
   	  		String dtValue = new DateTimeValue(new Date(doc.getMetadata().getLastModified())).getStringValue();
   	  		dtValue = dtValue.replaceAll(":", "-"); // multiple ':' are not allowed in URI so use '-'
   	  		dtValue = dtValue.replaceAll("\\.", "-"); // as we are using '-' instead of ':' do the same for '.'
   	  		XmldbURI name = XmldbURI.create(dtValue);
        
   	  		// create the destination document
        
   	  		//TODO : how is the transaction handled ? It holds the locks ! 
   	  		Collection destination = broker.getOrCreateCollection(transaction, path);
   	  		broker.saveCollection(transaction, destination);
   	  		broker.copyResource(transaction, doc, destination, name);
   	  	}
   	  	catch(XPathException xpe)
   	  	{
   	  		throw new TriggerException(xpe);
   	  	}
   	  	catch(IOException exception)
   	  	{
   	  		throw new TriggerException(exception);
   	  	}
   	  	catch(PermissionDeniedException exception)
   	  	{
   	  		throw new TriggerException(exception);
   	  	}
   	  	catch(LockException exception)
   	  	{
   	  		throw new TriggerException(exception);
   	  	} catch (EXistException exception) {
   	  		throw new TriggerException(exception);
		}
    }
    
	public void finish(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl document)
    {
    }

	@Override
	public void beforeCreateDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
	}

	@Override
	public void afterCreateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void beforeUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
    	makeCopy(broker, transaction, document);
	}

	@Override
	public void afterUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void beforeCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
    	makeCopy(broker, transaction, document);
	}

	@Override
	public void afterCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void beforeDeleteDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
    	makeCopy(broker, transaction, document);
	}

	@Override
	public void beforeMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
    	makeCopy(broker, transaction, document);
	}

	@Override
	public void afterMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void afterDeleteDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
	}
}