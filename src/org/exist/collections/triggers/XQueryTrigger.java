/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeSet;
import org.exist.memtree.SAXAdapter;
import org.exist.security.xacml.AccessContext;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A trigger that executes a user XQuery statement when invoked.
 * 
 * The XQuery source executed is the value of the parameter named "query" or the
 * query at the URL indicated by the parameter named "url".
 * 
 * Any additional parameters will be declared as external variables with the type xs:string
 * 
 * These external variables for the Trigger are accessible to the user XQuery statement
 * <code>xxx:type</code> : the type of event for the Trigger. Either "prepare" or "finish"
 * <code>xxx:collection</code> : the uri of the collection from which the event is triggered
 * <code>xxx:uri</code> : the uri of the document or collection from which the event is triggered
 * <code>xxx:new-uri</code> : the new uri of the document or collection from which the event is triggered
 * <code>xxx:event</code> : the kind of triggered event
 * xxx is the namespace prefix within the XQuery, can be set by the variable "bindingPrefix"
 * 
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
*/
public class XQueryTrigger extends FilteringTrigger implements DocumentTrigger, CollectionTrigger {

	private final static String EVENT_TYPE_PREPARE = "prepare";
	private final static String EVENT_TYPE_FINISH = "finish";
	
	private final static String DEFAULT_BINDING_PREFIX = "local:";
	
	public final static String [] EVENTS = {
		"CREATE-DOCUMENT", //0
		"CREATE-COLLECTION", //1
		"UPDATE-DOCUMENT", //2
		"UPDATE-COLLECTION", //3 ???
		"COPY-DOCUMENT", //4
		"COPY-COLLECTION", //5
		"MOVE-DOCUMENT", //6
		"MOVE-COLLECTION", //7
		"DELETE-DOCUMENT", //8
		"DELETE-COLLECTION" //9
	};
	

	private SAXAdapter adapter;
	private Set<TriggerEvents.EVENTS> events;
	private Collection collection = null;
	private String strQuery = null;
	private String urlQuery = null;
	private Properties userDefinedVariables = new Properties();
	
	/** Namespace prefix associated to trigger */
	private String bindingPrefix = null;
	private XQuery service;
	private ContentHandler originalOutputHandler;

    public final static String PEPARE_EXCEIPTION_MESSAGE = "Error during trigger prepare";

	
	public XQueryTrigger()
	{
		adapter = new SAXAdapter();
	}
	
	/**
	 * @link org.exist.collections.Trigger#configure(org.exist.storage.DBBroker, org.exist.collections.Collection, java.util.Map)
	 */
	public void configure(DBBroker broker, Collection parent, Map<String, List<?>> parameters) throws CollectionConfigurationException
	{
 		this.collection = parent;
 		
 		//for an XQuery trigger there must be at least
 		//one parameter to specify the XQuery
 		if(parameters != null)
 		{
 			List<String> paramEvents = (List<String>) parameters.get("event");
 			for (String event : paramEvents) {
 				events.addAll(TriggerEvents.convertFromOldDesign(event));
 				events.addAll(TriggerEvents.convertFromString(event));
 			}

 			List<String> urlQueries = (List<String>) parameters.get("url");
            urlQuery = urlQueries != null ? urlQueries.get(0) : null;

            List<String> strQueries = (List<String>) parameters.get("query");
 			strQuery = strQueries != null ? strQueries.get(0) : null;
 			
 			for(Iterator itParamName = parameters.keySet().iterator(); itParamName.hasNext();)
 			{
 				String paramName = (String)itParamName.next();
 				
 				//get the binding prefix (if any)
 				if(paramName.equals("bindingPrefix"))
 				{
 					String bindingPrefix = (String)parameters.get("bindingPrefix").get(0);
 					if(bindingPrefix != null && !"".equals(bindingPrefix.trim()))
 					{
 						this.bindingPrefix = bindingPrefix.trim() + ":";
 					}
 				}
 				
 				//get the URL of the query (if any)
 				else if(paramName.equals("url"))
 				{
 					urlQuery = (String)parameters.get("url").get(0);
 				}
 				
 				//get the query (if any)
 				else if(paramName.equals("query"))
 				{
 					strQuery = (String)parameters.get("query").get(0);
 				}
 				
 				//make any other parameters available as external variables for the query
 				else
 				{
                    //TODO could be enhanced to setup a sequence etc
 					userDefinedVariables.put(paramName, parameters.get(paramName).get(0));
 				}
 			}
 			
 			//set a default binding prefix if none was specified
 			if(this.bindingPrefix == null)
 			{
 				this.bindingPrefix = DEFAULT_BINDING_PREFIX;
 			}
 			
 			//old
 			if(urlQuery != null || strQuery != null)
 			{
				service = broker.getXQueryService();
				
				return;
 			}
 		}
 		
 		//no query to execute
 		LOG.error("XQuery Trigger for: '" + parent.getURI() + "' is missing its XQuery parameter");
	}
	
