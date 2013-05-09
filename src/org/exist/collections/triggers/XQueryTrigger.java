/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2012 The eXist Project
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.memtree.SAXAdapter;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.ProcessMonitor;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Expression;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.LiteralValue;
import org.exist.xquery.UserDefinedFunction;
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

        protected Logger LOG = Logger.getLogger(getClass());
    
	private final static String NAMESPACE = "http://exist-db.org/xquery/trigger";

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
	
	public final static QName beforeCreateCollection = new QName("before-create-collection", NAMESPACE); 
	public final static QName afterCreateCollection = new QName("after-create-collection", NAMESPACE); 

	public final static QName beforeUpdateCollection = new QName("before-update-collection", NAMESPACE); 
	public final static QName afterUpdateCollection = new QName("after-update-collection", NAMESPACE); 
	
	public final static QName beforeCopyCollection = new QName("before-copy-collection", NAMESPACE); 
	public final static QName afterCopyCollection = new QName("after-copy-collection", NAMESPACE); 

	public final static QName beforeMoveCollection = new QName("before-move-collection", NAMESPACE); 
	public final static QName afterMoveCollection = new QName("after-move-collection", NAMESPACE); 

	public final static QName beforeDeleteCollection = new QName("before-delete-collection", NAMESPACE); 
	public final static QName afterDeleteCollection = new QName("after-delete-collection", NAMESPACE); 

	public final static QName beforeCreateDocument = new QName("before-create-document", NAMESPACE); 
	public final static QName afterCreateDocument = new QName("after-create-document", NAMESPACE); 

	public final static QName beforeUpdateDocument = new QName("before-update-document", NAMESPACE); 
	public final static QName afterUpdateDocument = new QName("after-update-document", NAMESPACE); 
	
	public final static QName beforeCopyDocument = new QName("before-copy-document", NAMESPACE); 
	public final static QName afterCopyDocument = new QName("after-copy-document", NAMESPACE); 

	public final static QName beforeMoveDocument = new QName("before-move-document", NAMESPACE); 
	public final static QName afterMoveDocument = new QName("after-move-document", NAMESPACE); 

	public final static QName beforeDeleteDocument = new QName("before-delete-document", NAMESPACE); 
	public final static QName afterDeleteDocument = new QName("after-delete-document", NAMESPACE); 

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
	public void configure(DBBroker broker, Collection parent, Map<String, List<?>> parameters) throws TriggerException
	{
 		this.collection = parent;
 		
 		//for an XQuery trigger there must be at least
 		//one parameter to specify the XQuery
 		if(parameters != null)
 		{
 			events = new HashSet<TriggerEvents.EVENTS>();
 			final List<String> paramEvents = (List<String>) parameters.get("event");
 			if (paramEvents != null)
	 			for (final String event : paramEvents) {
	 				events.addAll(TriggerEvents.convertFromOldDesign(event));
	 				events.addAll(TriggerEvents.convertFromString(event));
	 			}

 			final List<String> urlQueries = (List<String>) parameters.get("url");
            urlQuery = urlQueries != null ? urlQueries.get(0) : null;

            final List<String> strQueries = (List<String>) parameters.get("query");
 			strQuery = strQueries != null ? strQueries.get(0) : null;
 			
 			for(final Iterator itParamName = parameters.keySet().iterator(); itParamName.hasNext();)
 			{
 				final String paramName = (String)itParamName.next();
 				
 				//get the binding prefix (if any)
 				if("bindingPrefix".equals(paramName))
 				{
 					final String bindingPrefix = (String)parameters.get("bindingPrefix").get(0);
 					if(bindingPrefix != null && !"".equals(bindingPrefix.trim()))
 					{
 						this.bindingPrefix = bindingPrefix.trim() + ":";
 					}
 				}
 				
 				//get the URL of the query (if any)
 				else if("url".equals(paramName))
 				{
 					urlQuery = (String)parameters.get("url").get(0);
 				}
 				
 				//get the query (if any)
 				else if("query".equals(paramName))
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
	private Source getQuerySource(DBBroker broker) {
		Source querySource = null;
		
		//try and get the XQuery from a URL
		if(urlQuery != null) {
			try {
				querySource = SourceFactory.getSource(broker, null, urlQuery, false);
			} catch(final Exception e) {
				LOG.error(e);
			}
		} else if(strQuery != null) {
			//try and get the XQuery from a string
			querySource = new StringSource(strQuery);
		}
	
		return querySource;
	}
	
	/**
	 * @link org.exist.collections.Trigger#prepareForExecution(java.lang.String, org.w3c.dom.Document)
	 */
	public void prepare(int event, DBBroker broker, Txn transaction, 
			XmldbURI documentPath, 
			DocumentImpl existingDocument) throws TriggerException {
		
//		LOG.debug("Preparing " + eventToString(event) + "XQuery trigger for document: '" + documentPath + "'");
//		prepareForExecution(event, broker, transaction, documentPath, (XmldbURI) null);
		
	}
	
	private void prepare(int event, DBBroker broker, Txn transaction,
			XmldbURI src, XmldbURI dst, boolean isCollection) throws TriggerException {
		
		//get the query
		final Source query = getQuerySource(broker);
		if(query == null)
			{return;}        
                        
		// avoid infinite recursion by allowing just one trigger per thread		
		if(!TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforePrepare(this, src)) {
			return;
		}
		TriggerStatePerThread.setTransaction(transaction);
		
		final XQueryContext context = service.newContext(AccessContext.TRIGGER);
         //TODO : further initialisations ?
        CompiledXQuery compiledQuery;
        try
        {
        	//compile the XQuery
        	compiledQuery = service.compile(context, query);

        	//declare external variables
        	context.declareVariable(bindingPrefix + "type", EVENT_TYPE_PREPARE);
        	context.declareVariable(bindingPrefix + "event", new StringValue(eventToString(event)));
        	
        	if (isCollection)
        		{context.declareVariable(bindingPrefix + "collection", new AnyURIValue(src));}
        	else
        		{context.declareVariable(bindingPrefix + "collection", new AnyURIValue(src.removeLastSegment()));}

        	context.declareVariable(bindingPrefix + "uri", new AnyURIValue(src));
        	if (dst == null)
        		{context.declareVariable(bindingPrefix + "new-uri", Sequence.EMPTY_SEQUENCE);}
        	else 
        		{context.declareVariable(bindingPrefix + "new-uri", new AnyURIValue(dst));}
        	
        	// For backward compatibility
        	context.declareVariable(bindingPrefix + "eventType", EVENT_TYPE_PREPARE);
        	context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(eventToString(event)));

        	if (isCollection)
        		{context.declareVariable(bindingPrefix + "collectionName", new AnyURIValue(src));}
        	else {
        		context.declareVariable(bindingPrefix + "collectionName", new AnyURIValue(src.removeLastSegment()));
        		context.declareVariable(bindingPrefix + "documentName", new AnyURIValue(src));
        	}
        	
        	//declare user defined parameters as external variables
        	for(final Iterator itUserVarName = userDefinedVariables.keySet().iterator(); itUserVarName.hasNext();)
        	{
        		final String varName = (String)itUserVarName.next();
        		final String varValue = userDefinedVariables.getProperty(varName);
        	
        		context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
        	}
        	
        } catch(final XPathException e) {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
	    } catch(final IOException e) {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
	    } catch (final PermissionDeniedException e) {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
		}

        //execute the XQuery
        try {
        	//TODO : should we provide another contextSet ?
	        final NodeSet contextSet = NodeSet.EMPTY_SET;
			service.execute(compiledQuery, contextSet);
			//TODO : should we have a special processing ?
			LOG.debug("Trigger fired for prepare");
        } catch(final XPathException e) {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
        } catch (final PermissionDeniedException e) {
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
    	
//    	LOG.debug("Finishing " + eventToString(event) + " XQuery trigger for document : '" + documentPath + "'");
//    	finish(event, broker, transaction, documentPath, (XmldbURI) null);
    	
    }	
    
	private void finish(int event, DBBroker broker, Txn transaction, XmldbURI src, XmldbURI dst, boolean isCollection) {
		
    	//get the query
    	final Source query = getQuerySource(broker);
		if (query == null)
			{return;}
    	
		// avoid infinite recursion by allowing just one trigger per thread
		if(!TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforeFinish(this, src))
			{return;}
		
        final XQueryContext context = service.newContext(AccessContext.TRIGGER);
        CompiledXQuery compiledQuery = null;
        try {
        	//compile the XQuery
        	compiledQuery = service.compile(context, query);
        	
        	//declare external variables
        	context.declareVariable(bindingPrefix + "type", EVENT_TYPE_FINISH);
        	context.declareVariable(bindingPrefix + "event", new StringValue(eventToString(event)));
        	
        	if (isCollection)
        		{context.declareVariable(bindingPrefix + "collection", new AnyURIValue(src));}
        	else
        		{context.declareVariable(bindingPrefix + "collection", new AnyURIValue(src.removeLastSegment()));}
    		
        	context.declareVariable(bindingPrefix + "uri", new AnyURIValue(src));
        	if (dst == null)
        		{context.declareVariable(bindingPrefix + "new-uri", Sequence.EMPTY_SEQUENCE);}
        	else 
        		{context.declareVariable(bindingPrefix + "new-uri", new AnyURIValue(dst));}
        	
        	// For backward compatibility
        	context.declareVariable(bindingPrefix + "eventType", EVENT_TYPE_FINISH);
        	context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(eventToString(event)));
        	if (isCollection)
        		{context.declareVariable(bindingPrefix + "collectionName", new AnyURIValue(src));}
        	else {
        		context.declareVariable(bindingPrefix + "collectionName", new AnyURIValue(src.removeLastSegment()));
        		context.declareVariable(bindingPrefix + "documentName", new AnyURIValue(src));
        	}
        	
        	//declare user defined parameters as external variables
        	for(final Iterator itUserVarName = userDefinedVariables.keySet().iterator(); itUserVarName.hasNext();)
        	{
        		final String varName = (String)itUserVarName.next();
        		final String varValue = userDefinedVariables.getProperty(varName);
        	
        		context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
        	}
        	
        } catch(final XPathException e) {
        	//Should never be reached
        	LOG.error(e);
	    } catch(final IOException e) {
	    	//Should never be reached
        	LOG.error(e);
	    } catch (final PermissionDeniedException e) {
        	//Should never be reached
        	LOG.error(e);
		}

	    //execute the XQuery
        try {
        	//TODO : should we provide another contextSet ?
	        final NodeSet contextSet = NodeSet.EMPTY_SET;	        
			service.execute(compiledQuery, contextSet);
			//TODO : should we have a special processing ?
        } catch (final XPathException e) {
        	//Should never be reached
			LOG.error("Error during trigger finish", e);
        } catch (final PermissionDeniedException e) {
        	//Should never be reached
        	LOG.error(e);
        }
        
		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
		TriggerStatePerThread.setTransaction(null);
		LOG.debug("Trigger fired for finish");
		
	}

	private CompiledXQuery getScript(boolean isBefore, DBBroker broker, Txn transaction, XmldbURI src) throws TriggerException {
		
		//get the query
		final Source query = getQuerySource(broker);
		if(query == null)
			{return null;}        
                        
		// avoid infinite recursion by allowing just one trigger per thread		
		if(isBefore && !TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforePrepare(this, src)) {
			return null;
		} else if (!isBefore && !TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforeFinish(this, src)) {
			return null;
		}
		TriggerStatePerThread.setTransaction(transaction);
		
		final XQueryContext context = service.newContext(AccessContext.TRIGGER);
        if (query instanceof DBSource) {
            context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI_PREFIX + ((DBSource)query).getDocumentPath().removeLastSegment().toString());
        }

        CompiledXQuery compiledQuery;
        try {
        	//compile the XQuery
        	compiledQuery = service.compile(context, query);

        	//declare user defined parameters as external variables
        	for(final Iterator itUserVarName = userDefinedVariables.keySet().iterator(); itUserVarName.hasNext();) {
        		final String varName = (String)itUserVarName.next();
        		final String varValue = userDefinedVariables.getProperty(varName);
        	
        		context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
        	}
        	
        	//reset & prepareForExecution for execution
        	compiledQuery.reset();

        	context.getWatchDog().reset();

            //do any preparation before execution
            context.prepareForExecution();

        	return compiledQuery;
        } catch(final XPathException e) {
            LOG.warn(e.getMessage(), e);
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
	    } catch(final IOException e) {
            LOG.warn(e.getMessage(), e);
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
	    } catch (final PermissionDeniedException e) {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
		}
	}
	
	private void execute(boolean isBefore, DBBroker broker, Txn transaction, QName functionName, XmldbURI ... urls) throws TriggerException {
		final XmldbURI src = urls[0];
		final CompiledXQuery compiledQuery = getScript(isBefore, broker, transaction, src);
		
		if (compiledQuery == null) {return;}
		
		ProcessMonitor pm = null;
		
		final XQueryContext context = compiledQuery.getContext();
        //execute the XQuery
        try {
        	
    		final UserDefinedFunction function = context.resolveFunction(functionName, urls.length);
    		if (function != null) {
    			final List<Expression> args = new ArrayList<Expression>(urls.length);
    			for (int i = 0; i < urls.length; i++)
    				args.add(new LiteralValue(context, new AnyURIValue(urls[i])));
    			
	    		pm = broker.getBrokerPool().getProcessMonitor();
	    		
	            context.getProfiler().traceQueryStart();
	            pm.queryStarted(context.getWatchDog());
	            
	            final FunctionCall call = new FunctionCall(context, function);
	            call.setArguments(args);
	            call.analyze(new AnalyzeContextInfo());
	    		call.eval(NodeSet.EMPTY_SET);
    		}
        } catch(final XPathException e) {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
        } finally {
        	if (pm != null) {
        		context.getProfiler().traceQueryEnd(context);
        		pm.queryCompleted(context.getWatchDog());
        	}
    		compiledQuery.reset();
    		context.reset();
        }

        if (!isBefore) {
        	TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
        	TriggerStatePerThread.setTransaction(null);
        	LOG.debug("Trigger fired 'after'");
        } else
        	{LOG.debug("Trigger fired 'before'");}
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
//		LOG.debug("Preparing " + eventToString(event) + "XQuery trigger for collection: '" + collection.getURI() + "'");
//		
//		//get the query
//		Source query = getQuerySource(broker);
//		if(query == null)
//			return;        
//                        
//		// avoid infinite recursion by allowing just one trigger per thread		
//		if(!TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforePrepare(this, collection.getURI()))
//		{
//			return;
//		}
//		TriggerStatePerThread.setTransaction(transaction);
//		
//		XQueryContext context = service.newContext(AccessContext.TRIGGER);
//         //TODO : further initialisations ?
//        CompiledXQuery compiledQuery;
//        try
//        {
//        	//compile the XQuery
//        	compiledQuery = service.compile(context, query);
//
//        	//declare external variables
//        	context.declareVariable(bindingPrefix + "eventType", EVENT_TYPE_PREPARE);
//        	context.declareVariable(bindingPrefix + "collectionName", new AnyURIValue(collection.getURI()));
//        	context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(eventToString(event)));
//        	
//        	//declare user defined parameters as external variables
//        	for(Iterator itUserVarName = userDefinedVariables.keySet().iterator(); itUserVarName.hasNext();)
//        	{
//        		String varName = (String)itUserVarName.next();
//        		String varValue = userDefinedVariables.getProperty(varName);
//        	
//        		context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
//        	}
//        	
//    		//context.declareVariable(bindingPrefix + "collection", Sequence.EMPTY_SEQUENCE);
//        }
//        catch(XPathException e)
//        {
//    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
//    		TriggerStatePerThread.setTransaction(null);
//        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
//	    }
//        catch(IOException e)
//        {
//    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
//    		TriggerStatePerThread.setTransaction(null);
//        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
//	    }
//
//        //execute the XQuery
//        try
//        {
//        	//TODO : should we provide another contextSet ?
//	        NodeSet contextSet = NodeSet.EMPTY_SET;
//			service.execute(compiledQuery, contextSet);
//			//TODO : should we have a special processing ?
//			LOG.debug("Trigger fired for prepareForExecution");
//        }
//        catch(XPathException e)
//        {
//    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
//    		TriggerStatePerThread.setTransaction(null);
//        	throw new TriggerException(PEPARE_EXCEIPTION_MESSAGE, e);
//        }
	}

	public void finish(int event, DBBroker broker, Txn transaction, Collection collection, Collection newCollection) {
//    	LOG.debug("Finishing " + eventToString(event) + " XQuery trigger for collection : '" + collection.getURI() + "'");
//    	
//    	//get the query
//    	Source query = getQuerySource(broker);
//		if (query == null)
//			return;
//    	
//		// avoid infinite recursion by allowing just one trigger per thread
//		if(!TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforeFinish(this, collection.getURI()))
//		{
//			return;
//		}
//		
//        XQueryContext context = service.newContext(AccessContext.TRIGGER);
//        CompiledXQuery compiledQuery = null;
//        try
//        {
//        	//compile the XQuery
//        	compiledQuery = service.compile(context, query);
//        	
//        	//declare external variables
//        	context.declareVariable(bindingPrefix + "eventType", EVENT_TYPE_FINISH);
//        	context.declareVariable(bindingPrefix + "collectionName", new AnyURIValue(collection.getURI()));
//        	context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(eventToString(event)));
//
//        	//declare user defined parameters as external variables
//        	for(Iterator itUserVarName = userDefinedVariables.keySet().iterator(); itUserVarName.hasNext();)
//        	{
//        		String varName = (String)itUserVarName.next();
//        		String varValue = userDefinedVariables.getProperty(varName);
//        	
//        		context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
//        	}
//        	
//    		//context.declareVariable(bindingPrefix + "collection", Sequence.EMPTY_SEQUENCE);
//        }
//        catch(XPathException e)
//        {
//        	//Should never be reached
//        	LOG.error(e);
//	    }
//        catch(IOException e)
//        {
//	    	//Should never be reached
//        	LOG.error(e);
//	    }
//
//	    //execute the XQuery
//        try
//        {
//        	//TODO : should we provide another contextSet ?
//	        NodeSet contextSet = NodeSet.EMPTY_SET;	        
//			service.execute(compiledQuery, contextSet);
//			//TODO : should we have a special processing ?
//        }
//        catch (XPathException e)
//        {
//        	//Should never be reached
//			LOG.error("Error during trigger finish", e);
//        }
//        
//		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
//		TriggerStatePerThread.setTransaction(null);
//		LOG.debug("Trigger fired for finish");
	}

	@Override
	public void beforeCreateCollection(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.CREATE_COLLECTION)) {
			prepare(1, broker, transaction, uri, (XmldbURI) null, true);
		} else
			{execute(true, broker, transaction, beforeCreateCollection, uri);}
	}

	@Override
	public void afterCreateCollection(DBBroker broker, Txn transaction, Collection collection) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.CREATE_COLLECTION)) {
			finish(1, broker, transaction, collection.getURI(), (XmldbURI) null, true);
		} else
			{execute(false, broker, transaction, afterCreateCollection, collection.getURI());}

	}

	@Override
	public void beforeCopyCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.COPY_COLLECTION)) {
			prepare(5, broker, transaction, collection.getURI(), newUri, true);
		} else
			{execute(true, broker, transaction, beforeCopyCollection, collection.getURI(), newUri);}
	}

	@Override
	public void afterCopyCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.COPY_COLLECTION)) {
			finish(5, broker, transaction, collection.getURI(), newUri, true);
		} else
			{execute(false, broker, transaction, afterCopyCollection, collection.getURI(), newUri);}
	}

	@Override
	public void beforeMoveCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.MOVE_COLLECTION)) {
			prepare(7, broker, transaction, collection.getURI(), newUri, true);
		} else
			{execute(true, broker, transaction, beforeMoveCollection, collection.getURI(), newUri);}
	}

	@Override
	public void afterMoveCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI oldUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.MOVE_COLLECTION)) {
			finish(7, broker, transaction, oldUri, collection.getURI(), true);
		} else
			{execute(false, broker, transaction, afterMoveCollection, oldUri, collection.getURI());}
	}

	@Override
	public void beforeDeleteCollection(DBBroker broker, Txn transaction, Collection collection) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.DELETE_COLLECTION)) {
			prepare(9, broker, transaction, collection.getURI(), (XmldbURI) null, true);
		} else
			{execute(true, broker, transaction, beforeDeleteCollection, collection.getURI());}
	}

	@Override
	public void afterDeleteCollection(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.DELETE_COLLECTION)) {
			finish(9, broker, transaction, collection.getURI(), (XmldbURI) null, true);
		} else
			{execute(false, broker, transaction, afterDeleteCollection, uri);}
	}

	@Override
	public void beforeCreateDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.CREATE_DOCUMENT)) {
			prepare(0, broker, transaction, uri, (XmldbURI) null, false);
		} else
			{execute(true, broker, transaction, beforeCreateDocument, uri);}
	}

	@Override
	public void afterCreateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.CREATE_DOCUMENT)) {
			finish(0, broker, transaction, document.getURI(), (XmldbURI) null, false);
		} else
			{execute(false, broker, transaction, afterCreateDocument, document.getURI());}
	}

	@Override
	public void beforeUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.UPDATE_DOCUMENT)) {
			prepare(2, broker, transaction, document.getURI(), (XmldbURI) null, false);
		} else
			{execute(true, broker, transaction, beforeUpdateDocument, document.getURI());}
	}

	@Override
	public void afterUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.UPDATE_DOCUMENT)) {
			finish(2, broker, transaction, document.getURI(), (XmldbURI) null, false);
		} else
			{execute(false, broker, transaction, afterUpdateDocument, document.getURI());}
	}

	@Override
	public void beforeCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.COPY_DOCUMENT)) {
			prepare(4, broker, transaction, document.getURI(), newUri, false);
		} else
			{execute(true, broker, transaction, beforeCopyDocument, document.getURI());}
	}

	@Override
	public void afterCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.COPY_DOCUMENT)) {
			finish(4, broker, transaction, document.getURI(), newUri, false);
		} else
			{execute(false, broker, transaction, afterCopyDocument, document.getURI());}
	}

	@Override
	public void beforeMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.MOVE_DOCUMENT)) {
			prepare(6, broker, transaction, document.getURI(), newUri, false);
		} else
			{execute(true, broker, transaction, beforeMoveDocument, document.getURI());}
	}

	@Override
	public void afterMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.MOVE_DOCUMENT)) {
			finish(6, broker, transaction, oldUri, document.getURI(), false);
		} else
			{execute(false, broker, transaction, afterMoveDocument, oldUri);}
	}

	@Override
	public void beforeDeleteDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.DELETE_DOCUMENT)) {
			prepare(8, broker, transaction, document.getURI(), (XmldbURI) null, false);
		} else
			{execute(true, broker, transaction, beforeDeleteDocument, document.getURI());}
	}

	@Override
	public void afterDeleteDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.DELETE_DOCUMENT)) {
			finish(8, broker, transaction, uri, (XmldbURI) null, false);
		} else
			{execute(false, broker, transaction, afterDeleteDocument, uri);}
	}

	@Override
	public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
		// TODO Auto-generated method stub
	}

	@Override
	public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
		// TODO Auto-generated method stub
	}

	/*public String toString() {
		return "collection=" + collection + "\n" +
			"modifiedDocument=" + TriggerStatePerThread.getModifiedDocument() + "\n" +
			( query != null ? query.substring(0, 40 ) : null );
	}*/
}
