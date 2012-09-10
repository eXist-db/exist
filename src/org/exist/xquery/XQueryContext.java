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
 *  $Id$
 */
package org.exist.xquery;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;
import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.Trigger;
import org.exist.collections.triggers.TriggerStatePerThread;
import org.exist.debuggee.DebuggeeJoint;
import org.exist.dom.*;
import org.exist.http.servlets.SessionWrapper;
import org.exist.memtree.DocBuilder;
import org.exist.memtree.InMemoryXMLStreamReader;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.security.xacml.AccessContext;
import org.exist.security.xacml.ExistPDP;
import org.exist.security.xacml.NullAccessContextException;
import org.exist.security.xacml.XACMLSource;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.UpdateListener;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Collations;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.hashtable.NamePool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.pragmas.*;
import org.exist.xquery.value.*;
import org.exist.xquery.update.Modification;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SimpleTimeZone;
import java.util.Stack;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * The current XQuery execution context. Contains the static as well
 * as the dynamic XQuery context components.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class XQueryContext {
    public static final String						   ENFORCE_INDEX_USE_ATTRIBUTE					    = "enforce-index-use";

	public static final String CONFIGURATION_ELEMENT_NAME = "xquery";
	public static final String CONFIGURATION_MODULES_ELEMENT_NAME = "builtin-modules";
	public static final String ENABLE_QUERY_REWRITING_ATTRIBUTE = "enable-query-rewriting";
	public static final String XQUERY_BACKWARD_COMPATIBLE_ATTRIBUTE = "backwardCompatible";
	public static final String XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_ATTRIBUTE = "raise-error-on-failed-retrieval";
		
	//TODO : move elsewhere ?
	public static final String CONFIGURATION_MODULE_ELEMENT_NAME = "module";
	public static final String BUILT_IN_MODULE_URI_ATTRIBUTE = "uri";
	public static final String BUILT_IN_MODULE_CLASS_ATTRIBUTE = "class";
    public static final String BUILT_IN_MODULE_SOURCE_ATTRIBUTE = "src";

	public static final String PROPERTY_XQUERY_BACKWARD_COMPATIBLE = "xquery.backwardCompatible";
	public static final String PROPERTY_ENABLE_QUERY_REWRITING = "xquery.enable-query-rewriting";
	public static final String PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL = "xquery.raise-error-on-failed-retrieval";
	public static final boolean XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_DEFAULT = false;
    public static final String						   PROPERTY_ENFORCE_INDEX_USE						= "xquery.enforce-index-use";

	//TODO : move elsewhere ?
	public static final String PROPERTY_BUILT_IN_MODULES = "xquery.modules";
    public static final String PROPERTY_STATIC_MODULE_MAP = "xquery.modules.static";
	
	private static final String JAVA_URI_START = "java:";
    //private static final String XMLDB_URI_START = "xmldb:exist://";
    
    protected final static Logger LOG = Logger.getLogger(XQueryContext.class);

	private static final String TEMP_STORE_ERROR = "Error occurred while storing temporary data";
    
	// Static namespace/prefix mappings
	protected HashMap staticNamespaces = new HashMap();
	
	// Static prefix/namespace mappings
	protected HashMap staticPrefixes = new HashMap();
	
	// Local in-scope namespace/prefix mappings in the current context
	protected HashMap inScopeNamespaces = new HashMap();
	
	// Local prefix/namespace mappings in the current context
	protected HashMap inScopePrefixes = new HashMap();
	
	// Inherited in-scope namespace/prefix mappings in the current context
	protected HashMap inheritedInScopeNamespaces = new HashMap();	

	// Inherited prefix/namespace mappings in the current context
	protected HashMap inheritedInScopePrefixes = new HashMap();	
	
	protected HashMap mappedModules = new HashMap();
	
	private boolean preserveNamespaces = true;
	
    private boolean inheritNamespaces = true;	

	// Local namespace stack
	protected Stack namespaceStack = new Stack();

	// Known user defined functions in the local module
	protected TreeMap declaredFunctions = new TreeMap();

    // Globally declared variables
	protected Map<QName, Variable> globalVariables = new TreeMap<QName, Variable>();

	// The last element in the linked list of local in-scope variables
	protected LocalVariable lastVar = null;
	
    protected Stack<LocalVariable> contextStack = new Stack<LocalVariable>();
    
    protected Stack callStack = new Stack();
    
	// The current size of the variable stack
	protected int variableStackSize = 0;
	
	// Unresolved references to user defined functions
	protected Stack forwardReferences = new Stack();
	
	// List of options declared for this query at compile time - i.e. declare option 
	protected List staticOptions = null;
	// List of options declared for this query at run time - i.e. util:declare-option()
	protected List dynamicOptions = null;
	
	//The Calendar for this context : may be changed by some options
	XMLGregorianCalendar calendar = null; 
	TimeZone implicitTimeZone = null;
	
    /**
     * the watchdog object assigned to this query
     */
	protected XQueryWatchDog watchdog;
    
	/**
	 * Loaded modules.
	 */
	protected HashMap<String,Module> modules = new HashMap<String,Module>();
	
	/**
	 * Loaded modules, including ones bubbled up from imported modules.
	 */
	protected HashMap<String,Module> allModules = new HashMap<String,Module>();
	
	/**
	 * Whether some modules were rebound to new instances since the last time this context's
	 * query was analyzed.  (This assumes that each context is attached to at most one query.)
	 */
	private boolean modulesChanged = true;

	/** 
	 * The set of statically known documents specified as
	 * an array of paths to documents and collections.
	 */
	protected XmldbURI[] staticDocumentPaths = null;
	
	/**
	 * The actual set of statically known documents. This
	 * will be generated on demand from staticDocumentPaths.
	 */
	protected DocumentSet staticDocuments = null;
	
	/** 
	 * The set of statically known documents specified as
	 * an array of paths to documents and collections.
	 */
	protected XmldbURI[] staticCollections = null;

    /**
     * A set of documents which were modified during the query,
     * usually through an XQuery update extension. The documents
     * will be checked after the query completed to see if a
     * defragmentation run is needed.
     */
    protected MutableDocumentSet modifiedDocuments = null;

	/**
	 * The main database broker object providing access
	 * to storage and indexes. Every XQuery has its own
	 * DBBroker object.
	 */
	protected DBBroker broker;

    /**
     * A general-purpose map to set attributes in the current
     * query context.
     */
    protected Map attributes = new HashMap();
    
    protected AnyURIValue baseURI = AnyURIValue.EMPTY_URI;
	
    protected boolean baseURISetInProlog = false;
    
	protected String moduleLoadPath = ".";
	
	protected String defaultFunctionNamespace = Function.BUILTIN_FUNCTION_NS;
	protected AnyURIValue defaultElementNamespace = AnyURIValue.EMPTY_URI;
	protected AnyURIValue defaultElementNamespaceSchema = AnyURIValue.EMPTY_URI;

	/**
	 * The default collation URI
	 */
	private String defaultCollation = Collations.CODEPOINT;
	
	/**
	 * Default Collator. Will be null for the default unicode codepoint collation.
	 */
	private Collator defaultCollator = null;
	
	/**
	 * Set to true to enable XPath 1.0
	 * backwards compatibility.
	 */
	private boolean backwardsCompatible = false;

	/**
	 * Should whitespace inside node constructors be stripped?
	 */
	private boolean stripWhitespace = true;
	
    /**
	 * Should empty order greatest or least?
	 */
    private boolean orderEmptyGreatest = true;

	/**
	 * The position of the currently processed item in the context 
	 * sequence. This field has to be set on demand, for example,
	 * before calling the fn:position() function. 
	 */
	private int contextPosition = 0;

	/**
	 * The builder used for creating in-memory document 
	 * fragments
	 */
	private MemTreeBuilder builder = null;

    /**
     * Shared name pool used by all in-memory documents constructed in
     * this query context.
     */
    private NamePool sharedNamePool = null; 
    
    /**
	 * Stack for temporary document fragments
	 */
	private Stack fragmentStack = new Stack();

	/**
	 * The root of the expression tree
	 */
	private Expression rootExpression;
	
	/**
	 * An incremental counter to count the expressions in
	 * the current XQuery. Used during compilation to assign
	 * a unique ID to every expression.
	 */
	private int expressionCounter = 0;
	
	/**
	 * Should all documents loaded by the query be locked?
	 * If set to true, it is the responsibility of the calling client
	 * code to unlock documents after the query has completed.
	 */
//	private boolean lockDocumentsOnLoad = false;

    /**
     * Documents locked during the query.
     */
//	private LockedDocumentMap lockedDocuments = null;

    private LockedDocumentMap protectedDocuments = null;
    
    /**
     * The profiler instance used by this context.
     */
    private Profiler profiler;
    
    //For holding XQuery Context variables for general storage in the XQuery Context
    HashMap XQueryContextVars = new HashMap();
    public static final String XQUERY_CONTEXTVAR_XQUERY_UPDATE_ERROR = "_eXist_xquery_update_error";
    public static final String HTTP_SESSIONVAR_XMLDB_USER = "_eXist_xmldb_user";

    //Transaction for  batched xquery updates
    private Txn batchTransaction = null;
    private MutableDocumentSet batchTransactionTriggers = new DefaultDocumentSet();
    
	private AccessContext accessCtx;
    
	private ContextUpdateListener updateListener = null;

    private boolean enableOptimizer = true;
    
    private boolean raiseErrorOnFailedRetrieval = XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_DEFAULT;

    private boolean isShared = false;

    private XACMLSource source = null;

    private boolean analyzed = false;
    
    private XQueryContext() {}
	
	protected XQueryContext(AccessContext accessCtx) {
		if(accessCtx == null)
			throw new NullAccessContextException();
		this.accessCtx = accessCtx;
		builder = new MemTreeBuilder(this);
		builder.startDocument();
        profiler = new Profiler(null);
	}
	
	public XQueryContext(DBBroker broker, AccessContext accessCtx) {
		this(accessCtx);
		this.broker = broker;
		loadDefaults(broker.getConfiguration());
        this.profiler = new Profiler(broker.getBrokerPool());
	}
	
	public XQueryContext(XQueryContext copyFrom) {
        this(copyFrom.getAccessContext());
        this.broker = copyFrom.broker;
        loadDefaultNS();
        Iterator prefixes = copyFrom.staticNamespaces.keySet().iterator();
        while (prefixes.hasNext()) {
            String prefix = (String)prefixes.next();
            if (prefix.equals("xml") || prefix.equals("xmlns")) {
                continue;
            }
            try {
                declareNamespace(prefix,(String)copyFrom.staticNamespaces.get(prefix));
            } catch (XPathException ex) {
                ex.printStackTrace();
            }
        }
        this.profiler = copyFrom.profiler;
    }

    /**
     * Returns true if this context has a parent context
     * (means it is a module context).
     * 
     * @return False.
     */
    public boolean hasParent() {
        return false;
    }

    public XQueryContext getRootContext() {
        return this;
    }
    
    public XQueryContext copyContext() {
        XQueryContext ctx = new XQueryContext(this);
        copyFields(ctx);
        return ctx;
    }

    /**
     * Update the current dynamic context using the properties
     * of another context. This is needed by
     * {@link org.exist.xquery.functions.util.Eval}.
     *  
     * @param from
     */
    public void updateContext(XQueryContext from) {
        this.watchdog = from.watchdog;
        this.lastVar = from.lastVar;
        this.variableStackSize = from.getCurrentStackSize();
        this.contextStack = from.contextStack;
        this.inScopeNamespaces = from.inScopeNamespaces;
        this.inScopePrefixes = from.inScopePrefixes;
        this.inheritedInScopeNamespaces = from.inheritedInScopeNamespaces;
        this.inheritedInScopePrefixes = from.inheritedInScopePrefixes;
        this.variableStackSize = from.variableStackSize;
        this.attributes = from.attributes;
        this.updateListener = from.updateListener;
        this.modules = from.modules;
        this.allModules = from.allModules;
        this.mappedModules = from.mappedModules;
    }

    protected void copyFields(XQueryContext ctx) {
    	ctx.calendar = this.calendar;
    	ctx.implicitTimeZone = this.implicitTimeZone;    	
        ctx.baseURI = this.baseURI;
        ctx.baseURISetInProlog = this.baseURISetInProlog;
        ctx.staticDocumentPaths = this.staticDocumentPaths;
        ctx.staticDocuments = this.staticDocuments;
        ctx.moduleLoadPath = this.moduleLoadPath;
        ctx.defaultFunctionNamespace = this.defaultFunctionNamespace;
        ctx.defaultElementNamespace = this.defaultElementNamespace;
        ctx.defaultCollation = this.defaultCollation;
        ctx.defaultCollator = this.defaultCollator;
        ctx.backwardsCompatible = this.backwardsCompatible;
        ctx.enableOptimizer = this.enableOptimizer;
        ctx.stripWhitespace = this.stripWhitespace;
        ctx.preserveNamespaces = this.preserveNamespaces;        
        ctx.inheritNamespaces = this.inheritNamespaces;
        ctx.orderEmptyGreatest = this.orderEmptyGreatest;

        ctx.declaredFunctions = new TreeMap(this.declaredFunctions);
        ctx.globalVariables = new TreeMap<QName, Variable>(this.globalVariables);
        // make imported modules available in the new context
        ctx.modules = new HashMap();
        for (Iterator i = this.modules.values().iterator(); i.hasNext(); ) {
            try {
                Module module = (Module) i.next();
                ctx.modules.put(module.getNamespaceURI(), module);
                String prefix = (String) this.staticPrefixes.get(module.getNamespaceURI());
                ctx.declareNamespace(prefix, module.getNamespaceURI());
            } catch (XPathException e) {
                // ignore
            }
        }
        ctx.allModules = new HashMap();
        for (Iterator i = this.allModules.values().iterator(); i.hasNext(); ) {
      	  Module module = (Module) i.next();
            if (module != null)
          	  ctx.allModules.put(module.getNamespaceURI(), module);
        }

        ctx.watchdog = this.watchdog;
        ctx.lastVar = this.lastVar;
        ctx.variableStackSize = getCurrentStackSize();
        ctx.contextStack = this.contextStack;        
        ctx.mappedModules = new HashMap(this.mappedModules);
        ctx.staticNamespaces = new HashMap(this.staticNamespaces);
        ctx.staticPrefixes = new HashMap(this.staticPrefixes);
    }
    
    /**
	 * Prepares the current context before xquery execution
	 */
	public void prepare()
	{
		//if there is an existing user in the current http session
		//then set the DBBroker user
		User user = getUserFromHttpSession();
		if(user != null)
		{
            broker.setUser(user);
        }
		//Reset current context position
		setContextPosition(0);	
		//Note that, for some reasons, an XQueryContext might be used without calling this method
	}
	
	public AccessContext getAccessContext() {
		return accessCtx;
	}
	

    /**
     * @return true if profiling is enabled for this context.
     */
    public boolean isProfilingEnabled() {
        return profiler.isEnabled();
    }
    
    public boolean isProfilingEnabled(int verbosity) {
    	return profiler.isEnabled() && profiler.verbosity() >= verbosity;
    }
    
    /**
     * Returns the {@link Profiler} instance of this context 
     * if profiling is enabled.
     * 
     * @return the profiler instance.
     */
    public Profiler getProfiler() {
        return profiler;
    }
    
	/**
	 * Called from the XQuery compiler to set the root expression
	 * for this context.
	 * 
	 * @param expr
	 */
	public void setRootExpression(Expression expr) {
		this.rootExpression = expr;
	}
	
	/**
	 * Returns the root expression of the XQuery associated with
	 * this context.
	 * 
	 * @return root expression
	 */
	public Expression getRootExpression() {
		return rootExpression;
	}
	
	/**
	 * Returns the next unique expression id. Every expression
	 * in the XQuery is identified by a unique id. During compilation,
	 * expressions are assigned their id by calling this method.
	 *  
	 * @return The next unique expression id.
	 */
	protected int nextExpressionId() {
		return expressionCounter++;
	}
	
    /**
     * Returns the number of expression objects in the internal
     * representation of the query. Used to estimate the size
     * of the query.
     * 
     * @return number of expression objects 
     */
    public int getExpressionCount() {
        return expressionCounter;
    }

    public void setSource(XACMLSource source) {
        this.source = source;
    }

    public XACMLSource getSource() {
        return source;
    }
    
	/**
	 * Returns the Source Key of the XQuery associated with
	 * this context.
	 * 
	 * @return source key
	 */
	public String getSourceKey() 
	{
		return source.getKey();
	}

	/**
	 * Returns the Source Type of the XQuery associated with
	 * this context.
	 * 
	 * @return source type
	 */
	public String getSourceType() 
	{
		return source.getType();
	}
	
	/**
	 * Declare a user-defined static prefix/namespace mapping.
	 * 
	 * eXist internally keeps a table containing all prefix/namespace
	 * mappings it found in documents, which have been previously
	 * stored into the database. These default mappings need not to be
	 * declared explicitely.
	 * 
	 * @param prefix
	 * @param uri
	 */
	public void declareNamespace(String prefix, String uri) throws XPathException {
		if (prefix == null)
			prefix = "";
		if(uri == null)
			uri = "";
		if (prefix.equals("xml") || prefix.equals("xmlns"))
			throw new XPathException("err:XQST0070: Namespace predefined prefix '" + prefix + "' can not be bound");
		if (uri.equals(Namespaces.XML_NS))
			throw new XPathException("err:XQST0070: Namespace URI '" + uri + "' must be bound to the 'xml' prefix");
		final String prevURI = (String)staticNamespaces.get(prefix);
		//This prefix was not bound
		if(prevURI == null ) {
			//Bind it
			if (uri.length() > 0) {
				staticNamespaces.put(prefix, uri);
				staticPrefixes.put(uri, prefix);
				return;
			}
			//Nothing to bind
			else {
				//TODO : check the specs : unbinding an NS which is not already bound may be disallowed.
				LOG.warn("Unbinding unbound prefix '" + prefix + "'");
			}
		}
		else
		//This prefix was bound
		{	
			//Unbind it
			if (uri.length() == 0) {
	            // if an empty namespace is specified, 
	            // remove any existing mapping for this namespace
	        	//TODO : improve, since XML_NS can't be unbound
				staticPrefixes.remove(uri);
	            staticNamespaces.remove(prefix);
	            return;
			}
			//those prefixes can be rebound to different URIs
			if ((prefix.equals("xs") && Namespaces.SCHEMA_NS.equals(prevURI)) ||
				(prefix.equals("xsi") && Namespaces.SCHEMA_INSTANCE_NS.equals(prevURI)) ||
				(prefix.equals("xdt") && Namespaces.XPATH_DATATYPES_NS.equals(prevURI))|| 
				(prefix.equals("fn") && Namespaces.XPATH_FUNCTIONS_NS.equals(prevURI)) ||
				(prefix.equals("local") && Namespaces.XQUERY_LOCAL_NS.equals(prevURI))) {

				staticPrefixes.remove(prevURI);
				staticNamespaces.remove(prefix);
				if (uri.length() > 0) {
					staticNamespaces.put(prefix, uri);
					staticPrefixes.put(uri, prefix);
					return;
				}
				//Nothing to bind (not sure if it should raise an error though)
				else {
					//TODO : check the specs : unbinding an NS which is not already bound may be disallowed.
					LOG.warn("Unbinding unbound prefix '" + prefix + "'");
				}
			} else {		
				//Forbids rebinding the *same* prefix in a *different* namespace in this *same* context
				if (!uri.equals(prevURI))
					throw new XPathException("err:XQST0033: Namespace prefix '" + prefix + "' is already bound to a different uri '" + prevURI + "'");
			}
		}
	}

	public void declareNamespaces(Map namespaceMap) {
		Map.Entry entry;
		String prefix, uri;
		for(Iterator i = namespaceMap.entrySet().iterator(); i.hasNext(); ) {
			entry = (Map.Entry)i.next();
			prefix = (String)entry.getKey();
			uri = (String) entry.getValue();
			if(prefix == null)
				prefix = "";
			if(uri == null)
				uri = "";
            staticNamespaces.put(prefix, uri);
			staticPrefixes.put(uri, prefix);
		}
	}
	
    /**
	 * Removes the namespace URI from the prefix/namespace 
	 * mappings table.
	 * 
	 * @param uri
	 */
	public void removeNamespace(String uri) {
		staticPrefixes.remove(uri);
		for (Iterator i = staticNamespaces.values().iterator(); i.hasNext();) {
			if (((String) i.next()).equals(uri)) {
				i.remove();
				return;
			}
		}
		inScopePrefixes.remove(uri);
		if (inScopeNamespaces != null) {
			for (Iterator i = inScopeNamespaces.values().iterator(); i.hasNext();) {
				if (((String) i.next()).equals(uri)) {
					i.remove();
					return;
				}
			}
		}
		//TODO : is this relevant ?
		inheritedInScopePrefixes.remove(uri);
		if (inheritedInScopeNamespaces != null) {
			for (Iterator i = inheritedInScopeNamespaces.values().iterator(); i.hasNext();) {
				if (((String) i.next()).equals(uri)) {
					i.remove();
					return;
				}
			}
		}		
	}
	
	/**
	 * Declare an in-scope namespace. This is called during query execution.
	 * 
	 * @param prefix
	 * @param uri
	 */
	public void declareInScopeNamespace(String prefix, String uri) {
		if (prefix == null || uri == null)
			throw new IllegalArgumentException("null argument passed to declareNamespace");
		//Activate the namespace by removing it from the inherited namespaces
		if (inheritedInScopePrefixes.get(getURIForPrefix(prefix)) != null)
			inheritedInScopePrefixes.remove(uri);	
		if (inheritedInScopeNamespaces.get(prefix) != null)
			inheritedInScopeNamespaces.remove(prefix);
		inScopePrefixes.put(uri, prefix);
		inScopeNamespaces.put(prefix, uri);
	}	
	
    public String getInScopeNamespace(String prefix) {
        return inScopeNamespaces == null ? null : (String) inScopeNamespaces.get(prefix);
    }

    public String getInScopePrefix(String uri) {
        return inScopePrefixes == null ? null : (String) inScopePrefixes.get(uri);
    }

    public String getInheritedNamespace(String prefix) {
        return inheritedInScopeNamespaces == null ? null : (String) inheritedInScopeNamespaces.get(prefix);
    }

    public String getInheritedPrefix(String uri) {
        return inheritedInScopePrefixes == null ?	null : (String) inheritedInScopePrefixes.get(uri);
    }	
	
	/**
	 * Return the namespace URI mapped to the registered prefix
	 * or null if the prefix is not registered.
	 * 
	 * @param prefix
	 * @return namespace
	 */
	public String getURIForPrefix(String prefix) {
        // try in-scope namespace declarations
       String uri = inScopeNamespaces == null ? null : (String) inScopeNamespaces.get(prefix);
       if (uri != null)
    	   return uri;
       if (inheritNamespaces) {
    	   uri = inheritedInScopeNamespaces == null ? null : (String) inheritedInScopeNamespaces.get(prefix);
    	   if (uri != null)
    		   return uri;
       }      
      return (String)staticNamespaces.get(prefix);
      /* old code checked namespaces first
		String ns = (String) namespaces.get(prefix);
		if (ns == null)
			// try in-scope namespace declarations
			return inScopeNamespaces == null
				? null
				: (String) inScopeNamespaces.get(prefix);
		else
			return ns;
        */
	}

	/**
	 * @param uri
         * @return the prefix mapped to the registered URI or null if the URI 
         * is not registered.
	 */
	public String getPrefixForURI(String uri) {
		String prefix = inScopePrefixes == null ? null : (String) inScopePrefixes.get(uri);
		if (prefix != null)
			return prefix;
		if (inheritNamespaces) {
			prefix = inheritedInScopePrefixes == null ?	null : (String) inheritedInScopePrefixes.get(uri);		
			if (prefix != null)
				return prefix;
		}
		return (String) staticPrefixes.get(uri);
	}

	/**
	 * Clear all user-defined prefix/namespace mappings.
	 */
    // TODO: remove since never used?
//	public void clearNamespaces() {
//		staticNamespaces.clear();
//		staticPrefixes.clear();
//		if (inScopeNamespaces != null) {
//			inScopeNamespaces.clear();
//			inScopePrefixes.clear();
//		}
//		//TODO : it this relevant ?
//		if (inheritedInScopeNamespaces != null) {
//			inheritedInScopeNamespaces.clear();
//			inheritedInScopePrefixes.clear();
//		}
//		loadDefaults(broker.getConfiguration());
//	}

	/**
	 * Returns the current default function namespace.
	 * 
	 * @return current default function namespace
	 */
	public String getDefaultFunctionNamespace() {
		return defaultFunctionNamespace;
	}

	/**
	 * Set the default function namespace. By default, this
	 * points to the namespace for XPath built-in functions.
	 * 
	 * @param uri
	 */
	public void setDefaultFunctionNamespace(String uri) throws XPathException  {
		//Not sure for the 2nd clause : eXist forces the function NS as default.
		if (defaultFunctionNamespace != null && !defaultFunctionNamespace.equals(Function.BUILTIN_FUNCTION_NS) 
				&& !defaultFunctionNamespace.equals(uri))
			throw new XPathException("err:XQST0066: default function namespace is already set to: '" + defaultFunctionNamespace + "'");
		defaultFunctionNamespace = uri;
	}

	/**
	 * Returns the current default element namespace.
	 * 
	 * @return current default element namespace schema
	 */
	public String getDefaultElementNamespaceSchema() throws XPathException {
		return defaultElementNamespaceSchema.getStringValue();
	}

	/**
	 * Set the default element namespace. By default, this
	 * points to the empty uri.
	 * 
	 * @param uri
	 */
	public void setDefaultElementNamespaceSchema(String uri) throws XPathException  {
		// eXist forces the empty element NS as default.
		if (!defaultElementNamespaceSchema.equals(AnyURIValue.EMPTY_URI))
			throw new XPathException("err:XQST0066: default function namespace schema is already set to: '" + defaultElementNamespaceSchema.getStringValue() + "'");
		defaultElementNamespaceSchema = new AnyURIValue(uri);
	}

	/**
	 * Returns the current default element namespace.
	 * 
	 * @return current default element namespace
	 */
	public String getDefaultElementNamespace() throws XPathException {
		return defaultElementNamespace.getStringValue();
	}

	/**
     * Set the default element namespace. By default, this
	 * points to the empty uri.
	 * 
     * @param uri a <code>String</code> value
     * @param schema a <code>String</code> value
     * @exception XPathException if an error occurs
     */
    public void setDefaultElementNamespace(String uri, String schema) throws XPathException  {
		// eXist forces the empty element NS as default.
		if (!defaultElementNamespace.equals(AnyURIValue.EMPTY_URI))
			throw new XPathException("err:XQST0066: default element namespace is already set to: '" + defaultElementNamespace.getStringValue() + "'");
        defaultElementNamespace = new AnyURIValue(uri);
        if (schema != null) {
            defaultElementNamespaceSchema = new AnyURIValue(schema);
        }
	}

	/**
	 * Set the default collation to be used by all operators and functions on strings.
	 * Throws an exception if the collation is unknown or cannot be instantiated.
	 * 
	 * @param uri
	 * @throws XPathException
	 */
	public void setDefaultCollation(String uri) throws XPathException {
		if(uri.equals(Collations.CODEPOINT) || uri.equals(Collations.CODEPOINT_SHORT)) {
			defaultCollation = Collations.CODEPOINT;
			defaultCollator = null;
		}

        URI uriTest;
        try {
            uriTest = new URI(uri);
        } catch(URISyntaxException e) {
            throw new XPathException("err:XQST0038: Unknown collation : '" + uri + "'");              
        }
        if (uri.startsWith(Collations.EXIST_COLLATION_URI) 
            || uri.startsWith("?")
            || uriTest.isAbsolute()) {
            defaultCollator = Collations.getCollationFromURI(this, uri);
            defaultCollation = uri;
        } else {
            String absUri = getBaseURI().getStringValue() + uri;
            defaultCollator = Collations.getCollationFromURI(this, absUri);
            defaultCollation = absUri;
        }
	}

	public String getDefaultCollation() {
		return defaultCollation;
	}
	
	public Collator getCollator(String uri) throws XPathException {
		if(uri == null)
			return defaultCollator;
		return Collations.getCollationFromURI(this, uri);
	}
	
	public Collator getDefaultCollator() {
		return defaultCollator;
	}

	/**
	 * Set the set of statically known documents for the current
	 * execution context. These documents will be processed if
	 * no explicit document set has been set for the current expression
	 * with fn:doc() or fn:collection().
	 * 
	 * @param docs
	 */
	public void setStaticallyKnownDocuments(XmldbURI[] docs) {
		staticDocumentPaths = docs;
	}
	
	public void setStaticallyKnownDocuments(DocumentSet set) {
		staticDocuments = set;
	}
	
	//TODO : not sure how these 2 options might/have to be related
	public void setCalendar(XMLGregorianCalendar newCalendar) {		
		this.calendar = (XMLGregorianCalendar)newCalendar.clone();	
	}
	
	public void setTimeZone(TimeZone newTimeZone) {
		this.implicitTimeZone = newTimeZone;
	}
	
	public XMLGregorianCalendar getCalendar() {
		//TODO : we might prefer to return null
		if (calendar == null) {
			try {
				//Initialize to current dateTime
				calendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar());
			} catch (DatatypeConfigurationException e) {
				LOG.error(e.getMessage(), e);
			}
		}
		//That's how we ensure stability of that static context function
		return calendar;
	}	
	
	 public TimeZone getImplicitTimeZone() { 	
		 if (implicitTimeZone == null) {
			 implicitTimeZone = TimeZone.getDefault();
			 if (implicitTimeZone.inDaylightTime(new Date()))
				 implicitTimeZone.setRawOffset(implicitTimeZone.getRawOffset() + implicitTimeZone.getDSTSavings());
		 }
		//That's how we ensure stability of that static context function
		return this.implicitTimeZone;
	}	
	
	/**
	 * @return set of statically known documents.
	 */
	public DocumentSet getStaticallyKnownDocuments() throws XPathException {
		if(staticDocuments != null)
            // the document set has already been built, return it
			return staticDocuments;
        if (protectedDocuments != null) {
            staticDocuments = protectedDocuments.toDocumentSet();
            return staticDocuments;
        }
        MutableDocumentSet ndocs = new DefaultDocumentSet(1031);
		if(staticDocumentPaths == null)
            // no path defined: return all documents in the db 
			broker.getAllXMLResources(ndocs);
		else {
			DocumentImpl doc;
			Collection collection;
			for(int i = 0; i < staticDocumentPaths.length; i++) {
				try {
                    collection = broker.getCollection(staticDocumentPaths[i]);
                    if (collection != null) {
                        collection.allDocs(broker, ndocs, true, true);
                    } else {
                        doc = broker.getXMLResource(staticDocumentPaths[i], Lock.READ_LOCK);
                        if(doc != null) {
                            if(doc.getPermissions().validate(broker.getUser(), Permission.READ)) {
                                ndocs.add(doc);
                            }
                            doc.getUpdateLock().release(Lock.READ_LOCK);
                        }
                    }
				} catch(PermissionDeniedException e) {
					LOG.warn("Permission denied to read resource " + staticDocumentPaths[i] + ". Skipping it.");
				}
			}
		}
        staticDocuments = ndocs;
        return staticDocuments;
	}

    public ExtendedXMLStreamReader getXMLStreamReader(NodeValue nv) throws XMLStreamException, IOException {
        ExtendedXMLStreamReader reader;
        if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
            NodeImpl node = (NodeImpl) nv;
            reader = new InMemoryXMLStreamReader(node.getDocument(), node.getDocument());
        } else {
            NodeProxy proxy = (NodeProxy) nv;
            reader = getBroker().newXMLStreamReader(new NodeProxy(proxy.getDocument(), NodeId.DOCUMENT_NODE, proxy.getDocument().getFirstChildAddress()), false);
        }
        return reader;
    }

    public void setProtectedDocs(LockedDocumentMap map) {
        this.protectedDocuments = map;
    }

    public LockedDocumentMap getProtectedDocs() {
        return this.protectedDocuments;
    }

    public boolean inProtectedMode() {
        return protectedDocuments != null;
    }

    /**
	 * Should loaded documents be locked?
	 * 
     * see #setLockDocumentsOnLoad(boolean)
     *
	 */
	public boolean lockDocumentsOnLoad() {
	    return false;
	}
	