	/**
	 * Get's a Source for the Trigger's XQuery
	 * 
	 * @param the database broker
	 * 
	 * @return the Source for the XQuery 
	 */
	private Source getQuerySource(DBBroker broker)
	{
		Source querySource = null;
		
		//try and get the XQuery from a URL
		if(urlQuery != null)
		{
			try
			{
				querySource = SourceFactory.getSource(broker, null, urlQuery, false);
			}
			catch(Exception e)
			{
				LOG.error(e);
			}
		}
	
		//try and get the XQuery from a string
		else if(strQuery != null)
		{
			querySource = new StringSource(strQuery);
		}
	
		return querySource;
	}
	
	/**
	 * @link org.exist.collections.Trigger#prepare(java.lang.String, org.w3c.dom.Document)
	 */
	public void prepare(int event, DBBroker broker, Txn transaction, 
			XmldbURI documentPath, 
			DocumentImpl existingDocument) throws TriggerException {
		
		LOG.debug("Preparing " + eventToString(event) + "XQuery trigger for document: '" + documentPath + "'");
		prepare(event, broker, transaction, documentPath, (XmldbURI) null);
		
	}
	
	private void prepare(int event, DBBroker broker, Txn transaction,
			XmldbURI src, XmldbURI dst) throws TriggerException {
		
		//get the query
		Source query = getQuerySource(broker);
		if(query == null)
			return;        
                        
		// avoid infinite recursion by allowing just one trigger per thread		
		if(!TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforePrepare(this, src))
		{
			return;
		}
		TriggerStatePerThread.setTransaction(transaction);
		
		XQueryContext context = service.newContext(AccessContext.TRIGGER);
         //TODO : further initialisations ?
        CompiledXQuery compiledQuery;
        try
        {
        	//compile the XQuery
        	compiledQuery = service.compile(context, query);

        	//declare external variables
        	context.declareVariable(bindingPrefix + "type", EVENT_TYPE_PREPARE);
        	context.declareVariable(bindingPrefix + "event", new StringValue(eventToString(event)));
        	context.declareVariable(bindingPrefix + "collection", new AnyURIValue(collection.getURI()));
    		context.declareVariable(bindingPrefix + "uri", new AnyURIValue(src));
        	if (dst == null)
        		context.declareVariable(bindingPrefix + "new-uri", Sequence.EMPTY_SEQUENCE);
        	else 
        		context.declareVariable(bindingPrefix + "new-uri", new AnyURIValue(dst));
        	
        	// For backward compatibility
        	context.declareVariable(bindingPrefix + "eventType", EVENT_TYPE_PREPARE);
        	context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(eventToString(event)));
        	context.declareVariable(bindingPrefix + "collectionName", new AnyURIValue(collection.getURI()));
    		context.declareVariable(bindingPrefix + "documentName", new AnyURIValue(src));
        	
        	//declare user defined parameters as external variables
        	for(Iterator itUserVarName = userDefinedVariables.keySet().iterator(); itUserVarName.hasNext();)
        	{
        		String varName = (String)itUserVarName.next();
        		String varValue = userDefinedVariables.getProperty(varName);
        	
        		context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
        	}
        	
        }
        catch(XPathException e)
        {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
	    }
        catch(IOException e)
        {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
	    }

