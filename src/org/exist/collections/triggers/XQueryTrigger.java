package org.exist.collections.triggers;

import java.io.IOException;
import java.util.Map;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeSet;
import org.exist.memtree.SAXAdapter;
import org.exist.security.xacml.AccessContext;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A trigger that executes a user XQuery statement when invoked.
 * The XQuery source executed is the value of the context parameter named "query".
 * These external variables are accessible to the user XQuery statement :
 * <code>xxx:collectionName</code> : the name of the collection from which the event is triggered
 * <code>xxx:documentName</code> : the name of the document from wich the event is triggered
 * <code>xxx:triggeredEvent</code> : the kind of triggered event
 * <code>xxx:document</code> : the document from wich the event is triggered
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
*/
public class XQueryTrigger extends FilteringTrigger {
	
	private SAXAdapter adapter;
	private Collection collection = null;
	private String query = null;
	/** namespace prefix associated to trigger */
	private String bindingPrefix = null;
	private XQuery service;
	private ContentHandler originalOutputHandler;
	
	public XQueryTrigger() {
		adapter = new SAXAdapter();
	}
	
	/**
	 * @see org.exist.collections.Trigger#configure(org.exist.storage.DBBroker, org.exist.collections.Collection, java.util.Map)
	 */
	public void configure(DBBroker broker, Collection parent, Map parameters)
		throws CollectionConfigurationException {
 		LOG.debug("Configured XQuery trigger for collection : '" + parent.getName() + "'");	
 		this.collection = parent;
 		this.query = (String) parameters.get("query");
		if (query == null)
			return;
		this.bindingPrefix = (String) parameters.get("bindingPrefix");
		if (this.bindingPrefix != null && !"".equals(this.bindingPrefix.trim()))
				this.bindingPrefix = this.bindingPrefix.trim() + ":";
		service = broker.getXQueryService();
	}
	
	/**
	 * @see org.exist.collections.Trigger#prepare(java.lang.String, org.w3c.dom.Document)
	 */
	public void prepare(int event, DBBroker broker, Txn transaction, 
			String documentName, DocumentImpl existingDocument )
		throws TriggerException {				
		
		LOG.debug("Preparing " + eventToString(event) + "XQuery trigger for document : '" + documentName + "'");
		
		if (query == null)
			return;        
                        
       // avoid infinite recursion by allowing just one trigger per thread		
		if( ! TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforePrepare(this, existingDocument) ) {
			return;
		}
		TriggerStatePerThread.setTransaction(transaction);
		
		XQueryContext context = service.newContext(AccessContext.TRIGGER);
         //TODO : futher initializations ?
        CompiledXQuery compiledQuery;
        try {       	
        	
        	compiledQuery = service.compile(context, new StringSource(query));

        	/*
        	Variable globalVar;
        	
        	globalVar = new Variable(new QName("collectionName", XQueryContext.EXIST_NS, "exist"));
	        globalVar.setValue(new StringValue(collection.getName()));	        
	        context.declareGlobalVariable(globalVar);	        

	        globalVar = new Variable(new QName("documentName", XQueryContext.EXIST_NS, "exist"));
	        globalVar.setValue(new StringValue(documentName));
	        context.declareGlobalVariable(globalVar);	        
        
	        globalVar = new Variable(new QName("triggerEvent", XQueryContext.EXIST_NS, "exist"));
	        globalVar.setValue(new StringValue(eventToString(event)));
	        context.declareGlobalVariable(globalVar);

	        globalVar = new Variable(new QName("document", XQueryContext.EXIST_NS, "exist"));
	        globalVar.setValue(new NodeProxy((DocumentImpl)existingDocument));
	        context.declareGlobalVariable(globalVar);
	        */
        	
        	context.declareVariable(bindingPrefix + "collectionName", new StringValue(collection.getName()));
        	context.declareVariable(bindingPrefix + "documentName", new StringValue(documentName));
        	context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(eventToString(event))); 
        	//if (existingDocument == null)
        	if (existingDocument instanceof BinaryDocument)
//        		TODO : encode in Base64 ?        		
        		context.declareVariable(bindingPrefix + "document", Sequence.EMPTY_SEQUENCE);
        	else
        		context.declareVariable(bindingPrefix + "document", (DocumentImpl)existingDocument);
	        	        
        } catch (XPathException e) {
        	query = null; //prevents future use
        	throw new TriggerException("Error during trigger prepare", e);
	    } catch (IOException e) {
        	query = null; //prevents future use
        	throw new TriggerException("Error during trigger prepare", e);
	    }

