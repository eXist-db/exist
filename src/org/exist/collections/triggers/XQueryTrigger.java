package org.exist.collections.triggers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.dom.BinaryDocument;
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
import org.exist.xquery.value.Base64Binary;
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
 * <code>xxx:eventType</code> : the type of event for the Trigger. Either "prepare" or "finish"
 * <code>xxx:collectionName</code> : the name of the collection from which the event is triggered
 * <code>xxx:documentName</code> : the name of the document from which the event is triggered
 * <code>xxx:triggerEvent</code> : the kind of triggered event
 * <code>xxx:document</code> : the document from which the event is triggered
 * xxx is the namespace prefix within the XQuery, can be set by the variable "bindingPrefix"
 * 
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 * @author Adam Retter <adam.retter@devon.gov.uk>
*/
public class XQueryTrigger extends FilteringTrigger
{
	private final static String EVENT_TYPE_PREPARE = "prepare";
	private final static String EVENT_TYPE_FINISH = "finish";
	
	private final static String DEFAULT_BINDING_PREFIX = "local:";
	
	private SAXAdapter adapter;
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
	public void configure(DBBroker broker, Collection parent, Map parameters) throws CollectionConfigurationException
	{
 		this.collection = parent;
 		
 		//for an XQuery trigger there must be at least
 		//one parameter to specify the XQuery
 		if(parameters != null)
 		{
 			urlQuery = (String) parameters.get("url");
 			strQuery = (String) parameters.get("query");
 			
 			for(Iterator itParamName = parameters.keySet().iterator(); itParamName.hasNext();)
 			{
 				String paramName = (String)itParamName.next();
 				
 				//get the binding prefix (if any)
 				if(paramName.equals("bindingPrefix"))
 				{
 					String bindingPrefix = (String)parameters.get("bindingPrefix");
 					if(bindingPrefix != null && !"".equals(bindingPrefix.trim()))
 					{
 						this.bindingPrefix = bindingPrefix.trim() + ":";
 					}
 				}
 				
 				//get the URL of the query (if any)
 				else if(paramName.equals("url"))
 				{
 					urlQuery = (String)parameters.get("url");
 				}
 				
 				//get the query (if any)
 				else if(paramName.equals("query"))
 				{
 					strQuery = (String)parameters.get("query");
 				}
 				
 				//make any other parameters available as external variables for the query
 				else
 				{
 					userDefinedVariables.put(paramName, parameters.get(paramName));
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
	public void prepare(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl existingDocument) throws TriggerException
	{
		LOG.debug("Preparing " + eventToString(event) + "XQuery trigger for document: '" + documentPath + "'");
		
		//get the query
		Source query = getQuerySource(broker);
		if(query == null)
			return;        
                        
		// avoid infinite recursion by allowing just one trigger per thread		
		if(!TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforePrepare(this, documentPath))
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
        	context.declareVariable(bindingPrefix + "documentName", new AnyURIValue(documentPath));
        	context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(eventToString(event)));
        	
        	//declare user defined parameters as external variables
        	for(Iterator itUserVarName = userDefinedVariables.keySet().iterator(); itUserVarName.hasNext();)
        	{
        		String varName = (String)itUserVarName.next();
        		String varValue = userDefinedVariables.getProperty(varName);
        	
        		context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
        	}
        	
        	if(existingDocument == null)
        		context.declareVariable(bindingPrefix + "document", Sequence.EMPTY_SEQUENCE);
        	else if (existingDocument instanceof BinaryDocument)
        	{
        		//binary document
        		BinaryDocument bin = (BinaryDocument)existingDocument;
                        InputStream is = broker.getBinaryResource(bin);
                        byte [] data = new byte[(int)broker.getBinaryResourceSize(bin)];
                        is.read(data);
                        is.close();
        		
                context.declareVariable(bindingPrefix + "document", new Base64Binary(data));
        	}
        	else
        	{
        		//XML document
        		context.declareVariable(bindingPrefix + "document", (DocumentImpl)existingDocument);
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
    public void finish(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl document)
    {
    	LOG.debug("Finishing " + eventToString(event) + " XQuery trigger for document : '" + documentPath + "'");
    	
    	//get the query
    	Source query = getQuerySource(broker);
		if (query == null)
			return;
    	
		// avoid infinite recursion by allowing just one trigger per thread
		if(!TriggerStatePerThread.verifyUniqueTriggerPerThreadBeforeFinish(this, documentPath))
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
        	context.declareVariable(bindingPrefix + "documentName", new AnyURIValue(documentPath));
        	context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(eventToString(event)));

        	//declare user defined parameters as external variables
        	for(Iterator itUserVarName = userDefinedVariables.keySet().iterator(); itUserVarName.hasNext();)
        	{
        		String varName = (String)itUserVarName.next();
        		String varValue = userDefinedVariables.getProperty(varName);
        	
        		context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
        	}
        	
        	if(event == REMOVE_DOCUMENT_EVENT)
        	{
        		//Document does not exist any more -> Sequence.EMPTY_SEQUENCE
        		context.declareVariable(bindingPrefix + "document", Sequence.EMPTY_SEQUENCE);
        	}
        	else if (document instanceof BinaryDocument)
        	{
        		//Binary document
        		BinaryDocument bin = (BinaryDocument)document;
                        InputStream is = broker.getBinaryResource(bin);
                        byte [] data = new byte[(int)broker.getBinaryResourceSize(bin)];
                        is.read(data);
                        is.close();
                
                context.declareVariable(bindingPrefix + "document", new Base64Binary(data));
        	}	
        	else
        	{
        		//XML document
        		context.declareVariable(bindingPrefix + "document", (DocumentImpl)document);
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

	/*public String toString() {
		return "collection=" + collection + "\n" +
			"modifiedDocument=" + TriggerStatePerThread.getModifiedDocument() + "\n" +
			( query != null ? query.substring(0, 40 ) : null );
	}*/
}