//	/**
//	 * If lock is true, all documents loaded during query execution
//	 * will be locked. This way, we avoid that query results become
//	 * invalid before the entire result has been processed by the client
//	 * code. All attempts to modify nodes which are part of the result
//	 * set will be blocked.
//	 * 
//	 * However, it is the client's responsibility to proper unlock
//	 * all documents once processing is completed.
//	 * 
//	 * @param lock
//	 */
//	public void setLockDocumentsOnLoad(boolean lock) {
//	    lockDocumentsOnLoad = lock;
//	    if(lock)
//	        lockedDocuments = new LockedDocumentMap();
//	}


    public void addLockedDocument(DocumentImpl doc) {
//        if (lockedDocuments != null)
//           lockedDocuments.add(doc);
    }

//    /**
//     * Release all locks on documents that have been locked
//     * during query execution.
//     *
//     *@see #setLockDocumentsOnLoad(boolean)
//     */
//	public void releaseLockedDocuments() {
//        if(lockedDocuments != null)
//	        lockedDocuments.unlock();
//	    lockDocumentsOnLoad = false;
//		lockedDocuments = null;
//	}
	
//    /**
//     * Release all locks on documents not being referenced by the sequence.
//     * This is called after query execution has completed. Only locks on those
//     * documents contained in the final result set will be preserved. All other
//     * locks are released as they are no longer needed.
//     * 
//     * @param seq
//     * @throws XPathException 
//     */
//	public LockedDocumentMap releaseUnusedDocuments(Sequence seq) throws XPathException {
//	    if(lockedDocuments == null)
//	        return null;
//        // determine the set of documents referenced by nodes in the sequence
//        DocumentSet usedDocs = new DocumentSet();
//        for(SequenceIterator i = seq.iterate(); i.hasNext(); ) {
//            Item next = i.nextItem();
//            if(Type.subTypeOf(next.getType(), Type.NODE)) {
//                NodeValue node = (NodeValue) next;
//                if(node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
//                    DocumentImpl doc = ((NodeProxy)node).getDocument();
//                    if(!usedDocs.contains(doc.getDocId()))
//	                    usedDocs.add(doc, false);
//                }
//            }
//        }
//        LockedDocumentMap remaining = lockedDocuments.unlockSome(usedDocs);
//        lockDocumentsOnLoad = false;
//		lockedDocuments = null;
//        return remaining;
//    }

    public void setShared(boolean shared) {
        isShared = shared;
    }

    public boolean isShared() {
        return isShared;
    }
    
    public void addModifiedDoc(DocumentImpl document) {
        if (modifiedDocuments == null)
            modifiedDocuments = new DefaultDocumentSet();
        modifiedDocuments.add(document);
    }
    
    public void reset() {
        reset(false);
    }

    /**
	 * Prepare this XQueryContext to be reused. This should be
     * called when adding an XQuery to the cache.
	 */
	public void reset(boolean keepGlobals) {
        if (modifiedDocuments != null) {
            try {
                Modification.checkFragmentation(this, modifiedDocuments);
            } catch (EXistException e) {
                LOG.warn("Error while checking modified documents: " + e.getMessage(), e);
            }
            modifiedDocuments = null;
        }
        calendar = null;
        implicitTimeZone = null;
        builder = new MemTreeBuilder(this);
        builder.startDocument();

        if(!keepGlobals) {
            // do not reset the statically known documents
            staticDocumentPaths = null;
            staticDocuments = null;
        }
        if(!isShared) {
            lastVar = null;
        }

        fragmentStack = new Stack();
        callStack.clear();
        protectedDocuments = null;
        if(!keepGlobals) {
            globalVariables.clear();
        }

        if(dynamicOptions != null) {
            dynamicOptions.clear(); //clear any dynamic options
        }

        if(!isShared) {
            watchdog.reset();
        }

        for(Iterator i = modules.values().iterator(); i.hasNext();) {
            Module module = (Module) i.next();
            if (module instanceof ExternalModule && ((ModuleContext)((ExternalModule)module).getContext()).getParentContext() != this) {
                continue;
            }
            module.reset(this);
        }
        
        if(!keepGlobals) {
            mappedModules.clear();
        }

        //remove the context-vars, subsequent execution of the query
        //may generate different values for the vars based on the
        //content of the db
        XQueryContextVars.clear();

        clearUpdateListeners();
        
        profiler.reset();

        analyzed = false;
    }
	
	/**
	 * Returns true if whitespace between constructed element nodes
	 * should be stripped by default.
	 */
	public boolean stripWhitespace() {
		return stripWhitespace;
	}
	
	public void setStripWhitespace(boolean strip) {
		this.stripWhitespace = strip;
	}

	/**
	 * Returns true if namespaces for constructed element and document nodes
	 * should be preserved on copy by default.
	 */
	public boolean preserveNamespaces() {
		return preserveNamespaces;
	}
	
	/**
     * The method <code>setPreserveNamespaces</code>
     *
     * @param preserve a <code>boolean</code> value
     */
    public void setPreserveNamespaces(final boolean preserve) {
		this.preserveNamespaces = preserve;
	}

	/**
	 * Returns true if namespaces for constructed element and document nodes
	 * should be inherited on copy by default.
	 */
	public boolean inheritNamespaces() {
		return inheritNamespaces;
	}
	
	/**
     * The method <code>setInheritNamespaces</code>
     *
     * @param inherit a <code>boolean</code> value
     */
    public void setInheritNamespaces(final boolean inherit) {
		this.inheritNamespaces = inherit;
	}

	/**
	 * Returns true if order empty is set to gretest, otherwise false
     * for order empty is least.
	 */
	public boolean orderEmptyGreatest() {
		return orderEmptyGreatest;
	}
	
	/**
     * The method <code>setOrderEmptyGreatest</code>
     *
     * @param order a <code>boolean</code> value
     */
    public void setOrderEmptyGreatest(final boolean order) {
		this.orderEmptyGreatest = order;
	}
    
    /**
 	 * @return iterator over all modules imported into this context
 	 */
	public Iterator getModules() {
		return modules.values().iterator();
	}
	
	/**
	 *  @return iterator over all modules registered in the entire context tree
	 */
	public Iterator getRootModules() {
		return getAllModules();
	}
	
	public Iterator getAllModules() {
		return allModules.values().iterator();
	}

	/**
	 * Get the built-in module registered for the given namespace
	 * URI.
	 * 
	 * @param namespaceURI
	 * @return built-in module
	 */
	public Module getModule(String namespaceURI) {
		return (Module) modules.get(namespaceURI);
	}
	
	public Module getRootModule(String namespaceURI) {
		return (Module) allModules.get(namespaceURI);
	}
	
	public void setModule(String namespaceURI, Module module) {
        if (module == null) {
            modules.remove(namespaceURI);   // unbind the module
        } else {
            modules.put(namespaceURI, module);
            if( !module.isInternalModule() && module.isReady() ) {
                ( (ModuleContext)( (ExternalModule)module ).getContext() ).setParentContext( this );
            }
        }
		setRootModule(namespaceURI, module);
	}
	
	protected void setRootModule(String namespaceURI, Module module) {
        if (module == null) {
            allModules.remove(namespaceURI); // unbind the module
            return;
        }
		if (allModules.get(namespaceURI) != module) setModulesChanged();
		allModules.put(namespaceURI, module);
	}
	
	void setModulesChanged() {
		this.modulesChanged = true;
	}

    /**
	 * For compiled expressions: check if the source of any
	 * module imported by the current query has changed since
	 * compilation.
	 */
	public boolean checkModulesValid() {
		for(Iterator i = modules.values().iterator(); i.hasNext(); ) {
			Module module = (Module)i.next();
			if(!module.isInternalModule()) {
				if(!((ExternalModule)module).moduleIsValid(getBroker())) {
					LOG.debug("Module with URI " + module.getNamespaceURI() +
							" has changed and needs to be reloaded");
					return false;
				}
			}
		}
		return true;
	}

	public void analyzeAndOptimizeIfModulesChanged(Expression expr) throws XPathException {

        if (analyzed)
               return;
        analyzed = true;

        for (Module module : expr.getContext().modules.values()) {
           if( !module.isInternalModule() ) {
               Expression root = ((ExternalModule)module).getRootExpression();
               ((ExternalModule)module).getContext().analyzeAndOptimizeIfModulesChanged(root);
           }
        }

        expr.analyze(new AnalyzeContextInfo());
        if (optimizationsEnabled()) {
            Optimizer optimizer = new Optimizer(this);
            expr.accept(optimizer);
            if (optimizer.hasOptimized()) {
                reset(true);
                expr.resetState(true);
                expr.analyze(new AnalyzeContextInfo());
            }
        }
        modulesChanged = false;
	}
	
	/**
	 * Load a built-in module from the given class name and assign it to the
	 * namespace URI. The specified class should be a subclass of
	 * {@link Module}. The method will try to instantiate the class. If the
	 * class is not found or an exception is thrown, the method will silently 
	 * fail. The namespace URI has to be equal to the namespace URI declared
	 * by the module class. Otherwise, the module is not loaded.
	 * 
	 * @param namespaceURI
	 * @param moduleClass
	 */
	public Module loadBuiltInModule(String namespaceURI, String moduleClass) {
		Module module = getModule(namespaceURI);
		if (module != null) {
//			LOG.debug("module " + namespaceURI + " is already present");
			return module;
		}
        return initBuiltInModule(namespaceURI, moduleClass);
    }

    protected Module initBuiltInModule(String namespaceURI, String moduleClass) {
        Module module = null;
        try {
            // lookup the class
            Class mClass = Class.forName(moduleClass);
            if (!(Module.class.isAssignableFrom(mClass))) {
                LOG.info(
                        "failed to load module. "
                                + moduleClass
                                + " is not an instance of org.exist.xquery.Module.");
                return null;
            }
            instantiateModule(namespaceURI, mClass);
			//LOG.debug("module " + module.getNamespaceURI() + " loaded successfully.");
		} catch (ClassNotFoundException e) {
			LOG.warn("module class " + moduleClass + " not found. Skipping...");
		}
        return module;
	}

    protected Module instantiateModule(String namespaceURI, Class mClass) {
        Module module = null;
        try {
            module = (Module) mClass.newInstance();
            if (!module.getNamespaceURI().equals(namespaceURI)) {
                LOG.warn("the module declares a different namespace URI. Expected: " + namespaceURI +
                        " found: " + module.getNamespaceURI());
                return null;
            }
            if (getPrefixForURI(module.getNamespaceURI()) == null
                && module.getDefaultPrefix().length() > 0)
                declareNamespace(module.getDefaultPrefix(), module.getNamespaceURI());

            modules.put(module.getNamespaceURI(), module);
            allModules.put(module.getNamespaceURI(), module);
            return module;
        } catch (InstantiationException e) {
            LOG.warn("error while instantiating module class " + mClass.getName(), e);
        } catch (IllegalAccessException e) {
            LOG.warn("error while instantiating module class " + mClass.getName(), e);
        } catch (XPathException e) {
            LOG.warn("error while instantiating module class " + mClass.getName(), e);
        }
        return null;
    }

    /**
	 * Convenience method that returns the XACML Policy Decision Point for 
	 * this database instance.  If XACML has not been enabled, this returns
	 * null.
	 * 
	 * @return the PDP for this database instance, or null if XACML is disabled 
	 */
	public ExistPDP getPDP()
	{
		return broker.getBrokerPool().getSecurityManager().getPDP();
	}
	
	/**
	 * Declare a user-defined function. All user-defined functions are kept
	 * in a single hash map.
	 * 
	 * @param function
	 * @throws XPathException
	 */
	public void declareFunction(UserDefinedFunction function) throws XPathException {
        // TODO: redeclaring functions should be forbidden. however, throwing an
        // exception will currently break util:eval.
		if (Namespaces.XML_NS.equals(function.getSignature().getName().getNamespaceURI()))
			throw new XPathException("XQST0045: function is in the forbidden namespace '" + Namespaces.XML_NS + "'");
		if (Namespaces.SCHEMA_NS.equals(function.getSignature().getName().getNamespaceURI()))
			throw new XPathException("XQST0045: function is in the forbidden namespace '" + Namespaces.SCHEMA_NS + "'");
		if (Namespaces.SCHEMA_INSTANCE_NS.equals(function.getSignature().getName().getNamespaceURI()))
			throw new XPathException("XQST0045: function is in the forbidden namespace '" + Namespaces.SCHEMA_INSTANCE_NS + "'");
        if (Namespaces.XPATH_FUNCTIONS_NS.equals(function.getSignature().getName().getNamespaceURI()))
			throw new XPathException("XQST0045: function is in the forbidden namespace '" + Namespaces.XPATH_FUNCTIONS_NS + "'");
		
		declaredFunctions.put(function.getSignature().getFunctionId(), function);
//		if (declaredFunctions.get(function.getSignature().getFunctionId()) == null)
//				declaredFunctions.put(function.getSignature().getFunctionId(), function);
//		else
//			throw new XPathException("XQST0034: function " + function.getName() + " is already defined with the same arity");
	}

	/**
	 * Resolve a user-defined function.
	 * 
	 * @param name
	 * @return user-defined function
	 * @throws XPathException
	 */
	public UserDefinedFunction resolveFunction(QName name, int argCount) throws XPathException {
		FunctionId id = new FunctionId(name, argCount);
		UserDefinedFunction func = (UserDefinedFunction) declaredFunctions.get(id);
		return func;
	}
	
	public Iterator getSignaturesForFunction(QName name) {
		ArrayList signatures = new ArrayList(2);
		for (Iterator i = declaredFunctions.values().iterator(); i.hasNext(); ) {
			UserDefinedFunction func = (UserDefinedFunction) i.next();
			if (func.getName().equals(name))
				signatures.add(func.getSignature());
		}
		return signatures.iterator();
	}
	
	public Iterator localFunctions() {
		return declaredFunctions.values().iterator();
	}

	/**
	 * Declare a local variable. This is called by variable binding expressions like
	 * "let" and "for".
	 * 
	 * @param var
	 * @throws XPathException
	 */
	public LocalVariable declareVariableBinding(LocalVariable var) throws XPathException {
		if(lastVar == null)
			lastVar = var;
		else {
			lastVar.addAfter(var);
			lastVar = var;
		}
		var.setStackPosition(getCurrentStackSize());
		return var;
	}

	/**
	 * Declare a global variable as by "declare variable".
	 * 
	 * @param var
	 * @throws XPathException
	 */
	public Variable declareGlobalVariable(Variable var) throws XPathException {
		globalVariables.put(var.getQName(), var);
		var.setStackPosition(getCurrentStackSize());
		return var;
	}
	
    public void undeclareGlobalVariable( QName name ) {
        globalVariables.remove(name);
    }

	
	/**
	 * Declare a user-defined variable.
	 * 
	 * The value argument is converted into an XPath value
	 * (@see XPathUtil#javaObjectToXPath(Object)).
	 * 
	 * @param qname the qualified name of the new variable. Any namespaces should
	 * have been declared before.
	 * @param value a Java object, representing the fixed value of the variable
	 * @return the created Variable object
	 * @throws XPathException if the value cannot be converted into a known XPath value
	 * or the variable QName references an unknown namespace-prefix. 
	 */
	public Variable declareVariable(String qname, Object value) throws XPathException {
		return declareVariable(QName.parse(this, qname, null), value);
	}
	
	public Variable declareVariable(QName qn, Object value) throws XPathException {
		Variable var;
		Module module = getModule(qn.getNamespaceURI());
		if(module != null) {
			var = module.declareVariable(qn, value);
			return var;
		}
		Sequence val = XPathUtil.javaObjectToXPath(value, this);
		var = globalVariables.get(qn);
		if(var == null) {			
			var = new Variable(qn);
			globalVariables.put(qn, var);
		}

        if (var.getSequenceType() != null) {
            int actualCardinality;
            if (val.isEmpty()) actualCardinality = Cardinality.EMPTY;
            else if (val.hasMany()) actualCardinality = Cardinality.MANY;
            else actualCardinality = Cardinality.ONE;                	
        	//Type.EMPTY is *not* a subtype of other types ; checking cardinality first
    		if (!Cardinality.checkCardinality(var.getSequenceType().getCardinality(), actualCardinality))
				throw new XPathException("XPTY0004: Invalid cardinality for variable $" + var.getQName() +
						". Expected " +
						Cardinality.getDescription(var.getSequenceType().getCardinality()) +
						", got " + Cardinality.getDescription(actualCardinality));
    		//TODO : ignore nodes right now ; they are returned as xs:untypedAtomicType
    		if (!Type.subTypeOf(var.getSequenceType().getPrimaryType(), Type.NODE)) {
        		if (!val.isEmpty() && !Type.subTypeOf(val.getItemType(), var.getSequenceType().getPrimaryType()))
    				throw new XPathException("XPTY0004: Invalid type for variable $" + var.getQName() +
    						". Expected " +
    						Type.getTypeName(var.getSequenceType().getPrimaryType()) +
    						", got " +Type.getTypeName(val.getItemType()));
    		//Here is an attempt to process the nodes correctly
    		} else {
    			//Same as above : we probably may factorize 
        		if (!val.isEmpty() && !Type.subTypeOf(val.getItemType(), var.getSequenceType().getPrimaryType()))
    				throw new XPathException("XPTY0004: Invalid type for variable $" + var.getQName() +
    						". Expected " +
    						Type.getTypeName(var.getSequenceType().getPrimaryType()) +
    						", got " +Type.getTypeName(val.getItemType()));
    			
    		}
        }
		
		//TODO : should we allow global variable *re*declaration ?
		var.setValue(val);
		return var;
	}

	/**
	 * Try to resolve a variable.
	 * 
	 * @param name the qualified name of the variable as string
	 * @return the declared Variable object
	 * @throws XPathException if the variable is unknown
	 */
	public Variable resolveVariable(String name) throws XPathException {
		QName qn = QName.parse(this, name, null);
		return resolveVariable(qn);
	}
	
	/**
	 * Try to resolve a variable.
	 * 
	 * @param qname the qualified name of the variable
	 * @return the declared Variable object
	 * @throws XPathException if the variable is unknown
	 */
	public Variable resolveVariable(QName qname) throws XPathException {
    	Variable var;
    	
    	// check if the variable is declared local
    	var = resolveLocalVariable(qname);
    	
    	// check if the variable is declared in a module
    	if (var == null){
    	    Module module = getModule(qname.getNamespaceURI());
    	    if(module != null) {
    	        var = module.resolveVariable(qname);
    	    }
    	}
    	
    	// check if the variable is declared global
    	if (var == null) 
    	    var = (Variable) globalVariables.get(qname);
    	//if (var == null)
    	//	throw new XPathException("variable $" + qname + " is not bound");
    	return var;
	}
    
	protected Variable resolveLocalVariable(QName qname) throws XPathException {
        LocalVariable end = contextStack.isEmpty() ? null : contextStack.peek();
		for(LocalVariable var = lastVar; var != null; var = var.before) {
            if (var == end)
                return null;
			if(qname.equals(var.getQName()))
				return var;
		}
		return null;
	}
	
    public boolean isVarDeclared(QName qname) {
        Module module = getModule(qname.getNamespaceURI());
        if(module != null) {
            if (module.isVarDeclared(qname))
                return true;
        }
        return globalVariables.get(qname) != null;
    }
    
    public Map<QName, Variable> getVariables() {
    	
    	Map<QName, Variable> variables = new HashMap<QName, Variable>();
    	
    	variables.putAll(globalVariables);
    	
        LocalVariable end = contextStack.isEmpty() ? null : (LocalVariable) contextStack.peek();
		for(LocalVariable var = lastVar; var != null; var = var.before) {
            if (var == end)
                break;
            
            variables.put(var.getQName(), var);
		}

    	return variables;
    }

    public Map<QName, Variable> getLocalVariables() {
        Map<QName, Variable> variables = new HashMap<QName, Variable>();

        LocalVariable end = contextStack.isEmpty() ? null : (LocalVariable)contextStack.peek();

        for( LocalVariable var = lastVar; var != null; var = var.before ) {

            if( var == end ) {
                break;
            }

            variables.put( var.getQName(), var );
        }

        return( variables );
    }

    public Map<QName, Variable> getGlobalVariables() {
        Map<QName, Variable> variables = new HashMap<QName, Variable>();

        variables.putAll( globalVariables );

        return( variables );
    }
    
	/**
	 * Turn on/off XPath 1.0 backwards compatibility.
	 * 
	 * If turned on, comparison expressions will behave like
	 * in XPath 1.0, i.e. if any one of the operands is a number,
	 * the other operand will be cast to a double.
	 * 
	 * @param backwardsCompatible
	 */
	public void setBackwardsCompatibility(boolean backwardsCompatible) {
		this.backwardsCompatible = backwardsCompatible;
	}

	/**
	 * XPath 1.0 backwards compatibility turned on?
	 * 
	 * In XPath 1.0 compatible mode, additional conversions
	 * will be applied to values if a numeric value is expected.
	 *  
	 */
	public boolean isBackwardsCompatible() {
		return this.backwardsCompatible;
	}
	
	public boolean isRaiseErrorOnFailedRetrieval() {
		return raiseErrorOnFailedRetrieval;
	}

	/**
	 * Get the DBBroker instance used for the current query.
	 * 
	 * The DBBroker is the main database access object, providing
	 * access to all internal database functions.
	 * 
	 * @return DBBroker instance
	 */
	public DBBroker getBroker() {
		return broker;
	}

	public void setBroker(DBBroker broker) {
		this.broker = broker;
	}
	
	/**
	 * Get the user which executes the current query.
	 * 
	 * @return user
	 */
	public User getUser()
	{		
		return getBroker().getUser(); 
	}
	
	/**
	 * If there is a HTTP Session, and a User has been stored in the session then this will
	 * return the user object from the session
	 * 
	 * @return The user or null if there is no session or no user
	 */
	private User getUserFromHttpSession()
	{
        SessionModule myModule = (SessionModule)getModule(SessionModule.NAMESPACE_URI);
		
		//Sanity check : one may *not* want to bind the module !
		if (myModule == null) {
			return null;
		}

		Variable var = null;
		try
		{
			var = myModule.resolveVariable(SessionModule.SESSION_VAR);
		}
		catch(XPathException xpe)
		{
			return null;
		}
		
		if(var != null && var.getValue() != null)
		{
    		if(var.getValue().getItemType() == Type.JAVA_OBJECT)
    		{
        		JavaObjectValue session = (JavaObjectValue) var.getValue().itemAt(0);
        		
        		if(session.getObject() instanceof SessionWrapper)
        		{
                    try {
                        return (User)((SessionWrapper)session.getObject()).getAttribute(HTTP_SESSIONVAR_XMLDB_USER);
                    } catch (IllegalStateException e) {
                        // session is invalid
                        return null;
                    }
                }
    		}
    	}
		
		return null;
	}

	/**
	 * Get the document builder currently used for creating
	 * temporary document fragments. A new document builder
	 * will be created on demand.
	 * 
	 * @return document builder
	 */
	public MemTreeBuilder getDocumentBuilder() {
		if (builder == null) {
			builder = new MemTreeBuilder(this);
			builder.startDocument();
		}
		return builder;
	}
	
	public MemTreeBuilder getDocumentBuilder(boolean explicitCreation) {
		if (builder == null) {
			builder = new MemTreeBuilder(this);
			builder.startDocument(explicitCreation);
		}
		return builder;
	}	

	/**
	 * Utility method to create a new in-memory document using the
	 * provided DocBuilder interface.
	 * 
	 * @param builder a supplied {@link DocBuilder}
	 * @return the document element of the created document
	 */
	public NodeValue createDocument(DocBuilder builder) {
		pushDocumentContext();
		try {
			MemTreeBuilder treeBuilder = getDocumentBuilder();
			builder.build(treeBuilder);
			treeBuilder.endDocument();
			NodeValue node = (NodeValue) treeBuilder.getDocument().getFirstChild();
			if (node == null)
				node = treeBuilder.getDocument().getAttribute(0);
			return node;
		} finally {
			popDocumentContext();
		}
	}
	
    /**
     * Returns the shared name pool used by all in-memory
     * documents which are created within this query context.
     * Create a name pool for every document would be a waste of
     * memory, especially since it is likely that the documents
     * contain elements or attributes with similar names.
     * 
     * @return the shared name pool
     */
    public NamePool getSharedNamePool() {
        if (sharedNamePool == null)
            sharedNamePool = new NamePool();
        return sharedNamePool;
    }

    /* DebuggeeJoint methods */

    public XQueryContext getContext() {
        return null;
    }

    public void expressionStart(Expression expr) throws TerminatedException {
        if (debuggeeJoint != null) {
            debuggeeJoint.expressionStart(expr);
        }
    }

    public void expressionEnd(Expression expr) {
        if (debuggeeJoint != null) {
            debuggeeJoint.expressionEnd(expr);
        }
    }

    public void stackEnter(Expression expr) throws TerminatedException {
        if (debuggeeJoint != null) {
            debuggeeJoint.stackEnter(expr);
        }
    }

    public void stackLeave(Expression expr) {
        if (debuggeeJoint != null) {
            debuggeeJoint.stackLeave(expr);
        }
    }

    /* Methods delegated to the watchdog */
	
	public void proceed() throws TerminatedException {
	    getWatchDog().proceed(null);
	}
	
	public void proceed(Expression expr) throws TerminatedException {
	    getWatchDog().proceed(expr);
	}
	
	public void proceed(Expression expr, MemTreeBuilder builder) throws TerminatedException {
	    getWatchDog().proceed(expr, builder);
	}
	
	public XQueryWatchDog getWatchDog() {
	    return watchdog;
	}
	
	/**
	 * Push any document fragment created within the current
	 * execution context on the stack.
	 */
	public void pushDocumentContext() {
	    fragmentStack.push(builder);
		builder = null;
	}

	public void popDocumentContext() {
		if (!fragmentStack.isEmpty()) {
			builder = (MemTreeBuilder) fragmentStack.pop();
		}
	}

	/**
	 * Set the base URI for the evaluation context.
	 * 
	 * This is the URI returned by the fn:base-uri()
	 * function.
	 * 
	 * @param uri
	 */
	public void setBaseURI(AnyURIValue uri) {
		setBaseURI(uri, false);
	}

    /**
     * Set the base URI for the evaluation context.
     * 
     * A base URI specified via the base-uri directive in the
     * XQuery prolog overwrites any other setting.
     * 
     * @param uri
     * @param setInProlog
     */
    public void setBaseURI(AnyURIValue uri, boolean setInProlog) {
        if (baseURISetInProlog)
            return;
        if (uri == null)
            baseURI = AnyURIValue.EMPTY_URI;
        baseURI = uri;
        baseURISetInProlog = setInProlog;
    }
	/**
	 * Set the path to a base directory where modules should
	 * be loaded from. Relative module paths will be resolved against
	 * this directory. The property is usually set by the XQueryServlet or
	 * XQueryGenerator, but can also be specified manually. 
	 * 
	 * @param path
	 */
	public void setModuleLoadPath(String path) {
		this.moduleLoadPath = path;
	}
	
	public String getModuleLoadPath() {
		return moduleLoadPath;
	}
	

    /**
     * The method <code>isBaseURIDeclared</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean isBaseURIDeclared() {
        if (baseURI == null 
            || baseURI.equals(AnyURIValue.EMPTY_URI)) {
            return false;
        } else {
            return true;
        }
    }

	/**
     * Get the base URI of the evaluation context.
	 * 
	 * This is the URI returned by the fn:base-uri() function.
	 * 
     * @return base URI of the evaluation context
     * @exception XPathException if an error occurs
     */
    public AnyURIValue getBaseURI()
        throws XPathException {
        // the base URI in the static context is established according to the 
        // principles outlined in [RFC3986] Section 5.1—that is, it defaults 
        // first to the base URI of the encapsulating entity, then to the URI 
        // used to retrieve the entity, and finally to an implementation-defined
        // default. If the URILiteral in the base URI declaration is a relative
        // URI, then it is made absolute by resolving it with respect to this 
        // same hierarchy.

        // It is not intrinsically an error if this process fails to establish 
        // an absolute base URI; however, the base URI in the static context 
        // is then undefined, and any attempt to use its value may result in 
        // an error [err:XPST0001].
        if (baseURI == null || baseURI.equals(AnyURIValue.EMPTY_URI)) {
            //throw new XPathException("err:XPST0001: base URI of the static context  has not been assigned a value.");
            // We catch and resolve this to the XmlDbURI.ROOT_COLLECTION_URI
            // at least in DocumentImpl so maybe we should do it here./ljo
        }
		return baseURI;
	}
    
	/**
	 * Set the current context position, i.e. the position
	 * of the currently processed item in the context sequence.
	 * This value is required by some expressions, e.g. fn:position().
	 * 
	 * @param pos
	 */
	public void setContextPosition(int pos) {
		contextPosition = pos;
	}

	/**
	 * Get the current context position, i.e. the position of
	 * the currently processed item in the context sequence.
	 *  
	 * @return current context position
	 */
	public int getContextPosition() {
		return contextPosition;
	}

    public void pushInScopeNamespaces() {
        pushInScopeNamespaces(true);
    }

	/**
	 * Push all in-scope namespace declarations onto the stack.
	 */
	public void pushInScopeNamespaces(boolean inherit) {
		//TODO : push into an inheritedInScopeNamespaces HashMap... and return an empty HashMap
		HashMap m = (HashMap) inScopeNamespaces.clone();
		HashMap p = (HashMap) inScopePrefixes.clone();
		namespaceStack.push(inheritedInScopeNamespaces);
		namespaceStack.push(inheritedInScopePrefixes);
		namespaceStack.push(inScopeNamespaces);
		namespaceStack.push(inScopePrefixes);
		//Current namespaces now become inherited just like the previous inherited ones
        if (inherit) {
            inheritedInScopeNamespaces = (HashMap)inheritedInScopeNamespaces.clone();
            inheritedInScopeNamespaces.putAll(m);
            inheritedInScopePrefixes = (HashMap)inheritedInScopePrefixes.clone();
            inheritedInScopePrefixes.putAll(p);
        } else {
            inheritedInScopeNamespaces = new HashMap();
            inheritedInScopePrefixes = new HashMap();
        }
		//TODO : consider dynamic instanciation
		inScopeNamespaces = new HashMap();
		inScopePrefixes = new HashMap();
	}

	public void popInScopeNamespaces() {
		inScopePrefixes = (HashMap) namespaceStack.pop();
		inScopeNamespaces = (HashMap) namespaceStack.pop();
		inheritedInScopePrefixes = (HashMap) namespaceStack.pop();
		inheritedInScopeNamespaces = (HashMap) namespaceStack.pop();
	}

	public void pushNamespaceContext() {
		HashMap m = (HashMap) staticNamespaces.clone();
		HashMap p = (HashMap) staticPrefixes.clone();		
		namespaceStack.push(staticNamespaces);
		namespaceStack.push(staticPrefixes);		
		staticNamespaces = m;
		staticPrefixes = p;
	}
	
	public void popNamespaceContext() {
		staticPrefixes = (HashMap) namespaceStack.pop();
		staticNamespaces = (HashMap) namespaceStack.pop();
	}
	
	/**
	 * Returns the last variable on the local variable stack.
	 * The current variable context can be restored by passing
	 * the return value to {@link #popLocalVariables(LocalVariable)}.
	 * 
	 * @return last variable on the local variable stack
	 */
	public LocalVariable markLocalVariables(boolean newContext) {
        if (newContext) {
        	if (lastVar == null)
        		lastVar = new LocalVariable(QName.EMPTY_QNAME);
        	contextStack.push(lastVar);
        }
		variableStackSize++;
		return lastVar;
	}
	
	/**
	 * Restore the local variable stack to the position marked
	 * by variable var.
	 * 
	 * @param var
	 */
	public void popLocalVariables(LocalVariable var) {
		if(var != null) {
			var.after = null;
            if (!contextStack.isEmpty() && var == contextStack.peek()) {
                contextStack.pop();
            }
        }
		lastVar = var;
		variableStackSize--;
	}

	/**
	 * Returns the current size of the stack. This is used to determine
	 * where a variable has been declared.
	 * 
	 * @return current size of the stack
	 */
	public int getCurrentStackSize() {
		return variableStackSize;
	}

    /* ----------------- Function call stack ------------------------ */
    
    /**
     * Report the start of a function execution. Adds the reported function signature 
     * to the function call stack.
     */
    public void functionStart(FunctionSignature signature) {
        callStack.push(signature);
    }
    
    /**
     * Report the end of the currently executed function. Pops the last function
     * signature from the function call stack.
     *
     */
    public void functionEnd() {
        if (callStack.isEmpty()) {
            LOG.warn("Function call stack is empty, but XQueryContext.functionEnd() was called. This " +
                "could indicate a concurrency issue (shared XQueryContext?)");
        } else
            callStack.pop();
    }
    
    /**
     * Check if the specified function signature is found in the current function 
     * called stack. If yes, the function might be tail recursive and needs to be
     * optimized. 
     * 
     * @param signature
     */
    public boolean tailRecursiveCall(FunctionSignature signature) {
        return callStack.contains(signature);
    }
    
    /* ----------------- Module imports ------------------------ */
    
    public void mapModule(String namespace, XmldbURI uri) {
    	mappedModules.put(namespace, uri);
    }
    
	/**
	 * Import a module and make it available in this context. The prefix and
	 * location parameters are optional. If prefix is null, the default prefix specified
	 * by the module is used. If location is null, the module will be read from the
	 * namespace URI.
	 * 
	 * @param namespaceURI
	 * @param prefix
	 * @param location
	 * @throws XPathException
	 */
	public void importModule(String namespaceURI, String prefix, String location) throws XPathException {
		Module module = getRootModule(namespaceURI);
		if(module != null) {
			LOG.debug("Module " + namespaceURI + " already present.");
			// Set locally to remember the dependency in case it was inherited.
			setModule(namespaceURI, module);
		} else {
			if(location == null) {
                // check if there's a static mapping in the configuration
                location = getModuleLocation(namespaceURI);
                if (location == null)
				    location = namespaceURI;
            }

			//Is the module's namespace mapped to a URL ?
			if (mappedModules.containsKey(location))
				location = mappedModules.get(location).toString();
			
			// is it a Java module?
			if(location.startsWith(JAVA_URI_START)) {
				location = location.substring(JAVA_URI_START.length());
				module = loadBuiltInModule(namespaceURI, location);
			} else {
				Source source;
                if (location.startsWith(XmldbURI.XMLDB_URI_PREFIX) ||
                        (location.indexOf(':') < 0 &&
                        moduleLoadPath.startsWith(XmldbURI.XMLDB_URI_PREFIX))) {
                    // Is the module source stored in the database?
    				try {
    					XmldbURI locationUri = XmldbURI.xmldbUriFor(location);
    					if (moduleLoadPath.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
    						XmldbURI moduleLoadPathUri = XmldbURI.xmldbUriFor(moduleLoadPath);
    						locationUri = moduleLoadPathUri.resolveCollectionPath(locationUri);
    					}
    	                DocumentImpl sourceDoc = null;
                        try {
                            sourceDoc = broker.getXMLResource(locationUri.toCollectionPathURI(), Lock.READ_LOCK);
                            if (sourceDoc == null)
                                throw new XPathException("source for module " + location + " not found in database");
                            if (sourceDoc.getResourceType() != DocumentImpl.BINARY_FILE ||
                                    !sourceDoc.getMetadata().getMimeType().equals("application/xquery"))
                                throw new XPathException("source for module " + location + " is not an XQuery or " +
                                "declares a wrong mime-type");
                            source = new DBSource(broker, (BinaryDocument) sourceDoc, true);
                            // we don't know if the module will get returned, oh well
                            module = compileOrBorrowModule(prefix, namespaceURI, location, source);
                        } catch (PermissionDeniedException e) {
                            throw new XPathException("permission denied to read module source from " + location);
                        } finally {
                            if(sourceDoc != null)
                                sourceDoc.getUpdateLock().release(Lock.READ_LOCK);
                        }
    				} catch(URISyntaxException e) {
                        throw new XPathException(e.getMessage(), e);
                    }
                } else {                 	
					// No. Load from file or URL
                    try {
                    	//TODO: use URIs to ensure proper resolution of relative locations
                        source = SourceFactory.getSource(broker, moduleLoadPath, location, true);
                    } catch (MalformedURLException e) {
                        throw new XPathException("source location for module " + namespaceURI + " should be a valid URL: " +
                                e.getMessage());
                    } catch (IOException e) {
                        throw new XPathException("source for module '" + namespaceURI + "' not found at '" + location + "': " +
                                e.getMessage());
                    } catch (PermissionDeniedException e) {
                    	throw new XPathException("Permission denied to access module '" + namespaceURI + "' at '" + location + "': " +
                                e.getMessage());
                    }  
                    // we don't know if the module will get returned, oh well
                    module = compileOrBorrowModule(prefix, namespaceURI, location, source);
                }
			}
		}
		if(prefix == null)
			prefix = module.getDefaultPrefix();
		declareNamespace(prefix, namespaceURI);
	}

    /**
     * Returns the static location mapped to an XQuery source module, if known.
     *
     * @param namespaceURI the URI of the module
     * @return the location string
     */
    public String getModuleLocation(String namespaceURI) {
        Map moduleMap = (Map) broker.getConfiguration().getProperty(PROPERTY_STATIC_MODULE_MAP);
        return (String) moduleMap.get(namespaceURI);
    }

    /**
     * Returns an iterator over all module namespace URIs which are statically
     * mapped to a known location.
     * @return an iterator
     */
    public Iterator getMappedModuleURIs() {
        Map moduleMap = (Map) broker.getConfiguration().getProperty(PROPERTY_STATIC_MODULE_MAP);
        return moduleMap.keySet().iterator();
    }

	private ExternalModule compileOrBorrowModule(String prefix, String namespaceURI, String location, Source source) throws XPathException {
		ExternalModule module = broker.getBrokerPool().getXQueryPool().borrowModule(broker, source, this);
		if (module == null) {
			module = compileModule(prefix, namespaceURI, location, source);
		} else {
            for (Iterator it = module.getContext().getAllModules(); it.hasNext();) {
                Module importedModule = (Module) it.next();
                if (importedModule != null && 
					!allModules.containsKey(importedModule.getNamespaceURI())) {
                    setRootModule(importedModule.getNamespaceURI(), importedModule);
                }
            }
        }
     	setModule(module.getNamespaceURI(), module);
        declareModuleVars(module);
      return module;
	}

    /**
     * @return The compiled module.
     * @throws XPathException
     */
    private ExternalModule compileModule(String prefix, String namespaceURI, String location, Source source) throws XPathException {
        LOG.debug("Loading module from " + location);
        
        Reader reader;
        try {
        	reader = source.getReader();
            if (reader == null) {
                throw new XPathException("failed to load module '" + namespaceURI + "' from '" + source +
                    ". Source not found. ");
            }
        } catch (IOException e) {
        	throw new XPathException("IO exception while loading module '" + namespaceURI + "' from '" + source + "'", e);
        }
        ExternalModuleImpl modExternal = new ExternalModuleImpl(namespaceURI, prefix);
        setModule(namespaceURI, modExternal);
        XQueryContext modContext = new ModuleContext(this, prefix, namespaceURI, location);
        modExternal.setContext( modContext );
        XQueryLexer lexer = new XQueryLexer(modContext, reader);
        XQueryParser parser = new XQueryParser(lexer);
        XQueryTreeParser astParser = new XQueryTreeParser(modContext, modExternal);
        try {
        	parser.xpath();
        	if (parser.foundErrors()) {
        		LOG.debug(parser.getErrorMessage());
        		throw new XPathException(
        			"error found while loading module from " + location + ": "
        				+ parser.getErrorMessage());
        	}
        	AST ast = parser.getAST();

        	PathExpr path = new PathExpr(modContext);
        	astParser.xpath(ast, path);
        	if (astParser.foundErrors()) {
        		throw new XPathException(
        			"error found while loading module from " + location + ": "
        				+ astParser.getErrorMessage(),
        			astParser.getLastException());
        	}
            
           
        	path.analyze(new AnalyzeContextInfo());
        	if(modExternal == null)
        		throw new XPathException("source at " + location + " is not a valid module");  
            
            modExternal.setRootExpression(path);
            
        	if(!modExternal.getNamespaceURI().equals(namespaceURI))
        		throw new XPathException("namespace URI declared by module (" + modExternal.getNamespaceURI() + 
        			") does not match namespace URI in import statement, which was: " + namespaceURI);
            // Set source information on module context
//            String sourceClassName = source.getClass().getName();
            modContext.setSource(XACMLSource.getInstance(source));
//            modContext.setSourceKey(source.getKey().toString());
            // Extract the source type from the classname by removing the package prefix and the "Source" suffix
//            modContext.setSourceType( sourceClassName.substring( 17, sourceClassName.length() - 6 ) );
            
            modExternal.setSource(source);
        	modExternal.setContext(modContext);
        	modExternal.setIsReady(true);
        	return modExternal;
        } catch (RecognitionException e) {
        	throw new XPathException(e.getLine(), e.getColumn(),
        		"error found while loading module from " + location + ": " + e.getMessage());
        } catch (TokenStreamException e) {
        	throw new XPathException(
        		"error found while loading module from " + location + ": " + e.getMessage(),
        		e);
        } catch (XPathException e) {
            e.prependMessage("Error while loading module " + location + ": ");
            throw e;
        } catch (Exception e) {
            throw new XPathException("Internal error while loading module: " + location, e);
        } finally {
            try {
				if (reader != null)
                	reader.close();
            } catch (IOException e) {
                LOG.warn("Error while closing module source: " + e.getMessage(), e);
            }
        }
    }

    private void declareModuleVars(Module module) {
        String moduleNS = module.getNamespaceURI();
        for (Iterator<Variable> i = globalVariables.values().iterator(); i.hasNext(); ) {
            Variable var = i.next();
            if (moduleNS.equals(var.getQName().getNamespaceURI())) {
                module.declareVariable(var);
				i.remove();
            }
        }
    }
    
	/**
	 * Add a forward reference to an undeclared function. Forward
	 * references will be resolved later.
	 * 
	 * @param call
	 */
	public void addForwardReference(FunctionCall call) {
		forwardReferences.push(call);
	}
	
	/**
	 * Resolve all forward references to previously undeclared functions.
	 * 
	 * @throws XPathException
	 */
	public void resolveForwardReferences() throws XPathException {
		while(!forwardReferences.empty()) {
			FunctionCall call = (FunctionCall)forwardReferences.pop();
			UserDefinedFunction func = call.getContext().resolveFunction( call.getQName(), call.getArgumentCount() );
			if(func == null)
				throw new XPathException(call, 
					"Call to undeclared function: " + call.getQName().getStringValue());
			call.resolveForwardReference(func);
		}
	}

    public boolean optimizationsEnabled() {
        return enableOptimizer;
    }
    
    /**
     * for static compile-time options i.e. declare option
     */
    public void addOption(String qnameString, String contents) throws XPathException
    {
    	if(staticOptions == null)
			staticOptions = new ArrayList();
    	
		addOption(staticOptions, qnameString, contents);
    }
    
    /**
     * for dynamic run-time options i.e. util:declare-option
     */
    public void addDynamicOption(String qnameString, String contents) throws XPathException
    {
    	if(dynamicOptions == null)
			dynamicOptions = new ArrayList();
    	
		addOption(dynamicOptions, qnameString, contents);
    }
    
    private void addOption(List options, String qnameString, String contents) throws XPathException
    {
    	QName qn = QName.parse(this, qnameString, defaultFunctionNamespace);

		Option option = new Option(qn, contents);
		
		//if the option exists, remove it so we can add the new option
		for(int i = 0; i < options.size(); i++)
		{
			if(options.get(i).equals(option))
			{
				options.remove(i);
				break;
			}
		}
		
		//add option
		options.add(option);
		
		// check predefined options
        if (Option.PROFILE_QNAME.compareTo(qn) == 0) {
            // configure profiling
            profiler.configure(option);
        } else if(Option.TIMEOUT_QNAME.compareTo(qn) == 0)
			watchdog.setTimeoutFromOption(option);
        else if(Option.OUTPUT_SIZE_QNAME.compareTo(qn) == 0)
			watchdog.setMaxNodesFromOption(option);
        else if (Option.OPTIMIZE_QNAME.compareTo(qn) == 0) {
            String params[] = option.tokenizeContents();
            if (params.length > 0) {
                String[] param = Option.parseKeyValuePair(params[0]);
                if ("enable".equals(param[0])) {
                    if ("yes".equals(param[1]))
                        enableOptimizer = true;
                    else
                        enableOptimizer = false;
                }
            }
        }
        //TODO : not sure how these 2 options might/have to be related
        else if (Option.OPTIMIZE_IMPLICIT_TIMEZONE.compareTo(qn) == 0) {
        	//TODO : error check
        	Duration duration = TimeUtils.getInstance().newDuration(option.getContents());         	
        	implicitTimeZone = new SimpleTimeZone((int)duration.getTimeInMillis(new Date()), "XQuery context"); 
        }
        else if (Option.CURRENT_DATETIME.compareTo(qn) == 0) {
        	//TODO : error check
        	DateTimeValue dtv = new DateTimeValue(option.getContents());
        	calendar = (XMLGregorianCalendar)dtv.calendar.clone();
        }
    }
    
    
	
	public Option getOption(QName qname)
	{
		/*
		 * check dynamic options that were declared at run-time
		 * first as these have precedence and then check
		 * static options that were declare at compile time 
		 */
		if(dynamicOptions != null)
		{
			for(int i = 0; i < dynamicOptions.size(); i++)
			{
				Option option = (Option)dynamicOptions.get(i);
				if(qname.compareTo(option.getQName()) == 0)
				{
					return option;
				}
			}
		}
		
		if(staticOptions != null)
		{
			for(int i = 0; i < staticOptions.size(); i++)
			{
				Option option = (Option)staticOptions.get(i);
				if(qname.compareTo(option.getQName()) == 0)
				{
					return option;
				}
			}
		}
		
		return null;
	}
	
    public Pragma getPragma(String name, String contents) throws XPathException {
        QName qname = QName.parse(this, name);
        if ("".equals(qname.getNamespaceURI())) {
        	throw new XPathException("XPST0081: pragma's ('" + name +"') namespace URI is empty");
        } else if (Namespaces.EXIST_NS.equals(qname.getNamespaceURI())) {
            contents = StringValue.trimWhitespace(contents);
            if (TimerPragma.TIMER_PRAGMA.equalsSimple(qname)) {
                return new TimerPragma(qname, contents);
            }
            if (Optimize.OPTIMIZE_PRAGMA.equalsSimple(qname)) {
                return new Optimize(this, qname, contents, true);
            }
            if (BatchTransactionPragma.BATCH_TRANSACTION_PRAGMA.equalsSimple(qname)) {
                return new BatchTransactionPragma(qname, contents);
            }
            if (ForceIndexUse.EXCEPTION_IF_INDEX_NOT_USED_PRAGMA.equalsSimple(qname)) {
            	return new ForceIndexUse(qname, contents);
            }
            if (ProfilePragma.PROFILING_PRAGMA.equalsSimple(qname)) {
            	return new ProfilePragma(qname, contents);
            }
            if (NoIndexPragma.NO_INDEX_PRAGMA.equalsSimple(qname)) {
                return new NoIndexPragma(qname, contents);
            }
        }
        return null;
    }
    
	/**
	 * Store the supplied data to a temporary document fragment.
	 * 
	 * @param doc
	 * @throws XPathException
	 */
	public DocumentImpl storeTemporaryDoc(org.exist.memtree.DocumentImpl doc) throws XPathException {
		try {
			DocumentImpl targetDoc = broker.storeTempResource(doc);
            if (targetDoc == null)
                throw new XPathException("Internal error: failed to store temporary doc fragment");
            LOG.debug("Stored: " + targetDoc.getDocId() + ": " + targetDoc.getURI());
			return targetDoc;
		} catch (EXistException e) {
			throw new XPathException(TEMP_STORE_ERROR, e);
		} catch (PermissionDeniedException e) {
			throw new XPathException(TEMP_STORE_ERROR, e);
		} catch (LockException e) {
			throw new XPathException(TEMP_STORE_ERROR, e);
		}
	}

    public void setAttribute(String attribute, Object value) {
        attributes.put(attribute, value);
    }

    public Object getAttribute(String attribute) {
        return attributes.get(attribute);
    }
    
    /**
	 * Set an XQuery Context variable.
	 * General variable storage in the xquery context
	 * 
	 * @param name The variable name
	 * @param XQvar The variable value, may be of any xs: type 
	 */
    public void setXQueryContextVar(String name, Object XQvar)
    {
    	XQueryContextVars.put(name, XQvar);
   }
    
    /**
	 * Get an XQuery Context variable.
	 * General variable storage in the xquery context
	 * 
	 * @param name The variable name
	 * @return The variable value indicated by name.
	 */
    public Object getXQueryContextVar(String name)
    {
    	return(XQueryContextVars.get(name));
    }
    
    
    /**
     *	Starts a batch Transaction 
     */
    public void startBatchTransaction() throws TransactionException
    {
    	//only allow one batch to exist at once, if there is a current batch then commit them
    	if(batchTransaction != null)
    		finishBatchTransaction();
    	
    	TransactionManager txnMgr = getBroker().getBrokerPool().getTransactionManager();
    	batchTransaction = txnMgr.beginTransaction();
    }

    /**
     *	Determines if a batch transaction should be performed
     *
     * 	@return true if a batch update transaction should be performed
     */
    public boolean hasBatchTransaction()
    {
    	return(batchTransaction != null);
    }
    
    /**
     * Get the Transaction for the batch
     * 
     * @return The Transaction
     */
    public Txn getBatchTransaction()
    {
    	return batchTransaction;
    }
    
    /**
     * Set's that a trigger should be executed for the provided document as part of the batch transaction
     * 
     * @param	doc	The document to trigger for
     */
    public void setBatchTransactionTrigger(DocumentImpl doc)
    {
    	//we want the last updated version of the document, so remove any previous version (matched by xmldburi)
    	Iterator itTrigDoc = batchTransactionTriggers.getDocumentIterator();
    	while(itTrigDoc.hasNext())
    	{
    		DocumentImpl trigDoc = (DocumentImpl)itTrigDoc.next();
    		if(trigDoc.getURI().equals(doc.getURI()))
    		{
    			itTrigDoc.remove();
    			break;
    		}
    	}
    
    	//store the document so we can later finish the trigger
    	batchTransactionTriggers.add(doc);
    }
    
    /**
     * Completes a batch transaction, by committing the transaction and calling finish on any triggers
     * set by setBatchTransactionTrigger()
     * */
    public void finishBatchTransaction() throws TransactionException
    {
    	if(batchTransaction != null)
    	{
    		//commit the transaction batch
    		TransactionManager txnMgr = getBroker().getBrokerPool().getTransactionManager();
    		txnMgr.commit(batchTransaction);
    	
    		//finish any triggers
    		Iterator itDoc = batchTransactionTriggers.getDocumentIterator();
    		while(itDoc.hasNext())
    		{
    			DocumentImpl doc = (DocumentImpl)itDoc.next();
    			
    			//finish the trigger
    	        CollectionConfiguration config = doc.getCollection().getConfiguration(getBroker());
    	        if(config != null)
    	        {
                    DocumentTrigger trigger = null;
                    try {
                        trigger = (DocumentTrigger)config.newTrigger(Trigger.UPDATE_DOCUMENT_EVENT, getBroker(), doc.getCollection());
                    } catch (CollectionConfigurationException e) {
                        LOG.debug("An error occurred while initializing a trigger for collection " + doc.getCollection().getURI() + ": " + e.getMessage(), e);
                    }
                    if(trigger != null)
    	        	{
    	        		try
    	        		{
    	        			trigger.finish(Trigger.UPDATE_DOCUMENT_EVENT, getBroker(), TriggerStatePerThread.getTransaction(), doc.getURI(), doc);
    	        		}
    	        		catch(Exception e)
    	        		{
    	        			LOG.debug("Trigger event UPDATE_DOCUMENT_EVENT for collection: " + doc.getCollection().getURI() + " with: " + doc.getURI() + " " + e.getMessage());
    	        		}
    	        	}
    	        }
    		}
    		batchTransactionTriggers.clear();
    		
    		batchTransaction = null;
    	}
    }
    
	/**
	 * Load the default prefix/namespace mappings table and set up
	 * internal functions.
	 */
	protected void loadDefaults(Configuration config) {
		this.watchdog = new XQueryWatchDog(this);
		
		/*
		SymbolTable syms = broker.getSymbols();
		String[] pfx = syms.defaultPrefixList();
		namespaces = new HashMap(pfx.length);
		prefixes = new HashMap(pfx.length);
		String sym;
		for (int i = 0; i < pfx.length; i++) {
			sym = syms.getDefaultNamespace(pfx[i]);
			namespaces.put(pfx[i], sym);
			prefixes.put(sym, pfx[i]);			
		}	
		*/			

		loadDefaultNS();

        String param = (String) getBroker().getConfiguration().getProperty(PROPERTY_ENABLE_QUERY_REWRITING);
        enableOptimizer = param != null && param.equals("yes");
        
        param = (String) getBroker().getConfiguration().getProperty(PROPERTY_XQUERY_BACKWARD_COMPATIBLE);
        backwardsCompatible = param == null || param.equals("yes");
        Boolean option = ((Boolean) getBroker().getConfiguration().getProperty(PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL));
        raiseErrorOnFailedRetrieval = option != null && option.booleanValue();
        
        // load built-in modules
        Map modules = (Map) config.getProperty(PROPERTY_BUILT_IN_MODULES);
        if (modules != null) {
            for (Iterator i = modules.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry entry = (Map.Entry) i.next();
                Class mClass = (Class) entry.getValue();
                String namespaceURI = (String) entry.getKey();
                // first check if the module has already been loaded
                // in the parent context
                Module module = getModule(namespaceURI);
                if (module == null) {
                    instantiateModule(namespaceURI, mClass);
                } else if (getPrefixForURI(module.getNamespaceURI()) == null
                        && module.getDefaultPrefix().length() > 0) {
                    // make sure the namespaces of default modules are known,
                    // even if they were imported in a parent context
                    try {
                        declareNamespace(module.getDefaultPrefix(), module.getNamespaceURI());
                    } catch (XPathException e) {
                        LOG.warn("Internal error while loading default modules: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    protected void loadDefaultNS() {
        try {
			// default namespaces
			staticNamespaces.put("xml", Namespaces.XML_NS);
			staticPrefixes.put(Namespaces.XML_NS, "xml");
			declareNamespace("xs", Namespaces.SCHEMA_NS);
			declareNamespace("xsi", Namespaces.SCHEMA_INSTANCE_NS);
			//required for backward compatibility
			declareNamespace("xdt", Namespaces.XPATH_DATATYPES_NS);
			declareNamespace("fn", Namespaces.XPATH_FUNCTIONS_NS);
			declareNamespace("local", Namespaces.XQUERY_LOCAL_NS);
			//*not* as standard NS
			declareNamespace("exist", Namespaces.EXIST_NS);
			//TODO : include "err" namespace ?
		} catch (XPathException e) {
			//TODO : ignored because it should never happen
		}
    }
    
    public void registerUpdateListener(UpdateListener listener) {
		if (updateListener == null) {
			updateListener = new ContextUpdateListener();
			broker.getBrokerPool().getNotificationService().subscribe(updateListener);
		}
		updateListener.addListener(listener);
	}
	
	protected void clearUpdateListeners() {
		if (updateListener != null)
			broker.getBrokerPool().getNotificationService().unsubscribe(updateListener);
		updateListener = null;
	}

    /**
     * Check if the XQuery contains pragmas that define serialization settings.
     * If yes, copy the corresponding settings to the current set of output
     * properties.
     *
     * @param properties the properties object to which serialization parameters will
     * be added.
     * @throws XPathException if an error occurs while parsing the option
     */
    public void checkOptions(Properties properties) throws XPathException
    {
        Option pragma = getOption(Option.SERIALIZE_QNAME);
        if (pragma == null)
            return;
        String[] contents = pragma.tokenizeContents();
        for (int i = 0; i < contents.length; i++) {
            String[] pair = Option.parseKeyValuePair(contents[i]);
            if (pair == null)
                throw new XPathException("Unknown parameter found in "
                        + pragma.getQName().getStringValue() + ": '" + contents[i]
                        + "'");
            LOG.debug("Setting serialization property from pragma: " + pair[0] + " = " + pair[1]);
            properties.setProperty(pair[0], pair[1]);
        }
    }
    
    /**
     * Read list of built-in modules from the configuration. This method will only make sure
     * that the specified module class exists and is a subclass of {@link org.exist.xquery.Module}.
     *
     * @param xquery configuration root
     * @throws DatabaseConfigurationException
     */
    public static void loadModuleClasses(Element xquery, Map classMap, Map externalMap)
        throws DatabaseConfigurationException {
        // add the standard function module
        classMap.put(Namespaces.XPATH_FUNCTIONS_NS, org.exist.xquery.functions.ModuleImpl.class);
        // add other modules specified in configuration
        NodeList builtins = xquery.getElementsByTagName(CONFIGURATION_MODULES_ELEMENT_NAME);
        if (builtins.getLength() > 0) {
            Element elem = (Element) builtins.item(0);
            NodeList modules = elem.getElementsByTagName(CONFIGURATION_MODULE_ELEMENT_NAME);
            if (modules.getLength() > 0) {
	            for (int i = 0; i < modules.getLength(); i++) {
	                elem = (Element) modules.item(i);
	                String uri = elem.getAttribute(XQueryContext.BUILT_IN_MODULE_URI_ATTRIBUTE);
	                String clazz = elem.getAttribute(XQueryContext.BUILT_IN_MODULE_CLASS_ATTRIBUTE);
                    String source = elem.getAttribute(XQueryContext.BUILT_IN_MODULE_SOURCE_ATTRIBUTE);
	                if (uri == null)
	                    throw new DatabaseConfigurationException("element 'module' requires an attribute 'uri'");
	                if (clazz == null && source == null)
	                    throw new DatabaseConfigurationException("element 'module' requires either an attribute " +
                                "'class' or 'src'");
                    if (source != null) {
                        externalMap.put(uri, source);
                        if (LOG.isDebugEnabled())
                            LOG.debug("Registered mapping for module '" + uri + "' to '" + source + "'");
                    } else {
                        Class mClass = lookupModuleClass(uri, clazz);
                        if (mClass != null)
                            classMap.put(uri, mClass);
                        if (LOG.isDebugEnabled())
                            LOG.debug("Configured module '" + uri + "' implemented in '" + clazz + "'");
                    }
                }
            }
        }
    }

    private static Class lookupModuleClass(String uri, String clazz) throws DatabaseConfigurationException {
        try {
            Class mClass = Class.forName(clazz);
            if (!(Module.class.isAssignableFrom(mClass))) {
                throw new DatabaseConfigurationException("Failed to load module: " + uri + ". Class " +
                        clazz + " is not an instance of org.exist.xquery.Module.");
            }
            return mClass;
            
        } catch (ClassNotFoundException e) {
            // Note: can't throw an exception here since this would create
            // problems with test cases and jar dependencies
            LOG.warn("Configuration problem: failed to load class for module " +
                    uri + "; class: " + clazz + "; message: " + e.getMessage());
            
        } catch (NoClassDefFoundError e){
            LOG.warn("Module " + uri + " could not be initialized due to a missing " +
                    "dependancy (NoClassDefFoundError): " + e.getMessage());
            
        }
        return null;
    }

    private class ContextUpdateListener implements UpdateListener {

		private List listeners = new ArrayList();

        public void addListener(UpdateListener listener) {
			listeners.add(listener);
		}
		
		public void documentUpdated(DocumentImpl document, int event) {
			for (int i = 0; i < listeners.size(); i++) {
                UpdateListener listener = (UpdateListener) listeners.get(i);
                if (listener != null)
                    listener.documentUpdated(document, event);
			}
		}

        public void unsubscribe() {
            for (int i = 0; i < listeners.size(); i++) {
                UpdateListener listener = (UpdateListener) listeners.get(i);
                if (listener != null) {
                    listener.unsubscribe();
                }
            }
            listeners.clear();
        }

        public void nodeMoved(NodeId oldNodeId, StoredNode newNode) {
            for (int i = 0; i < listeners.size(); i++) {
                UpdateListener listener = (UpdateListener) listeners.get(i);
                if (listener != null)
                    listener.nodeMoved(oldNodeId, newNode);
			}
        }

        public void debug() {
			LOG.debug("XQueryContext: ");
			for (int i = 0; i < listeners.size(); i++) {
				((UpdateListener) listeners.get(i)).debug();
			}
		}
		
	}

    private DebuggeeJoint debuggeeJoint = null;
    
	public void setDebuggeeJoint(DebuggeeJoint joint) {
		//XXX: if (debuggeeJoint != null) ???
		debuggeeJoint = joint;
	}

	public DebuggeeJoint getDebuggeeJoint() {
		return debuggeeJoint;
	}
	
	private boolean isDebugMode = false;

	public void setDebugMode(boolean isDebugMode) {
		this.isDebugMode = isDebugMode;
	}
	
	public boolean isDebugMode() {
		return isDebugMode;
	}
}
