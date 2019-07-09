/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
package org.exist.collections.triggers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.ProcessMonitor;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;

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
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 * @author <a href="mailto:adam.retter@devon.gov.uk">Adam Retter</a>
 * @author <a href="mailto:gazdovsky@gmail.com">Evgeny Gazdovsky</a>
*/
public class XQueryTrigger extends SAXTrigger implements DocumentTrigger, CollectionTrigger {

    protected Logger LOG = LogManager.getLogger(getClass());
    
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

	private Set<TriggerEvents.EVENTS> events;
	private Collection collection = null;
	private String strQuery = null;
	private String urlQuery = null;
	private Properties userDefinedVariables = new Properties();
	
	/** Namespace prefix associated to trigger */
	private String bindingPrefix = null;
	private XQuery service;

    public final static String PREPARE_EXCEPTION_MESSAGE = "Error during trigger prepare";
	
	/**
	 * {@link org.exist.collections.triggers.Trigger#configure(DBBroker, Txn, Collection, Map)}
	 */
    @Override
	public void configure(DBBroker broker, Txn transaction, Collection parent, Map<String, List<?>> parameters) throws TriggerException
	{
 		this.collection = parent;
 		
 		//for an XQuery trigger there must be at least
 		//one parameter to specify the XQuery
 		if(parameters != null)
 		{
 			events = new HashSet<>();
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

			for (final Map.Entry<String, List<?>> entry : parameters.entrySet()) {
 				final String paramName = entry.getKey();
				final Object paramValue = entry.getValue().get(0);

 				//get the binding prefix (if any)
 				if("bindingPrefix".equals(paramName)) {
					final String bindingPrefix = (String) paramValue;
 					if(bindingPrefix != null && !bindingPrefix.trim().isEmpty()) {
 						this.bindingPrefix = bindingPrefix.trim() + ":";
 					}
 				}

 				//get the URL of the query (if any)
 				else if("url".equals(paramName)) {
					urlQuery = (String) paramValue;
 				}

 				//get the query (if any)
 				else if("query".equals(paramName)) {
					strQuery = (String) paramValue;
 				}

 				//make any other parameters available as external variables for the query
 				else {
                    //TODO could be enhanced to setup a sequence etc
 					userDefinedVariables.put(paramName, paramValue);
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
				service = broker.getBrokerPool().getXQueryService();
				
				return;
 			}
 		}
 		
 		//no query to execute
 		LOG.error("XQuery Trigger for: '" + parent.getURI() + "' is missing its XQuery parameter");
	}
	
	/**
	 * Get's a Source for the Trigger's XQuery
	 * 
	 * @param broker the database broker
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
		
		final XQueryContext context = new XQueryContext(broker.getBrokerPool());
         //TODO : further initialisations ?
        CompiledXQuery compiledQuery;
        try
        {
        	//compile the XQuery
        	compiledQuery = service.compile(broker, context, query);

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
        	
        } catch(final XPathException | IOException | PermissionDeniedException e) {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PREPARE_EXCEPTION_MESSAGE, e);
	    }

        //execute the XQuery
        try {
        	//TODO : should we provide another contextSet ?
	        final NodeSet contextSet = NodeSet.EMPTY_SET;
			service.execute(broker, compiledQuery, contextSet);
			//TODO : should we have a special processing ?
			LOG.debug("Trigger fired for prepare");
        } catch(final XPathException | PermissionDeniedException e) {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PREPARE_EXCEPTION_MESSAGE, e);
        }
    }
    
	private void finish(int event, DBBroker broker, Txn transaction, XmldbURI src, XmldbURI dst, boolean isCollection) {
		
    	//get the query
    	final Source query = getQuerySource(broker);
		if (query == null)
			{return;}
    	
		// avoid infinite recursion by allowing just one trigger per thread
		if(!TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforeFinish(this, src))
			{return;}
		
        final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        CompiledXQuery compiledQuery = null;
        try {
        	//compile the XQuery
        	compiledQuery = service.compile(broker, context, query);
        	
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
        	
        } catch(final XPathException | IOException | PermissionDeniedException e) {
        	//Should never be reached
        	LOG.error(e);
	    }

        //execute the XQuery
        try {
        	//TODO : should we provide another contextSet ?
	        final NodeSet contextSet = NodeSet.EMPTY_SET;	        
			service.execute(broker, compiledQuery, contextSet);
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
		
		final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        if (query instanceof DBSource) {
            context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI_PREFIX + ((DBSource)query).getDocumentPath().removeLastSegment().toString());
        }

        CompiledXQuery compiledQuery;
        try {
        	//compile the XQuery
        	compiledQuery = service.compile(broker, context, query);

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
        } catch(final XPathException | IOException | PermissionDeniedException e) {
            LOG.warn(e.getMessage(), e);
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PREPARE_EXCEPTION_MESSAGE, e);
	    }
    }
	
	private void execute(boolean isBefore, DBBroker broker, Txn transaction, QName functionName, XmldbURI src, XmldbURI dst) throws TriggerException {
		final CompiledXQuery compiledQuery = getScript(isBefore, broker, transaction, src);
		
		if (compiledQuery == null) {return;}
		
		ProcessMonitor pm = null;
		
		final XQueryContext context = compiledQuery.getContext();
        //execute the XQuery
        try {
            int nParams = 1;
            if (dst != null)
                nParams = 2;

            final UserDefinedFunction function = context.resolveFunction(functionName, nParams);
            if (function != null) {
                final List<Expression> args = new ArrayList<>(nParams);
                if (isBefore) {
                    args.add(new LiteralValue(context, new AnyURIValue(src)));
                    if (dst != null)
                        args.add(new LiteralValue(context, new AnyURIValue(dst)));
                } else {
                    if (dst != null)
                        args.add(new LiteralValue(context, new AnyURIValue(dst)));
                    args.add(new LiteralValue(context, new AnyURIValue(src)));
                }

	    		pm = broker.getBrokerPool().getProcessMonitor();
	    		
	            context.getProfiler().traceQueryStart();
	            pm.queryStarted(context.getWatchDog());
	            
	            final FunctionCall call = new FunctionCall(context, function);
	            call.setArguments(args);
	            call.analyze(new AnalyzeContextInfo());

				final Sequence contextSequence;
				final ContextItemDeclaration cid = call.getContext().getContextItemDeclartion();
				if(cid != null) {
					contextSequence = cid.eval(null);
				} else {
					contextSequence = NodeSet.EMPTY_SET;
				}
	    		call.eval(contextSequence);
    		}
        } catch(final XPathException e) {
    		TriggerStatePerThread.setTriggerRunningState(TriggerStatePerThread.NO_TRIGGER_RUNNING, this, null);
    		TriggerStatePerThread.setTransaction(null);
        	throw new TriggerException(PREPARE_EXCEPTION_MESSAGE, e);
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

//	public void startDocument() throws SAXException
//	{
//		originalOutputHandler = getOutputHandler();
//		//TODO : uncomment when it works
//		/*
//		if (isValidating()) 
//			setOutputHandler(adapter);	
//		*/	
//		super.startDocument();
//	}	
//
//	public void endDocument() throws SAXException
//	{
//		super.endDocument();
//		
//		setOutputHandler(originalOutputHandler);
//		
//		//if (!isValidating())
//		//		return;				
//		
//        //XQueryContext context = service.newContext(AccessContext.TRIGGER);
//        //TODO : futher initializations ?
//        // CompiledXQuery compiledQuery;
//        
//        //try {
//        	
//        	// compiledQuery = 
//        	//service.compile(context, query);
//        	
//        	//context.declareVariable(bindingPrefix + "validating", new BooleanValue(isValidating()));
//        	//if (adapter.getDocument() == null)
//        		//context.declareVariable(bindingPrefix + "document", Sequence.EMPTY_SEQUENCE);
//        	//TODO : find the right method ;-)
//        	/*
//        	else
//        		context.declareVariable(bindingPrefix + "document", (DocumentImpl)adapter.getDocument());
//        	*/
//	        	        
//        //} catch (XPathException e) {
//        	//query = null; //prevents future use
//        //	throw new SAXException("Error during endDocument", e);
//	    //} catch (IOException e) {
//        	//query = null; //prevents future use
//        //	throw new SAXException("Error during endDocument", e);
//	    //}
//
//	    //TODO : uncomment when it works
//	    /*
//        try {
//        	//TODO : should we provide another contextSet ?
//	        NodeSet contextSet = NodeSet.EMPTY_SET;	        
//			//Sequence result = service.execute(compiledQuery, contextSet);
//			//TODO : should we have a special processing ?
//			LOG.debug("done.");
//			
//        } catch (XPathException e) {
//        	query = null; //prevents future use
//        	throw new SAXException("Error during endDocument", e);
//		}	
//		*/		
//		
//        //TODO : check that result is a document node
//		//TODO : Stream result to originalOutputHandler 
//	}
    
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

	@Override
	public void beforeCreateCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.CREATE_COLLECTION)) {
			prepare(1, broker, txn, uri, null, true);
		} else {
            execute(true, broker, txn, beforeCreateCollection, uri, null);
	    }
	}

	@Override
	public void afterCreateCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.CREATE_COLLECTION)) {
			finish(1, broker, txn, collection.getURI(), null, true);
		} else {
            execute(false, broker, txn, afterCreateCollection, collection.getURI(), null);
	    }

	}

	@Override
	public void beforeCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.COPY_COLLECTION)) {
			prepare(5, broker, txn, collection.getURI(), newUri, true);
		} else {
		    execute(true, broker, txn, beforeCopyCollection, collection.getURI(), newUri);
	    }
	}

	@Override
	public void afterCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.COPY_COLLECTION)) {
			finish(5, broker, txn, collection.getURI(), oldUri, true);
		} else {
            execute(false, broker, txn, afterCopyCollection, oldUri, collection.getURI());
	    }
	}

	@Override
	public void beforeMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.MOVE_COLLECTION)) {
			prepare(7, broker, txn, collection.getURI(), newUri, true);
		} else {
		    execute(true, broker, txn, beforeMoveCollection, collection.getURI(), newUri);
	    }
	}

	@Override
	public void afterMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.MOVE_COLLECTION)) {
			finish(7, broker, txn, oldUri, collection.getURI(), true);
		} else {
		    execute(false, broker, txn, afterMoveCollection, oldUri, collection.getURI());
	    }
	}

	@Override
	public void beforeDeleteCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.DELETE_COLLECTION)) {
			prepare(9, broker, txn, collection.getURI(), null, true);
		} else {
            execute(true, broker, txn, beforeDeleteCollection, collection.getURI(), null);
	    }
	}

	@Override
	public void afterDeleteCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.DELETE_COLLECTION)) {
			finish(9, broker, txn, collection.getURI(), null, true);
		} else {
            execute(false, broker, txn, afterDeleteCollection, uri, null);
	    }
	}

	@Override
	public void beforeCreateDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.CREATE_DOCUMENT)) {
			prepare(0, broker, txn, uri, null, false);
		} else {
            execute(true, broker, txn, beforeCreateDocument, uri, null);
	    }
	}

	@Override
	public void afterCreateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.CREATE_DOCUMENT)) {
			finish(0, broker, txn, document.getURI(), null, false);
		} else {
            execute(false, broker, txn, afterCreateDocument, document.getURI(), null);
	    }
	}

	@Override
	public void beforeUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.UPDATE_DOCUMENT)) {
			prepare(2, broker, txn, document.getURI(), null, false);
		} else {
            execute(true, broker, txn, beforeUpdateDocument, document.getURI(), null);
	    }
	}

	@Override
	public void afterUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.UPDATE_DOCUMENT)) {
			finish(2, broker, txn, document.getURI(), null, false);
		} else {
            execute(false, broker, txn, afterUpdateDocument, document.getURI(), null);
	    }
	}

	@Override
	public void beforeCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.COPY_DOCUMENT)) {
			prepare(4, broker, txn, document.getURI(), newUri, false);
		} else {
            execute(true, broker, txn, beforeCopyDocument, document.getURI(), newUri);
	    }
	}

	@Override
    public void afterCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.COPY_DOCUMENT)) {
			finish(4, broker, txn, document.getURI(), oldUri, false);
		} else {
            execute(false, broker, txn, afterCopyDocument, oldUri, document.getURI());
	    }
	}

	@Override
	public void beforeMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.MOVE_DOCUMENT)) {
			prepare(6, broker, txn, document.getURI(), newUri, false);
		} else {
            execute(true, broker, txn, beforeMoveDocument, document.getURI(), newUri);
	    }
	}

	@Override
	public void afterMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.MOVE_DOCUMENT)) {
			finish(6, broker, txn, oldUri, document.getURI(), false);
		} else {
            execute(false, broker, txn, afterMoveDocument, oldUri, document.getURI());
	    }
	}

	@Override
	public void beforeDeleteDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.DELETE_DOCUMENT)) {
			prepare(8, broker, txn, document.getURI(), null, false);
		} else {
            execute(true, broker, txn, beforeDeleteDocument, document.getURI(), null);
	    }
	}

	@Override
	public void afterDeleteDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
		if (events.contains(TriggerEvents.EVENTS.DELETE_DOCUMENT)) {
			finish(8, broker, txn, uri, null, false);
		} else {
            execute(false, broker, txn, afterDeleteDocument, uri, null);
	    }
	}

	@Override
	public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
	}

	/*public String toString() {
		return "collection=" + collection + "\n" +
			"modifiedDocument=" + TriggerStatePerThread.getModifiedDocument() + "\n" +
			( query != null ? query.substring(0, 40 ) : null );
	}*/
}
