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

import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.text.Collator;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.Trigger;
import org.exist.collections.triggers.TriggerStatePerThread;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.http.servlets.SessionWrapper;
import org.exist.memtree.MemTreeBuilder;
import org.exist.numbering.NodeId;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.security.xacml.AccessContext;
import org.exist.security.xacml.ExistPDP;
import org.exist.security.xacml.NullAccessContextException;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.storage.DBBroker;
import org.exist.storage.UpdateListener;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Collations;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

/**
 * The current XQuery execution context. Contains the static as well
 * as the dynamic XQuery context components.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class XQueryContext {
	
	public static final String CONFIGURATION_ELEMENT_NAME = "xquery";
	public static final String CONFIGURATION_MODULES_ELEMENT_NAME = "builtin-modules";
	public static final String CONFIGURATION_MODULE_ELEMENT_NAME = "module";
	public static final String PROPERTY_BUILT_IN_MODULES = "xquery.modules";
	public static final String PROPERTY_XQUERY_BACKWARD_COMPATIBLE = "xquery.backwardCompatible";
	public static final String PROPERTY_ENABLE_QUERY_REWRITING = "xquery.enable-query-rewriting";
	
	private static final String JAVA_URI_START = "java:";
    //private static final String XMLDB_URI_START = "xmldb:exist://";
    
    public final static String XQUERY_LOCAL_NS =
		"http://www.w3.org/2003/08/xquery-local-functions";

    protected final static Logger LOG = Logger.getLogger(XQueryContext.class);

	private static final String TEMP_STORE_ERROR = "Error occurred while storing temporary data";
	
    private static final HashMap moduleClasses = new HashMap();
    
	// Static namespace/prefix mappings
	protected HashMap namespaces = new HashMap();
	
	// Local in-scope namespace/prefix mappings in the current context
	protected HashMap inScopeNamespaces = new HashMap();

	// Static prefix/namespace mappings
	protected HashMap prefixes = new HashMap();
	
	// Local prefix/namespace mappings in the current context
	protected final HashMap inScopePrefixes = new HashMap();

	// Local namespace stack
	protected final Stack namespaceStack = new Stack();

	// Known user defined functions in the local module
	protected TreeMap declaredFunctions = new TreeMap();

	// Globally declared variables
	protected TreeMap globalVariables = new TreeMap();

	// The last element in the linked list of local in-scope variables
	protected LocalVariable lastVar = null;
	
    protected Stack contextStack = new Stack();
    
    protected final Stack callStack = new Stack();
    
	// The current size of the variable stack
	protected int variableStackSize = 0;
	
	// Unresolved references to user defined functions
	protected final Stack forwardReferences = new Stack();
	
	// List of options declared for this query
	protected List options = null;
	
    /**
     * the watchdog object assigned to this query
     *  
     * @uml.property name="watchdog"
     * @uml.associationEnd multiplicity="(1 1)"
     */
	protected XQueryWatchDog watchdog;
    
	/**
	 * Loaded modules.
	 */
	protected HashMap modules = new HashMap();

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
	 * The main database broker object providing access
	 * to storage and indexes. Every XQuery has its own
	 * DBBroker object.
	 */
	protected DBBroker broker;

	protected AnyURIValue baseURI = AnyURIValue.EMPTY_URI;
	
    protected boolean baseURISetInProlog = false;
    
	protected String moduleLoadPath = ".";
	
	protected String defaultFunctionNamespace = Function.BUILTIN_FUNCTION_NS;

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
	private boolean lockDocumentsOnLoad = false;
    
    /**
     * Documents locked during the query.
     */
	private LockedDocumentMap lockedDocuments = null;
    
    /**
     * The profiler instance used by this context.
     */
    private Profiler profiler = new Profiler();
    
    //For holding XQuery Context variables from setXQueryContextVar() and getXQueryContextVar()
    HashMap XQueryContextVars = new HashMap();
    public static final String XQUERY_CONTEXTVAR_XQUERY_UPDATE_ERROR = "_eXist_xquery_update_error";
    public static final String HTTP_SESSIONVAR_XMLDB_USER = "_eXist_xmldb_user";

    //Transaction for  batched xquery updates
    private Txn batchTransaction = null;
    private DocumentSet batchTransactionTriggers = new DocumentSet();
    
	private AccessContext accessCtx;
    
	private ContextUpdateListener updateListener = null;

    private boolean enableOptimizer = true;

    private XQueryContext() {}
	
	protected XQueryContext(AccessContext accessCtx) {
		if(accessCtx == null)
			throw new NullAccessContextException();
		this.accessCtx = accessCtx;
		builder = new MemTreeBuilder(this);
		builder.startDocument();
	}
	
	public XQueryContext(DBBroker broker, AccessContext accessCtx) {
		this(accessCtx);
		this.broker = broker;
		loadDefaults(broker.getConfiguration());
	}
	
	public XQueryContext(XQueryContext copyFrom) {
        this(copyFrom.getAccessContext());
        this.broker = copyFrom.broker;
        loadDefaultNS();
        Iterator prefixes = copyFrom.namespaces.keySet().iterator();
        while (prefixes.hasNext()) {
            String prefix = (String)prefixes.next();
            if (prefix.equals("xml") || prefix.equals("xmlns")) {
                continue;
            }
            try {
                declareNamespace(prefix,(String)copyFrom.namespaces.get(prefix));
            } catch (XPathException ex) {
                ex.printStackTrace();
            }
        }
    }

    public XQueryContext copyContext() {
        XQueryContext ctx = new XQueryContext(this);
        copyFields(ctx);
        return ctx;
    }

    protected void copyFields(XQueryContext ctx) {
        ctx.baseURI = this.baseURI;
        ctx.baseURISetInProlog = this.baseURISetInProlog;
        ctx.staticDocumentPaths = this.staticDocumentPaths;
        ctx.staticDocuments = this.staticDocuments;
        ctx.moduleLoadPath = this.moduleLoadPath;
        ctx.defaultFunctionNamespace = this.defaultFunctionNamespace;
        ctx.defaultCollation = this.defaultCollation;
        ctx.defaultCollator = this.defaultCollator;
        ctx.backwardsCompatible = this.backwardsCompatible;
        ctx.enableOptimizer = this.enableOptimizer;
        ctx.stripWhitespace = this.stripWhitespace;

        ctx.declaredFunctions = new TreeMap(this.declaredFunctions);
        ctx.globalVariables = new TreeMap(this.globalVariables);
        ctx.modules = new HashMap(this.modules);
        ctx.watchdog = this.watchdog;

        ctx.lastVar = this.lastVar;
        ctx.variableStackSize = getCurrentStackSize();
        ctx.contextStack = this.contextStack;
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
    
	/**
	 * Declare a user-defined prefix/namespace mapping.
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
		final String prevURI = (String)namespaces.get(prefix);
		//This prefix was not bound
		if(prevURI == null ) {
			//Bind it
			if (uri.length() > 0) {
				namespaces.put(prefix, uri);
				prefixes.put(uri, prefix);
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
	            prefixes.remove(uri);
	            namespaces.remove(prefix);
	            return;
			}
			//Forbids rebinding the *same* prefix in a *different* namespace in this *same* context
			if (!uri.equals(prevURI))
				throw new XPathException("err:XQST0033: Namespace prefix '" + prefix + "' is already bound to a different uri '" + prevURI + "'");
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
            namespaces.put(prefix, uri);
			prefixes.put(uri, prefix);
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
		if (inScopeNamespaces == null)
			inScopeNamespaces = new HashMap();
		inScopeNamespaces.put(prefix, uri);
	}

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
			throw new XPathException("XQST0066: default function namespace is already set to: '" + defaultFunctionNamespace + "'");
		defaultFunctionNamespace = uri;
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
		defaultCollator = Collations.getCollationFromURI(this, uri);
		defaultCollation = uri;
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
	 * Return the namespace URI mapped to the registered prefix
	 * or null if the prefix is not registered.
	 * 
	 * @param prefix
	 * @return namespace
	 */
	public String getURIForPrefix(String prefix) {
            // try in-scope namespace declarations
           String ns =  inScopeNamespaces == null
				? null
				: (String) inScopeNamespaces.get(prefix);
           if (ns==null) {
              // Check global declarations
              return (String)namespaces.get(prefix);
           } else {
              return ns;
           }
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
		String prefix = (String) prefixes.get(uri);
		if (prefix == null)
			return inScopePrefixes == null ? null : (String) inScopeNamespaces.get(uri);
		else
			return prefix;
	}

	/**
	 * Removes the namespace URI from the prefix/namespace 
	 * mappings table.
	 * 
	 * @param uri
	 */
	public void removeNamespace(String uri) {
		prefixes.remove(uri);
		for (Iterator i = namespaces.values().iterator(); i.hasNext();) {
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
	}

	/**
	 * Clear all user-defined prefix/namespace mappings.
	 */
	public void clearNamespaces() {
		namespaces.clear();
		prefixes.clear();
		if (inScopeNamespaces != null) {
			inScopeNamespaces.clear();
			inScopePrefixes.clear();
		}
		loadDefaults(broker.getConfiguration());
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
	
	/**
	 * @return set of statically known documents.
	 */
	public DocumentSet getStaticallyKnownDocuments() throws XPathException {
		if(staticDocuments != null)
            // the document set has already been built, return it
			return staticDocuments;
		staticDocuments = new DocumentSet(1031);
		if(staticDocumentPaths == null)
            // no path defined: return all documents in the db 
			broker.getAllXMLResources(staticDocuments);
		else {
			DocumentImpl doc;
			Collection collection;
			for(int i = 0; i < staticDocumentPaths.length; i++) {
				try {
                    collection = broker.getCollection(staticDocumentPaths[i]);
                    if (collection != null) {
                        collection.allDocs(broker, staticDocuments, true, true);
                    } else {
                        doc = broker.getXMLResource(staticDocumentPaths[i], Lock.READ_LOCK);
                        if(doc != null) {
                            if(doc.getPermissions().validate(broker.getUser(), Permission.READ)) {
                                staticDocuments.add(doc);
                            }
                            doc.getUpdateLock().release(Lock.READ_LOCK);
                        }
                    }
				} catch(PermissionDeniedException e) {
					LOG.warn("Permission denied to read resource " + staticDocumentPaths[i] + ". Skipping it.");
				}
			}
		}
		return staticDocuments;
	}
	
	/**
	 * Should loaded documents be locked?
	 * 
         * @see #setLockDocumentsOnLoad(boolean)
         * 
	 */
	public boolean lockDocumentsOnLoad() {
	    return lockDocumentsOnLoad;
	}
	
	/**
	 * If lock is true, all documents loaded during query execution
	 * will be locked. This way, we avoid that query results become
	 * invalid before the entire result has been processed by the client
	 * code. All attempts to modify nodes which are part of the result
	 * set will be blocked.
	 * 
	 * However, it is the client's responsibility to proper unlock
	 * all documents once processing is completed.
	 * 
	 * @param lock
	 */
	public void setLockDocumentsOnLoad(boolean lock) {
	    lockDocumentsOnLoad = lock;
	    if(lock)
	        lockedDocuments = new LockedDocumentMap();
	}

    public void addLockedDocument(DocumentImpl doc) {
        if (lockedDocuments != null)
           lockedDocuments.add(doc);
    }
    /**
     * Release all locks on documents that have been locked
     * during query execution.
     *
     *@see #setLockDocumentsOnLoad(boolean)
     */
	public void releaseLockedDocuments() {
        if(lockedDocuments != null)
	        lockedDocuments.unlock();
	    lockDocumentsOnLoad = false;
		lockedDocuments = null;
	}
	
    /**
     * Release all locks on documents not being referenced by the sequence.
     * This is called after query execution has completed. Only locks on those
     * documents contained in the final result set will be preserved. All other
     * locks are released as they are no longer needed.
     * 
     * @param seq
     * @throws XPathException 
     */
	public LockedDocumentMap releaseUnusedDocuments(Sequence seq) throws XPathException {
	    if(lockedDocuments == null)
	        return null;
        // determine the set of documents referenced by nodes in the sequence
        DocumentSet usedDocs = new DocumentSet();
        for(SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            Item next = i.nextItem();
            if(Type.subTypeOf(next.getType(), Type.NODE)) {
                NodeValue node = (NodeValue) next;
                if(node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                    DocumentImpl doc = ((NodeProxy)node).getDocument();
                    if(!usedDocs.contains(doc.getDocId()))
	                    usedDocs.add(doc, false);
                }
            }
        }
        LockedDocumentMap remaining = lockedDocuments.unlockSome(usedDocs);
        lockDocumentsOnLoad = false;
		lockedDocuments = null;
        return remaining;
    }

    public void reset() {
        reset(false);
    }

    /**
	 * Prepare this XQueryContext to be reused. This should be
     * called when adding an XQuery to the cache.
	 */
	public void reset(boolean keepGlobals) {
        builder = new MemTreeBuilder(this);
		builder.startDocument();
		staticDocumentPaths = null;
		staticDocuments = null;
		lastVar = null;
		fragmentStack = new Stack();
		callStack.clear();

        if (!keepGlobals)
            globalVariables.clear();
        
        //remove the context-vars, subsequent execution of the query
		//may generate different values for the vars based on the
		//content of the db
		XQueryContextVars.clear();
		
		watchdog.reset();
        profiler.reset();
		for(Iterator i = modules.values().iterator(); i.hasNext(); ) {
			Module module = (Module)i.next();
			module.reset();
		}
		clearUpdateListeners();
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
	 * @return iterator over all built-in modules currently
	 * registered.
	 */
	public Iterator getModules() {
		return modules.values().iterator();
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

	/**
	 * For compiled expressions: check if the source of any
	 * module imported by the current query has changed since
	 * compilation.
	 */
	public boolean checkModulesValid() {
		for(Iterator i = modules.values().iterator(); i.hasNext(); ) {
			Module module = (Module)i.next();
			if(!module.isInternalModule()) {
				if(!((ExternalModule)module).moduleIsValid()) {
					LOG.debug("Module with URI " + module.getNamespaceURI() + 
							" has changed and needs to be reloaded");
					return false;
				}
			}
		}
		return true;
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
            Class mClass = (Class) moduleClasses.get(moduleClass);
            if (mClass == null) {
    			mClass = Class.forName(moduleClass);
    			if (!(Module.class.isAssignableFrom(mClass))) {
    				LOG.info(
    					"failed to load module. "
    						+ moduleClass
    						+ " is not an instance of org.exist.xquery.Module.");
    				return null;
    			}
                moduleClasses.put(moduleClass, mClass);
            }
			module = (Module) mClass.newInstance();
			if (!module.getNamespaceURI().equals(namespaceURI)) {
				LOG.warn("the module declares a different namespace URI. Skipping...");
				return null;
			}
			if (getPrefixForURI(module.getNamespaceURI()) == null
				&& module.getDefaultPrefix().length() > 0)
				declareNamespace(module.getDefaultPrefix(), module.getNamespaceURI());

			modules.put(module.getNamespaceURI(), module);
			//LOG.debug("module " + module.getNamespaceURI() + " loaded successfully.");
		} catch (ClassNotFoundException e) {
			LOG.warn("module class " + moduleClass + " not found. Skipping...");
		} catch (InstantiationException e) {
			LOG.warn("error while instantiating module class " + moduleClass, e);
		} catch (IllegalAccessException e) {
			LOG.warn("error while instantiating module class " + moduleClass, e);
		} catch (XPathException e) {
			LOG.warn("error while instantiating module class " + moduleClass, e);
		}		
		return module;
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
		//TODO : forbid "http://www.w3.org/2005/xpath-functions"
		
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
		QName qn = QName.parse(this, qname, null);
		Variable var;
		Module module = getModule(qn.getNamespaceURI());
		if(module != null) {
			var = module.declareVariable(qn, value);
			return var;
		}
		Sequence val = XPathUtil.javaObjectToXPath(value, this);
		var = (Variable)globalVariables.get(qn);
		if(var == null) {			
			var = new Variable(qn);
			globalVariables.put(qn, var);
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
    	if (var == null)
    		throw new XPathException("variable $" + qname + " is not bound");
    	return var;
	}
    
	protected Variable resolveLocalVariable(QName qname) throws XPathException {
        LocalVariable end = contextStack.isEmpty() ? null : (LocalVariable) contextStack.peek();
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
		return broker.getUser();
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
        			return (User)((SessionWrapper)session.getObject()).getAttribute(HTTP_SESSIONVAR_XMLDB_USER);
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
	
	/* Methods delegated to the watchdog */
	
	public void proceed() throws TerminatedException {
	    proceed(null);
	}
	
	public void proceed(Expression expr) throws TerminatedException {
	    watchdog.proceed(expr);
	}
	
	public void proceed(Expression expr, MemTreeBuilder builder) throws TerminatedException {
	    watchdog.proceed(expr, builder);
	}
	
	public void recover() {
	    watchdog.reset();
	    builder = null;
	}
	
	public XQueryWatchDog getWatchDog() {
	    return watchdog;
	}
	
	protected void setWatchDog(XQueryWatchDog watchdog) {
	    this.watchdog = watchdog;
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
	 * Get the base URI of the evaluation context.
	 * 
	 * This is the URI returned by the fn:base-uri() function.
	 * 
	 * @return base URI of the evaluation context
	 */
	public AnyURIValue getBaseURI() {
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

	/**
	 * Push all in-scope namespace declarations onto the stack.
	 */
	public void pushInScopeNamespaces() {
		HashMap m = (HashMap) inScopeNamespaces.clone();
		namespaceStack.push(inScopeNamespaces);
		inScopeNamespaces = m;
	}

	public void popInScopeNamespaces() {
		inScopeNamespaces = (HashMap) namespaceStack.pop();
	}

	public void pushNamespaceContext() {
		HashMap m = (HashMap) namespaces.clone();
		HashMap p = (HashMap) prefixes.clone();
		namespaceStack.push(namespaces);
		namespaceStack.push(prefixes);
		namespaces = m;
		prefixes = p;
	}
	
	public void popNamespaceContext() {
		prefixes = (HashMap) namespaceStack.pop();
		namespaces = (HashMap) namespaceStack.pop();
	}
	
	/**
	 * Returns the last variable on the local variable stack.
	 * The current variable context can be restored by passing
	 * the return value to {@link #popLocalVariables(LocalVariable)}.
	 * 
	 * @return last variable on the local variable stack
	 */
	public LocalVariable markLocalVariables(boolean newContext) {
        if (newContext)
            contextStack.push(lastVar);
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
	public void importModule(String namespaceURI, String prefix, String location)
		throws XPathException {
		Module module = getModule(namespaceURI);
		if(module != null) {
			LOG.debug("Module " + namespaceURI + " already present.");
		} else {
			if(location == null)
				location = namespaceURI;
			// is it a Java module?
			if(location.startsWith(JAVA_URI_START)) {
				location = location.substring(JAVA_URI_START.length());
				module = loadBuiltInModule(namespaceURI, location);
			} else {
				Source source;
                if (location.startsWith(XmldbURI.XMLDB_URI_PREFIX) || 
                        moduleLoadPath.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
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
                            module = compileModule(namespaceURI, location, module, source);
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
                        throw new XPathException("source for module " + namespaceURI + " not found: " +
                                e.getMessage());
                    } catch (PermissionDeniedException e) {
                    	throw new XPathException("Permission denied to access module " + namespaceURI + " : " +
                                e.getMessage());
                    }
                    
                    module = compileModule(namespaceURI, location, module, source);
                }
			}
		}
		if(prefix == null)
			prefix = module.getDefaultPrefix();
		declareNamespace(prefix, namespaceURI);
	}

    /**
     * @param namespaceURI
     * @param location
     * @param module
     * @param source
     * @return The compiled module.
     * @throws XPathException
     */
    private Module compileModule(String namespaceURI, String location, Module module, Source source) throws XPathException {
        LOG.debug("Loading module from " + location);
        
        Reader reader;
        try {
        	reader = source.getReader();
        } catch (IOException e) {
        	throw new XPathException("IO exception while loading module " + namespaceURI, e);
        }
        XQueryContext modContext = new ModuleContext(this);
        XQueryLexer lexer = new XQueryLexer(modContext, reader);
        XQueryParser parser = new XQueryParser(lexer);
        XQueryTreeParser astParser = new XQueryTreeParser(modContext);
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
        	ExternalModule modExternal = astParser.getModule();
        	if(modExternal == null)
        		throw new XPathException("source at " + location + " is not a valid module");
        	if(!modExternal.getNamespaceURI().equals(namespaceURI))
        		throw new XPathException("namespace URI declared by module (" + modExternal.getNamespaceURI() + 
        			") does not match namespace URI in import statement, which was: " + namespaceURI);
        	modules.put(modExternal.getNamespaceURI(), modExternal);
        	modExternal.setSource(source);
        	modExternal.setContext(modContext);
        	module = modExternal;
        } catch (RecognitionException e) {
        	throw new XPathException(
        		"error found while loading module from " + location + ": " + e.getMessage(),
        		e.getLine(), e.getColumn());
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
                reader.close();
            } catch (IOException e) {
                LOG.warn("Error while closing module source: " + e.getMessage(), e);
            }
        }
        declareModuleVars(module);
        return module;
    }

    private void declareModuleVars(Module module) {
        String moduleNS = module.getNamespaceURI();
        for (Iterator i = globalVariables.values().iterator(); i.hasNext(); ) {
            Variable var = (Variable) i.next();
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
			UserDefinedFunction func = resolveFunction(call.getQName(), call.getArgumentCount());
			if(func == null)
				throw new XPathException(call.getASTNode(), 
					"Call to undeclared function: " + call.getQName().getStringValue());
			call.resolveForwardReference(func);
		}
	}

    public boolean optimizationsEnabled() {
        return enableOptimizer;
    }
    
    public void addOption(String qnameString, String contents) throws XPathException {
		QName qn = QName.parse(this, qnameString, defaultFunctionNamespace);

		Option option = new Option(qn, contents);
		if(options == null)
			options = new ArrayList();
		
		// check if this overwrites an already existing option
		boolean added = false;
		Option old;
		for (int i = 0; i < options.size(); i++) {
			old = (Option) options.get(i);
			if (old.equals(option)) {
				options.add(i, option);
				added = true;
				break;
			}
		}
		// add the option to the list if it does not yet exist
		if (!added)
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
    }
	
	public Option getOption(QName qname) {
		if(options != null) {
			for(int i = 0; i < options.size(); i++) {
				Option option = (Option)options.get(i);
				if(qname.compareTo(option.getQName()) == 0)
					return option;
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
            } else if (Optimize.OPTIMIZE_PRAGMA.equalsSimple(qname)) {
                return new Optimize(this, qname, contents, true);
            }
            if (BatchTransactionPragma.BATCH_TRANSACTION_PRAGMA.equalsSimple(qname)) {
                return new BatchTransactionPragma(qname, contents);
            }
            if (ForceIndexUse.EXCEPTION_IF_INDEX_NOT_USED_PRAGMA.equalsSimple(qname)) {
            	return new ForceIndexUse(qname, contents);
            }
            if (ProfilePragma.PROFILIE_PRAGMA.equalsSimple(qname)) {
            	return new ProfilePragma(qname, contents);
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
	
	/**
	 * Set an XQuery Context variable.
	 * Used by the context extension module; called by context:set-var().
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
	 * Used by the context extension module; called by context:get-var().
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
    	Iterator itTrigDoc = batchTransactionTriggers.iterator();
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
    		Iterator itDoc = batchTransactionTriggers.iterator();
    		while(itDoc.hasNext())
    		{
    			DocumentImpl doc = (DocumentImpl)itDoc.next();
    			
    			//finish the trigger
    	        CollectionConfiguration config = doc.getCollection().getConfiguration(doc.getBroker());
    	        if(config != null)
    	        {
    	        	DocumentTrigger trigger = (DocumentTrigger)config.getTrigger(Trigger.UPDATE_DOCUMENT_EVENT);
    	        	if(trigger != null)
    	        	{
    	        		try
    	        		{
    	        			trigger.finish(Trigger.UPDATE_DOCUMENT_EVENT, doc.getBroker(), TriggerStatePerThread.getTransaction(), doc);
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
	 * Set the serializer to use for output
	 * Used by the context extension module; called by context:set-serializer().
	 * 
	 * @param name The name of the serializer to use
	 * @param indent Should the output be indented?
	 * @param omitxmldeclaration Should the output omit the xml declaration?
	 */
    public void setXQuerySerializer(String name, boolean indent, boolean omitxmldeclaration) throws XPathException
    {
    	Option option;

    	//Has a exist:serialize option already been set?
    	for(int i = 0; i < options.size(); i++)
    	{
    		option = (Option)options.get(i);
    		if(option.getQName().equals("exist:serialize"))
    		{
    			//yes, so modify the content from the existing option
    			String content = option.getContents();
    			if(content.indexOf("method=") != Constants.STRING_NOT_FOUND)
    			{
    				content = content.replaceFirst("method=[^/ ]*", "method=" + name);
    			}
    			else
    			{
    				content += " method=" + name;
    			}
    			if(content.indexOf("indent=") != Constants.STRING_NOT_FOUND)
    			{
    				content = content.replaceFirst("indent=[^/ ]*", "indent=" + (indent ? "yes":"no"));
    			}
    			else
    			{
    				content += " indent" + (indent ? "yes":"no");
    			}
    			if(content.indexOf("omit-xml-declaration") != Constants.STRING_NOT_FOUND)
    			{
    				content = content.replaceFirst("omit-xml-declaration=[^/ ]*", "omit-xml-declaration=" + (omitxmldeclaration ? "yes":"no"));
    			}
    			else
    			{
    				content += " omit-xml-declaration" +  (omitxmldeclaration ? "yes":"no");
    			}
    			
    			//Delete the existing serialize option
    			options.remove(i);
    			
    			//Add the new serialize option
    			addOption("exist:serialize", content);
    			
    			return; //done
    		}
    	}
    	
    	//no, so set a option for serialization
    	addOption("exist:serialize", "method=" + name + " indent=" + (indent ? "yes":"no") + " omit-xml-declaration=" + (omitxmldeclaration ? "yes":"no"));
    	
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
        backwardsCompatible = param == null ? true : param.equals("yes");
        
        // load built-in modules
		
		// these modules are loaded dynamically. It is not an error if the
		// specified module class cannot be found in the classpath.
		loadBuiltInModule(
			Function.BUILTIN_FUNCTION_NS,
			"org.exist.xquery.functions.ModuleImpl");
		
		String modules[][] = (String[][]) config.getProperty(PROPERTY_BUILT_IN_MODULES);
		if ( modules != null ) {
			for (int i = 0; i < modules.length; i++) {
				//LOG.debug("Loading module " + modules[i][0]);
				loadBuiltInModule(modules[i][0], modules[i][1]);
			}
		}		
	}

    protected void loadDefaultNS() {
        try {
			// default namespaces
			namespaces.put("xml", Namespaces.XML_NS);
			prefixes.put(Namespaces.XML_NS, "xml");
			declareNamespace("xs", Namespaces.SCHEMA_NS);
			declareNamespace("xsi", Namespaces.SCHEMA_INSTANCE_NS);
			//required for backward compatibility
			declareNamespace("xdt", Namespaces.XPATH_DATATYPES_NS);
			declareNamespace("fn", Function.BUILTIN_FUNCTION_NS);
			declareNamespace("local", XQUERY_LOCAL_NS);
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
    public void checkOptions(Properties properties)
    throws XPathException {
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
}