        //execute the XQuery
        try
        {
        	//TODO : should we provide another contextSet ?
	        NodeSet contextSet = NodeSet.EMPTY_SET;
			service.execute(compiledQuery, contextSet);
			//TODO : should we have a special processing ?
			LOG.debug("Trigger fired for prepare");
        }
        catch(XPathException e)
        {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
        }
        
	}
    
    /**
     * @link org.exist.collections.triggers.DocumentTrigger#finish(int, org.exist.storage.DBBroker, java.lang.String, org.w3c.dom.Document)
     */
    public void finish(int event, DBBroker broker, Txn transaction, 
    		XmldbURI documentPath, 
    		DocumentImpl document){
    	
    	LOG.debug("Finishing " + eventToString(event) + " XQuery trigger for document : '" + documentPath + "'");
    	finish(event, broker, transaction, documentPath, (XmldbURI) null);
    	
    }	
    
	private void finish(int event, DBBroker broker, Txn transaction, XmldbURI src, XmldbURI dst) {
		
    	//get the query
    	Source query = getQuerySource(broker);
		if (query == null)
			return;
    	
		// avoid infinite recursion by allowing just one trigger per thread
		if(!TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforeFinish(this, src))
		{
			return;
		}
		
        XQueryContext context = service.newContext(AccessContext.TRIGGER);
        CompiledXQuery compiledQuery = null;
        try
        {
        	//compile the XQuery
        	compiledQuery = service.compile(context, query);
        	
        	//declare external variables
        	context.declareVariable(bindingPrefix + "type", EVENT_TYPE_FINISH);
        	context.declareVariable(bindingPrefix + "event", new StringValue(eventToString(event)));
        	context.declareVariable(bindingPrefix + "collection", new AnyURIValue(collection.getURI()));
    		context.declareVariable(bindingPrefix + "uri", new AnyURIValue(src));
        	if (dst == null)
        		context.declareVariable(bindingPrefix + "new-uri", Sequence.EMPTY_SEQUENCE);
        	else 
        		context.declareVariable(bindingPrefix + "new-uri", new AnyURIValue(dst));
        	
        	// For backward compatibility
        	context.declareVariable(bindingPrefix + "eventType", EVENT_TYPE_FINISH);
        	context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(eventToString(event)));
        	context.declareVariable(bindingPrefix + "collectionName", new AnyURIValue(collection.getURI()));
    		context.declareVariable(bindingPrefix + "documentName", new AnyURIValue(src));
        	
        	//declare user defined parameters as external variables
        	for(Iterator itUserVarName = userDefinedVariables.keySet().iterator(); itUserVarName.hasNext();)
        	{
        		String varName = (String)itUserVarName.next();
        		String varValue = userDefinedVariables.getProperty(varName);
        	
        		context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
        	}
        	
        }
        catch(XPathException e)
        {
        	//Should never be reached
        	LOG.error(e);
	    }
        catch(IOException e)
        {
	    	//Should never be reached
        	LOG.error(e);
	    }

	    //execute the XQuery
        try
        {
        	//TODO : should we provide another contextSet ?
	        NodeSet contextSet = NodeSet.EMPTY_SET;	        
			service.execute(compiledQuery, contextSet);
			//TODO : should we have a special processing ?
        }
        catch (XPathException e)
        {
        	//Should never be reached
			LOG.error("Error during trigger finish", e);
        }
        
		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
		TriggerStatePerThread.setTransaction(null);
		LOG.debug("Trigger fired for finish");
		
	}

	public void startDocument() throws SAXException
	{
		originalOutputHandler = getOutputHandler();
		//TODO : uncomment when it works
		/*
		if (isValidating()) 
			setOutputHandler(adapter);	
		*/	
		super.startDocument();
	}	

	public void endDocument() throws SAXException
	{
		super.endDocument();
		
		setOutputHandler(originalOutputHandler);
		
		//if (!isValidating())
		//		return;				
		
        //XQueryContext context = service.newContext(AccessContext.TRIGGER);
        //TODO : futher initializations ?
        // CompiledXQuery compiledQuery;
        
        //try {
        	
        	// compiledQuery = 
        	//service.compile(context, query);
        	
        	//context.declareVariable(bindingPrefix + "validating", new BooleanValue(isValidating()));
        	//if (adapter.getDocument() == null)
        		//context.declareVariable(bindingPrefix + "document", Sequence.EMPTY_SEQUENCE);
        	//TODO : find the right method ;-)
        	/*
        	else
        		context.declareVariable(bindingPrefix + "document", (DocumentImpl)adapter.getDocument());
        	*/
	        	        
        //} catch (XPathException e) {
        	//query = null; //prevents future use
        //	throw new SAXException("Error during endDocument", e);
	    //} catch (IOException e) {
        	//query = null; //prevents future use
        //	throw new SAXException("Error during endDocument", e);
	    //}

	    //TODO : uncomment when it works
	    /*
        try {
        	//TODO : should we provide another contextSet ?
	        NodeSet contextSet = NodeSet.EMPTY_SET;	        
			//Sequence result = service.execute(compiledQuery, contextSet);
			//TODO : should we have a special processing ?
			LOG.debug("done.");
			
        } catch (XPathException e) {
        	query = null; //prevents future use
        	throw new SAXException("Error during endDocument", e);
		}	
		*/		
		
        //TODO : check that result is a document node
		//TODO : Stream result to originalOutputHandler 
	}
    
	/**
	 * Returns a String representation of the Trigger event
	 * 
	 * @param event The Trigger event
	 * 
	 * @return The String representation
	 */
    public static String eventToString(int event)
    {
    	return EVENTS[event];
    }
    
    //Collection's methods

	public void prepare(int event, DBBroker broker, Txn transaction, Collection collection, Collection newCollection) throws TriggerException {
		LOG.debug("Preparing " + eventToString(event) + "XQuery trigger for collection: '" + collection.getURI() + "'");
		
		//get the query
		Source query = getQuerySource(broker);
		if(query == null)
			return;        
                        
		// avoid infinite recursion by allowing just one trigger per thread		
		if(!TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforePrepare(this, collection.getURI()))
		{
			return;
		}
		TriggerStatePerThread.setTransaction(transaction);
		
		XQueryContext context = service.newContext(AccessContext.TRIGGER);
         //TODO : further initialisations ?
        CompiledXQuery compiledQuery;
        try
        {
        	//compile the XQuery
        	compiledQuery = service.compile(context, query);

        	//declare external variables
        	context.declareVariable(bindingPrefix + "eventType", EVENT_TYPE_PREPARE);
        	context.declareVariable(bindingPrefix + "collectionName", new AnyURIValue(collection.getURI()));
        	context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(eventToString(event)));
        	
        	//declare user defined parameters as external variables
        	for(Iterator itUserVarName = userDefinedVariables.keySet().iterator(); itUserVarName.hasNext();)
        	{
        		String varName = (String)itUserVarName.next();
        		String varValue = userDefinedVariables.getProperty(varName);
        	
        		context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
        	}
        	
    		//context.declareVariable(bindingPrefix + "collection", Sequence.EMPTY_SEQUENCE);
        }
        catch(XPathException e)
        {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
	    }
        catch(IOException e)
        {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
	    }

        //execute the XQuery
        try
        {
        	//TODO : should we provide another contextSet ?
	        NodeSet contextSet = NodeSet.EMPTY_SET;
			service.execute(compiledQuery, contextSet);
			//TODO : should we have a special processing ?
			LOG.debug("Trigger fired for prepare");
        }
        catch(XPathException e)
        {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
        }
	}

	public void finish(int event, DBBroker broker, Txn transaction, Collection collection, Collection newCollection) {
    	LOG.debug("Finishing " + eventToString(event) + " XQuery trigger for collection : '" + collection.getURI() + "'");
    	
    	//get the query
    	Source query = getQuerySource(broker);
		if (query == null)
			return;
    	
		// avoid infinite recursion by allowing just one trigger per thread
		if(!TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforeFinish(this, collection.getURI()))
		{
			return;
		}
		
        XQueryContext context = service.newContext(AccessContext.TRIGGER);
        CompiledXQuery compiledQuery = null;
        try
        {
        	//compile the XQuery
        	compiledQuery = service.compile(context, query);
        	
        	//declare external variables
        	context.declareVariable(bindingPrefix + "eventType", EVENT_TYPE_FINISH);
        	context.declareVariable(bindingPrefix + "collectionName", new AnyURIValue(collection.getURI()));
        	context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(eventToString(event)));

        	//declare user defined parameters as external variables
        	for(Iterator itUserVarName = userDefinedVariables.keySet().iterator(); itUserVarName.hasNext();)
        	{
        		String varName = (String)itUserVarName.next();
        		String varValue = userDefinedVariables.getProperty(varName);
        	
        		context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
        	}
        	
    		//context.declareVariable(bindingPrefix + "collection", Sequence.EMPTY_SEQUENCE);
        }
        catch(XPathException e)
        {
        	//Should never be reached
        	LOG.error(e);
	    }
        catch(IOException e)
        {
	    	//Should never be reached
        	LOG.error(e);
	    }

	    //execute the XQuery
        try
        {
        	//TODO : should we provide another contextSet ?
	        NodeSet contextSet = NodeSet.EMPTY_SET;	        
			service.execute(compiledQuery, contextSet);
			//TODO : should we have a special processing ?
        }
        catch (XPathException e)
        {
        	//Should never be reached
			LOG.error("Error during trigger finish", e);
        }
        
		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
		TriggerStatePerThread.setTransaction(null);
		LOG.debug("Trigger fired for finish");
	}

	@Override
	public void beforeCreateCollection(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.CREATE_COLLECTION)) {
			prepare(1, broker, transaction, uri, (XmldbURI) null);
		}
	}

	@Override
	public void afterCreateCollection(DBBroker broker, Txn transaction, Collection collection) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.CREATE_COLLECTION)) {
			finish(1, broker, transaction, collection.getURI(), (XmldbURI) null);
		}
	}

	@Override
	public void beforeCopyCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.COPY_COLLECTION)) {
			prepare(5, broker, transaction, collection.getURI(), newUri);
		}
	}

	@Override
	public void afterCopyCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.COPY_COLLECTION)) {
			finish(5, broker, transaction, collection.getURI(), newUri);
		}
	}

	@Override
	public void beforeMoveCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.MOVE_COLLECTION)) {
			prepare(7, broker, transaction, collection.getURI(), newUri);
		}
	}

	@Override
	public void afterMoveCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.MOVE_COLLECTION)) {
			finish(7, broker, transaction, collection.getURI(), newUri);
		}
	}

	@Override
	public void beforeDeleteCollection(DBBroker broker, Txn transaction, Collection collection) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.DELETE_COLLECTION)) {
			prepare(9, broker, transaction, collection.getURI(), (XmldbURI) null);
		}
	}

	@Override
	public void afterDeleteCollection(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.DELETE_COLLECTION)) {
			finish(9, broker, transaction, collection.getURI(), (XmldbURI) null);
		}
	}

	@Override
	public void beforeCreateDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.CREATE_DOCUMENT)) {
			prepare(0, broker, transaction, uri, (XmldbURI) null);
		}
	}

	@Override
	public void afterCreateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.CREATE_DOCUMENT)) {
			finish(0, broker, transaction, document.getURI(), (XmldbURI) null);
		}
	}

	@Override
	public void beforeUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.UPDATE_DOCUMENT)) {
			prepare(2, broker, transaction, document.getURI(), (XmldbURI) null);
		}
	}

	@Override
	public void afterUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.UPDATE_DOCUMENT)) {
			finish(2, broker, transaction, document.getURI(), (XmldbURI) null);
		}
	}

	@Override
	public void beforeCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.COPY_DOCUMENT)) {
			prepare(4, broker, transaction, document.getURI(), newUri);
		}
	}

	@Override
	public void afterCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.COPY_DOCUMENT)) {
			finish(4, broker, transaction, document.getURI(), newUri);
		}
	}

	@Override
	public void beforeMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.MOVE_DOCUMENT)) {
			prepare(6, broker, transaction, document.getURI(), newUri);
		}
	}

	@Override
	public void afterMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.MOVE_DOCUMENT)) {
			finish(6, broker, transaction, document.getURI(), newUri);
		}
	}

	@Override
	public void beforeDeleteDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.DELETE_DOCUMENT)) {
			prepare(8, broker, transaction, document.getURI(), (XmldbURI) null);
		}
	}

	@Override
	public void afterDeleteDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.DELETE_DOCUMENT)) {
			finish(8, broker, transaction, uri, (XmldbURI) null);
		}
	}

	/*public String toString() {
		return "collection=" + collection + "\n" +
			"modifiedDocument=" + TriggerStatePerThread.getModifiedDocument() + "\n" +
			( query != null ? query.substring(0, 40 ) : null );
	}*/
}