        try {
        	//TODO : should we provide another contextSet ?
	        NodeSet contextSet = NodeSet.EMPTY_SET;
			service.execute(compiledQuery, contextSet);
			//TODO : should we have a special processing ?
			LOG.debug("done.");
			
        } catch (XPathException e) {
        	query = null; //prevents future use
        	throw new TriggerException("Error during trigger prepare", e);
		}	   
		
	}
	
    /**
     * @see org.exist.collections.triggers.DocumentTrigger#finish(int, org.exist.storage.DBBroker, java.lang.String, org.w3c.dom.Document)
     */
    public void finish(int event, DBBroker broker, Txn transaction, DocumentImpl document) {		
    	
		// avoid infinite recursion by allowing just one trigger per thread
		if( !TriggerStatePerThread
				.verifyUniqueTriggerPerThreadBeforeFinish(this, document) ) {
			return;
		}
		
    	LOG.debug("Finishing " + eventToString(event) + "XQuery trigger for document : '" + document.getName() + "'");

		if (query == null)
			return;
		
        XQueryContext context = service.newContext(AccessContext.TRIGGER);
        CompiledXQuery compiledQuery = null;
        try {
        	
        	compiledQuery = service.compile(context, new StringSource(query));
        	
        	/*
        	Variable globalVar;

        	globalVar = new Variable(new QName("collectionName", XQueryContext.EXIST_NS, "exist"));
	        globalVar.setValue(new StringValue(collection.getName()));	    
	        context.declareGlobalVariable(globalVar);
	        
	        globalVar = new Variable(new QName("documentName", XQueryContext.EXIST_NS, "exist"));
	        globalVar.setValue(new StringValue(documentName));
	        context.declareGlobalVariable(globalVar);

	        globalVar = new Variable(new QName("triggerEvent", XQueryContext.EXIST_NS, "exist"));
	        globalVar.setValue(new StringValue(eventToString(event)));
	        context.declareGlobalVariable(globalVar);	  

	        globalVar = new Variable(new QName("document", XQueryContext.EXIST_NS, "exist"));
	        globalVar.setValue(new NodeProxy((DocumentImpl)document));
	        context.declareGlobalVariable(globalVar);
	        */
        	      	
        	context.declareVariable(bindingPrefix + "collectionName", new StringValue(collection.getName()));
        	context.declareVariable(bindingPrefix + "documentName", new StringValue(document.getName()));
        	context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(eventToString(event)));
        	if (event == REMOVE_DOCUMENT_EVENT)
//        		Document does not exist any more -> Sequence.EMPTY_SEQUENCE
        		context.declareVariable(bindingPrefix + "document", Sequence.EMPTY_SEQUENCE);
        	else if (document instanceof BinaryDocument)
//        		TODO : encode in Base64 ?
        		context.declareVariable(bindingPrefix + "document", Sequence.EMPTY_SEQUENCE);
        	else         	
        		context.declareVariable(bindingPrefix + "document", (DocumentImpl)document);     
	        	        
        } catch (XPathException e) {
        	//Should never be reached
	    } catch (IOException e) {
	    	//Should never be reached
	    }

        try {
        	//TODO : should we provide another contextSet ?
	        NodeSet contextSet = NodeSet.EMPTY_SET;	        
			service.execute(compiledQuery, contextSet);
			//TODO : should we have a special processing ?
			
			TriggerStatePerThread
					.setTriggerRunningState( TriggerStatePerThread.NO_TRIGGER_RUNNING,
							this, null );
			TriggerStatePerThread.setTransaction(null);
			LOG.debug("trigger done.");

        } catch (XPathException e) {
        	//Should never be reached
			LOG.error("trigger done with error: " + e );
		}	   
    }	
      
	public void startDocument() throws SAXException {
		originalOutputHandler = getOutputHandler();
//		TODO : uncomment when it works
		/*
		if (isValidating()) 
			setOutputHandler(adapter);	
		*/	
		super.startDocument();
	}	

	public void endDocument() throws SAXException {
		super.endDocument();
		
		setOutputHandler(originalOutputHandler);
		
		if (!isValidating())
				return;				
		
        XQueryContext context = service.newContext(AccessContext.TRIGGER);
        //TODO : futher initializations ?
        // CompiledXQuery compiledQuery;
        
        try {       	
        	
        	// compiledQuery = 
        	service.compile(context, new StringSource(query));

        	/*
        	Variable globalVar;
        	
        	globalVar = new Variable(new QName("collectionName", XQueryContext.EXIST_NS, "exist"));
	        globalVar.setValue(new StringValue(collection.getName()));	        
	        context.declareGlobalVariable(globalVar);	        

	        globalVar = new Variable(new QName("documentName", XQueryContext.EXIST_NS, "exist"));
	        globalVar.setValue(new StringValue(documentName));
	        context.declareGlobalVariable(globalVar);	        
        
	        globalVar = new Variable(new QName("triggerEvent", XQueryContext.EXIST_NS, "exist"));
	        globalVar.setValue(new StringValue(eventToString(event)));
	        context.declareGlobalVariable(globalVar);

	        globalVar = new Variable(new QName("document", XQueryContext.EXIST_NS, "exist"));
	        globalVar.setValue(new NodeProxy((DocumentImpl)existingDocument));
	        context.declareGlobalVariable(globalVar);
	        */
        	
        	context.declareVariable(bindingPrefix + "validating", new BooleanValue(isValidating()));
        	if (adapter.getDocument() == null)
        		context.declareVariable(bindingPrefix + "document", Sequence.EMPTY_SEQUENCE);
        	//TODO : find the right method ;-)
        	/*
        	else
        		context.declareVariable(bindingPrefix + "document", (DocumentImpl)adapter.getDocument());
        	*/
	        	        
        } catch (XPathException e) {
        	query = null; //prevents future use
        	throw new SAXException("Error during endDocument", e);
	    } catch (IOException e) {
        	query = null; //prevents future use
        	throw new SAXException("Error during endDocument", e);
	    }

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
    
    public static String eventToString(int event) {
    	switch (event) {
    		case STORE_DOCUMENT_EVENT : return "STORE"; 
    		case UPDATE_DOCUMENT_EVENT : return "UPDATE";
    		case REMOVE_DOCUMENT_EVENT : return "REMOVE";
    		case CREATE_COLLECTION_EVENT : return "CREATE";
    		case RENAME_COLLECTION_EVENT : return "RENAME";
    		case DELETE_COLLECTION_EVENT : return "DELETE";
    		default : return null;
    	}
    }

	public String toString() {
		return "collection=" + collection + "\n" +
			"modifiedDocument=" + TriggerStatePerThread.getModifiedDocument() + "\n" +
			( query != null ? query.substring(0, 40 ) : null );
}
}
