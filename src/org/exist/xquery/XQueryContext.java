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
package org.exist.xquery;

import java.io.File;
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

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.debuggee.Debuggee;
import org.exist.debuggee.DebuggeeJoint;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.SessionWrapper;
import org.exist.interpreter.Context;
import org.exist.memtree.InMemoryXMLStreamReader;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.repo.ExistRepository;
import org.exist.security.AuthenticationException;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.xacml.*;
import org.exist.source.*;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.UpdateListener;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.util.Collations;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.hashtable.NamePool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.parser.*;
import org.exist.xquery.pragmas.*;
import org.exist.xquery.update.Modification;
import org.exist.xquery.value.*;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * The current XQuery execution context. Contains the static as well as the dynamic
 * XQuery context components.
 *
 * @author  Wolfgang Meier (wolfgang@exist-db.org)
 */
public class XQueryContext implements BinaryValueManager, Context
{
    public static final String                         ENABLE_QUERY_REWRITING_ATTRIBUTE                 = "enable-query-rewriting";
    public static final String                         XQUERY_BACKWARD_COMPATIBLE_ATTRIBUTE             = "backwardCompatible";
    public static final String                         XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_ATTRIBUTE = "raise-error-on-failed-retrieval";
    public static final String						   ENFORCE_INDEX_USE_ATTRIBUTE					    = "enforce-index-use";

    //TODO : move elsewhere ?
    public static final String                         BUILT_IN_MODULE_URI_ATTRIBUTE                    = "uri";
    public static final String                         BUILT_IN_MODULE_CLASS_ATTRIBUTE                  = "class";
    public static final String                         BUILT_IN_MODULE_SOURCE_ATTRIBUTE                 = "src";

    public static final String                         PROPERTY_XQUERY_BACKWARD_COMPATIBLE              = "xquery.backwardCompatible";
    public static final String                         PROPERTY_ENABLE_QUERY_REWRITING                  = "xquery.enable-query-rewriting";
    public static final String                         PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL  = "xquery.raise-error-on-failed-retrieval";
    public static final boolean                        XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_DEFAULT   = false;
    public static final String						   PROPERTY_ENFORCE_INDEX_USE						= "xquery.enforce-index-use";

    //TODO : move elsewhere ?
    public static final String                         PROPERTY_BUILT_IN_MODULES                        = "xquery.modules";
    public static final String                         PROPERTY_STATIC_MODULE_MAP                       = "xquery.modules.static";
    public static final String                         PROPERTY_MODULE_PARAMETERS                       = "xquery.modules.parameters";

    public static final String                        JAVA_URI_START                                   = "java:";
    //private static final String XMLDB_URI_START = "xmldb:exist://";

    protected final static Logger                      LOG                                              = Logger.getLogger( XQueryContext.class );

    private static final String                        TEMP_STORE_ERROR                                 = "Error occurred while storing temporary data";
    public static final String                         XQUERY_CONTEXTVAR_XQUERY_UPDATE_ERROR            = "_eXist_xquery_update_error";
    public static final String                         HTTP_SESSIONVAR_XMLDB_USER                       = "_eXist_xmldb_user";
    public static final String                         HTTP_REQ_ATTR_USER                               = "xquery.user";
    public static final String                         HTTP_REQ_ATTR_PASS                               = "xquery.password";

    // Static namespace/prefix mappings
    protected HashMap<String, String>                  staticNamespaces                                 = new HashMap<String, String>();

    // Static prefix/namespace mappings
    protected HashMap<String, String>                  staticPrefixes                                   = new HashMap<String, String>();

    // Local in-scope namespace/prefix mappings in the current context
    protected HashMap<String, String>                  inScopeNamespaces                                = new HashMap<String, String>();

    // Local prefix/namespace mappings in the current context
    protected HashMap<String, String>                  inScopePrefixes                                  = new HashMap<String, String>();

    // Inherited in-scope namespace/prefix mappings in the current context
    protected HashMap<String, String>                  inheritedInScopeNamespaces                       = new HashMap<String, String>();

    // Inherited prefix/namespace mappings in the current context
    protected HashMap<String, String>                  inheritedInScopePrefixes                         = new HashMap<String, String>();

    protected HashMap<String, XmldbURI>                mappedModules                                    = new HashMap<String, XmldbURI>();

    private boolean                                    preserveNamespaces                               = true;

    private boolean                                    inheritNamespaces                                = true;

    // Local namespace stack
    protected Stack<HashMap<String, String>>           namespaceStack                                   = new Stack<HashMap<String, String>>();

    // Known user defined functions in the local module
    protected TreeMap<FunctionId, UserDefinedFunction> declaredFunctions                                = new TreeMap<FunctionId, UserDefinedFunction>();

    // Globally declared variables
    protected Map<QName, Variable>                     globalVariables                                  = new TreeMap<QName, Variable>();

    // The last element in the linked list of local in-scope variables
    protected LocalVariable                            lastVar                                          = null;

    protected Stack<LocalVariable>                     contextStack                                     = new Stack<LocalVariable>();

    protected Stack<FunctionSignature>                 callStack                                        = new Stack<FunctionSignature>();

    // The current size of the variable stack
    protected int                                      variableStackSize                                = 0;

    // Unresolved references to user defined functions
    protected Stack<FunctionCall>                      forwardReferences                                = new Stack<FunctionCall>();

    // List of options declared for this query at compile time - i.e. declare option
    protected List<Option>                             staticOptions                                    = null;

    // List of options declared for this query at run time - i.e. util:declare-option()
    protected List<Option>                             dynamicOptions                                   = null;

    //The Calendar for this context : may be changed by some options
    XMLGregorianCalendar                               calendar                                         = null;
    TimeZone                                           implicitTimeZone                                 = null;

    /** the watchdog object assigned to this query. */
    protected XQueryWatchDog                           watchdog;

    /** Loaded modules. */
    protected HashMap<String, Module>                  modules                                          = new HashMap<String, Module>();

    /** Loaded modules, including ones bubbled up from imported modules. */
    protected HashMap<String, Module>                  allModules                                       = new HashMap<String, Module>();

    /** Used to save current state when modules are imported dynamically */
    protected SavedState		   					   savedState										= new SavedState();
    
    /**
     * Whether some modules were rebound to new instances since the last time this context's query was analyzed. (This assumes that each context is
     * attached to at most one query.)
     */
    @SuppressWarnings( "unused" )
    private boolean                                    modulesChanged                = true;

    /** The set of statically known documents specified as an array of paths to documents and collections. */
    protected XmldbURI[]                               staticDocumentPaths           = null;

    /** The actual set of statically known documents. This will be generated on demand from staticDocumentPaths. */
    protected DocumentSet                              staticDocuments               = null;

    /** The set of statically known documents specified as an array of paths to documents and collections. */
    protected XmldbURI[]                               staticCollections             = null;

    /**
     * A set of documents which were modified during the query, usually through an XQuery update extension. The documents will be checked after the
     * query completed to see if a defragmentation run is needed.
     */
    protected MutableDocumentSet                       modifiedDocuments             = null;

    /** A general-purpose map to set attributes in the current query context. */
    protected Map<String, Object>                      attributes                    = new HashMap<String, Object>();

    protected AnyURIValue                              baseURI                       = AnyURIValue.EMPTY_URI;

    protected boolean                                  baseURISetInProlog            = false;

    protected String                                     moduleLoadPath                = ".";

    protected String                                   defaultFunctionNamespace      = Function.BUILTIN_FUNCTION_NS;
    protected AnyURIValue                              defaultElementNamespace       = AnyURIValue.EMPTY_URI;
    protected AnyURIValue                              defaultElementNamespaceSchema = AnyURIValue.EMPTY_URI;

    /** The default collation URI. */
    private String                                     defaultCollation              = Collations.CODEPOINT;

    /** Default Collator. Will be null for the default unicode codepoint collation. */
    private Collator                                   defaultCollator               = null;

    /** Set to true to enable XPath 1.0 backwards compatibility. */
    private boolean                                    backwardsCompatible           = false;

    /** Should whitespace inside node constructors be stripped? */
    private boolean                                    stripWhitespace               = true;

    /** Should empty order greatest or least? */
    private boolean                                    orderEmptyGreatest            = true;

    /**
     * The position of the currently processed item in the context sequence. This field has to be set on demand, for example, before calling the
     * fn:position() function.
     */
    private int                                        contextPosition               = 0;
    private Sequence                                   contextSequence               = null;

    /** Shared name pool used by all in-memory documents constructed in this query context. */
    private NamePool                                   sharedNamePool                = null;

    /** Stack for temporary document fragments. */
    private Stack<MemTreeBuilder>                      fragmentStack                 = new Stack<MemTreeBuilder>();

    /** The root of the expression tree. */
    private Expression                                 rootExpression;

    /** An incremental counter to count the expressions in the current XQuery. Used during compilation to assign a unique ID to every expression. */
    private int                                        expressionCounter             = 0;

    /**
     * Should all documents loaded by the query be locked? If set to true, it is the responsibility of the calling client code to unlock documents
     * after the query has completed.
     */
//  private boolean lockDocumentsOnLoad = false;

    /** Documents locked during the query. */
//  private LockedDocumentMap lockedDocuments = null;

    private LockedDocumentMap                          protectedDocuments            = null;

    /** The profiler instance used by this context. */
    protected Profiler                                 profiler;

    //For holding XQuery Context variables for general storage in the XQuery Context
    HashMap<String, Object>                            XQueryContextVars             = new HashMap<String, Object>();
    
    //For holding the environment variables
    Map<String,String> envs;
    
    private AccessContext                              accessCtx;

    private ContextUpdateListener                      updateListener                = null;

    private boolean                                    enableOptimizer               = true;

    private boolean                                    raiseErrorOnFailedRetrieval   = XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_DEFAULT;

    private boolean                                    isShared                      = false;

    private Source source = null;
    
    private XACMLSource                                xacmlSource                        = null;

    private DebuggeeJoint                              debuggeeJoint                 = null;

    private int                                        xqueryVersion                 = 10;
    
    protected Database db;

    private boolean analyzed = false;

    public synchronized ExistRepository getRepository()
    throws XPathException {
        return getBroker().getBrokerPool().getExpathRepo();
    }

    private Module resolveInEXPathRepository(String namespace, String prefix)
            throws XPathException
    {
        // the repo and its eXist handler
        final ExistRepository repo = getRepository();
        // try an internal module
        final Module mod = repo.resolveJavaModule(namespace, this);
        if ( mod != null ) {
            return mod;
        }
        // try an eXist-specific module
        final File resolved = repo.resolveXQueryModule(namespace);
        // use the resolved file or return null
        if ( resolved == null ) {
            return null;
        }
        // build a module object from the file
        final Source src = new FileSource(resolved, "utf-8", false);
        return compileOrBorrowModule(prefix, namespace, "", src);
    }
    // TODO: end of expath repo manageer, may change


    protected XQueryContext( AccessContext accessCtx )
    {
        if( accessCtx == null ) {
            throw( new NullAccessContextException() );
        }
        this.accessCtx = accessCtx;
        profiler = new Profiler( null );
    }


    public XQueryContext( Database db, AccessContext accessCtx )
    {
        this( accessCtx );
        this.db = db;
        loadDefaults( db.getConfiguration() );
        this.profiler = new Profiler( db );
    }


    public XQueryContext( XQueryContext copyFrom )
    {
        this( copyFrom.getAccessContext() );
        this.db = copyFrom.db;
        loadDefaultNS();
        final Iterator<String> prefixes = copyFrom.staticNamespaces.keySet().iterator();

        while( prefixes.hasNext() ) {
            final String prefix = prefixes.next();

            if( "xml".equals(prefix) || "xmlns".equals(prefix) ) {
                continue;
            }

            try {
                declareNamespace( prefix, copyFrom.staticNamespaces.get( prefix ) );
            }
            catch( final XPathException ex ) {
                ex.printStackTrace();
            }
        }
        this.profiler = copyFrom.profiler;
    }

    /**
     * Returns true if this context has a parent context (means it is a module context).
     *
     * @return  False.
     */
    public boolean hasParent()
    {
        return( false );
    }


    public XQueryContext getRootContext()
    {
        return( this );
    }


    public XQueryContext copyContext()
    {
        final XQueryContext ctx = new XQueryContext( this );
        copyFields( ctx );
        return( ctx );
    }


    /**
     * Update the current dynamic context using the properties of another context. This is needed by {@link org.exist.xquery.functions.util.Eval}.
     *
     * @param  from
     */
    public void updateContext( XQueryContext from )
    {
        this.watchdog                   = from.watchdog;
        this.lastVar                    = from.lastVar;
        this.variableStackSize          = from.getCurrentStackSize();
        this.contextStack               = from.contextStack;
        this.inScopeNamespaces          = from.inScopeNamespaces;
        this.inScopePrefixes            = from.inScopePrefixes;
        this.inheritedInScopeNamespaces = from.inheritedInScopeNamespaces;
        this.inheritedInScopePrefixes   = from.inheritedInScopePrefixes;
        this.variableStackSize          = from.variableStackSize;
        this.attributes                 = from.attributes;
        this.updateListener             = from.updateListener;
        this.modules                    = from.modules;
        this.allModules                 = from.allModules;
        this.mappedModules              = from.mappedModules;
        this.dynamicOptions				= from.dynamicOptions;
        this.staticOptions				= from.staticOptions;
        this.db							= from.db;
    }


    protected void copyFields( XQueryContext ctx )
    {
        ctx.calendar                 = this.calendar;
        ctx.implicitTimeZone         = this.implicitTimeZone;
        ctx.baseURI                  = this.baseURI;
        ctx.baseURISetInProlog       = this.baseURISetInProlog;
        ctx.staticDocumentPaths      = this.staticDocumentPaths;
        ctx.staticDocuments          = this.staticDocuments;
        ctx.moduleLoadPath           = this.moduleLoadPath;
        ctx.defaultFunctionNamespace = this.defaultFunctionNamespace;
        ctx.defaultElementNamespace  = this.defaultElementNamespace;
        ctx.defaultCollation         = this.defaultCollation;
        ctx.defaultCollator          = this.defaultCollator;
        ctx.backwardsCompatible      = this.backwardsCompatible;
        ctx.enableOptimizer          = this.enableOptimizer;
        ctx.stripWhitespace          = this.stripWhitespace;
        ctx.preserveNamespaces       = this.preserveNamespaces;
        ctx.inheritNamespaces        = this.inheritNamespaces;
        ctx.orderEmptyGreatest       = this.orderEmptyGreatest;

        ctx.declaredFunctions        = new TreeMap<FunctionId, UserDefinedFunction>( this.declaredFunctions );
        ctx.globalVariables          = new TreeMap<QName, Variable>( this.globalVariables );
        ctx.attributes               = new HashMap<String, Object>( this.attributes );

        // make imported modules available in the new context
        ctx.modules                  = new HashMap<String, Module>();

        for( final Module module : this.modules.values() ) {

            try {
                ctx.modules.put( module.getNamespaceURI(), module );
                final String prefix = this.staticPrefixes.get( module.getNamespaceURI() );
                ctx.declareNamespace( prefix, module.getNamespaceURI() );
            }
            catch( final XPathException e ) {
                // ignore
            }
        }
        ctx.allModules = new HashMap<String, Module>();

        for( final Module module : this.allModules.values() ) {

            if( module != null ) { //UNDERSTAND: why is it possible? -shabanovd
                ctx.allModules.put( module.getNamespaceURI(), module );
            }
        }

        ctx.watchdog          = this.watchdog;
        ctx.profiler          = getProfiler();
        ctx.lastVar           = this.lastVar;
        ctx.variableStackSize = getCurrentStackSize();
        ctx.contextStack      = this.contextStack;
        ctx.mappedModules     = new HashMap<String, XmldbURI>( this.mappedModules );
        ctx.staticNamespaces  = new HashMap<String, String>( this.staticNamespaces );
        ctx.staticPrefixes    = new HashMap<String, String>( this.staticPrefixes );
        
        if (this.dynamicOptions != null){
        	ctx.dynamicOptions = new ArrayList<Option>( this.dynamicOptions );
        }
        
        if (this.staticOptions != null){
        	ctx.staticOptions = new ArrayList<Option>( this.staticOptions );
        }
        
    }


    /**
     * Prepares the current context before xquery execution.
     */
    @Override
    public void prepareForExecution() {
        //if there is an existing user in the current http session
        //then set the DBBroker user
    	final Subject user = getUserFromHttpSession();
        if(user != null) {
            getBroker().setSubject(user);
        }

        //Reset current context position
        setContextSequencePosition( 0, null );
        //Note that, for some reasons, an XQueryContext might be used without calling this method
    }


    public AccessContext getAccessContext()
    {
        return( accessCtx );
    }


    /**
     * Is profiling enabled?
     *
     * @return  true if profiling is enabled for this context.
     */
    public boolean isProfilingEnabled()
    {
        return( profiler.isEnabled() );
    }


    public boolean isProfilingEnabled( int verbosity )
    {
        return( profiler.isEnabled() && ( profiler.verbosity() >= verbosity ) );
    }


    /**
     * Returns the {@link Profiler} instance of this context if profiling is enabled.
     *
     * @return  the profiler instance.
     */
    public Profiler getProfiler()
    {
        return( profiler );
    }


    /**
     * Called from the XQuery compiler to set the root expression for this context.
     *
     * @param  expr
     */
    public void setRootExpression( Expression expr )
    {
        this.rootExpression = expr;
    }


    /**
     * Returns the root expression of the XQuery associated with this context.
     *
     * @return  root expression
     */
    public Expression getRootExpression()
    {
        return( rootExpression );
    }


    /**
     * Returns the next unique expression id. Every expression in the XQuery is identified by a unique id. During compilation, expressions are
     * assigned their id by calling this method.
     *
     * @return  The next unique expression id.
     */
    protected int nextExpressionId()
    {
        return( expressionCounter++ );
    }


    /**
     * Returns the number of expression objects in the internal representation of the query. Used to estimate the size of the query.
     *
     * @return  number of expression objects
     */
    public int getExpressionCount()
    {
        return( expressionCounter );
    }


    @Override
    public void setXacmlSource(final XACMLSource xacmlSource) {
        this.xacmlSource = xacmlSource;
    }


    @Override
    public XACMLSource getXacmlSource() {
        return xacmlSource;
    }

    /**
     * Declare a user-defined static prefix/namespace mapping.
     *
     * <p>eXist internally keeps a table containing all prefix/namespace mappings it found in documents, which have been previously stored into the
     * database. These default mappings need not to be declared explicitely.</p>
     *
     * @param   prefix
     * @param   uri
     *
     * @throws  XPathException  
     */
    public void declareNamespace( String prefix, String uri ) throws XPathException
    {
        if( prefix == null ) {
            prefix = "";
        }

        if( uri == null ) {
            uri = "";
        }

        if( "xml".equals(prefix) || "xmlns".equals(prefix) ) {
            throw( new XPathException( ErrorCodes.XQST0070, "Namespace predefined prefix '" + prefix + "' can not be bound" ) );
        }

        if( uri.equals( Namespaces.XML_NS ) ) {
            throw( new XPathException( ErrorCodes.XQST0070, "Namespace URI '" + uri + "' must be bound to the 'xml' prefix" ) );
        }
        
        final String prevURI = staticNamespaces.get( prefix );

        //This prefix was not bound
        if( prevURI == null ) {
    
            if( uri.length() > 0 ) {
                //Bind it
                staticNamespaces.put( prefix, uri );
                staticPrefixes.put( uri, prefix );
                return;
                
            } else {
                //Nothing to bind

                //TODO : check the specs : unbinding an NS which is not already bound may be disallowed.
                LOG.warn( "Unbinding unbound prefix '" + prefix + "'" );
            }
            
        } else {
            //This prefix was bound

            //Unbind it
            if( uri.length() == 0 ) {

                // if an empty namespace is specified,
                // remove any existing mapping for this namespace
                //TODO : improve, since XML_NS can't be unbound
                staticPrefixes.remove( uri );
                staticNamespaces.remove( prefix );
                return;
            }

            //those prefixes can be rebound to different URIs
            if(    ( "xs".equals(prefix)    && Namespaces.SCHEMA_NS.equals( prevURI ) ) 
                || ( "xsi".equals(prefix)   && Namespaces.SCHEMA_INSTANCE_NS.equals( prevURI ) ) 
                || ( "xdt".equals(prefix)   && Namespaces.XPATH_DATATYPES_NS.equals( prevURI ) ) 
                || ( "fn".equals(prefix)    && Namespaces.XPATH_FUNCTIONS_NS.equals( prevURI ) )
                || ( "math".equals(prefix)) && Namespaces.XPATH_FUNCTIONS_MATH_NS.equals( prevURI )
                || ( "local".equals(prefix) && Namespaces.XQUERY_LOCAL_NS.equals( prevURI ) ) ) {

                staticPrefixes.remove( prevURI );
                staticNamespaces.remove( prefix );

                if( uri.length() > 0 ) {
                    staticNamespaces.put( prefix, uri );
                    staticPrefixes.put( uri, prefix );
                    return;
                    
                } else {
                    //Nothing to bind (not sure if it should raise an error though)

                    //TODO : check the specs : unbinding an NS which is not already bound may be disallowed.
                    LOG.warn( "Unbinding unbound prefix '" + prefix + "'" );
                }
                
            } else {

                //Forbids rebinding the *same* prefix in a *different* namespace in this *same* context
                if( !uri.equals( prevURI ) ) {
                    throw( new XPathException( ErrorCodes.XQST0033, "prefix '"+prefix+"' bind to '"+prevURI+"'" ) );
                }
            }
        }
    }


    public void declareNamespaces( Map<String, String> namespaceMap )
    {
        String prefix;
        String uri;

        for( final Map.Entry<String, String> entry : namespaceMap.entrySet() ) {
            prefix = entry.getKey();
            uri    = entry.getValue();

            if( prefix == null ) {
                prefix = "";
            }

            if( uri == null ) {
                uri = "";
            }
            staticNamespaces.put( prefix, uri );
            staticPrefixes.put( uri, prefix );
        }
    }


    /**
     * Removes the namespace URI from the prefix/namespace mappings table.
     *
     * @param  uri
     */
    public void removeNamespace( String uri )
    {
        staticPrefixes.remove( uri );

        for( final Iterator<String> i = staticNamespaces.values().iterator(); i.hasNext(); ) {

            if( i.next().equals( uri ) ) {
                i.remove();
                return;
            }
        }
        inScopePrefixes.remove( uri );

        if( inScopeNamespaces != null ) {

            for( final Iterator<String> i = inScopeNamespaces.values().iterator(); i.hasNext(); ) {

                if( i.next().equals( uri ) ) {
                    i.remove();
                    return;
                }
            }
        }

        //TODO : is this relevant ?
        inheritedInScopePrefixes.remove( uri );

        if( inheritedInScopeNamespaces != null ) {

            for( final Iterator<String> i = inheritedInScopeNamespaces.values().iterator(); i.hasNext(); ) {

                if( i.next().equals( uri ) ) {
                    i.remove();
                    return;
                }
            }
        }
    }


    /**
     * Declare an in-scope namespace. This is called during query execution.
     *
     * @param  prefix
     * @param  uri
     */
    public void declareInScopeNamespace( String prefix, String uri )
    {
        if( ( prefix == null ) || ( uri == null ) ) {
            throw( new IllegalArgumentException( "null argument passed to declareNamespace" ) );
        }

        //Activate the namespace by removing it from the inherited namespaces
        if( inheritedInScopePrefixes.get( getURIForPrefix( prefix ) ) != null ) {
            inheritedInScopePrefixes.remove( uri );
        }

        if( inheritedInScopeNamespaces.get( prefix ) != null ) {
            inheritedInScopeNamespaces.remove( prefix );
        }
        inScopePrefixes.put( uri, prefix );
        inScopeNamespaces.put( prefix, uri );
    }


    public String getInScopeNamespace( String prefix )
    {
        return( ( inScopeNamespaces == null ) ? null : inScopeNamespaces.get( prefix ) );
    }


    public String getInScopePrefix( String uri )
    {
        return( ( inScopePrefixes == null ) ? null : inScopePrefixes.get( uri ) );
    }

    public Map<String, String> getInScopePrefixes( )
    {
        return( ( inScopePrefixes == null ) ? null : inScopePrefixes );
    }

    public String getInheritedNamespace( String prefix )
    {
        return( ( inheritedInScopeNamespaces == null ) ? null : inheritedInScopeNamespaces.get( prefix ) );
    }


    public String getInheritedPrefix( String uri )
    {
        return( ( inheritedInScopePrefixes == null ) ? null : inheritedInScopePrefixes.get( uri ) );
    }


    /**
     * Return the namespace URI mapped to the registered prefix or null if the prefix is not registered.
     *
     * @param   prefix
     *
     * @return  namespace
     */
    public String getURIForPrefix( String prefix )
    {
        // try in-scope namespace declarations
        String uri = ( inScopeNamespaces == null ) ? null : inScopeNamespaces.get( prefix );

        if( uri != null ) {
            return( uri );
        }

        if( inheritNamespaces ) {
            uri = ( inheritedInScopeNamespaces == null ) ? null : inheritedInScopeNamespaces.get( prefix );

            if( uri != null ) {
                return( uri );
            }
        }
        return( staticNamespaces.get( prefix ) );
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
     * Get URI Prefix
     *
     * @param   uri
     *
     * @return  the prefix mapped to the registered URI or null if the URI is not registered.
     */
    public String getPrefixForURI( String uri )
    {
        String prefix = ( inScopePrefixes == null ) ? null : inScopePrefixes.get( uri );

        if( prefix != null ) {
            return( prefix );
        }

        if( inheritNamespaces ) {
            prefix = ( inheritedInScopePrefixes == null ) ? null : inheritedInScopePrefixes.get( uri );

            if( prefix != null ) {
                return( prefix );
            }
        }
        return( staticPrefixes.get( uri ) );
    }


    /**
     * Clear all user-defined prefix/namespace mappings.
     *
     * @return  
     */
    // TODO: remove since never used?
//  public void clearNamespaces() {
//      staticNamespaces.clear();
//      staticPrefixes.clear();
//      if (inScopeNamespaces != null) {
//          inScopeNamespaces.clear();
//          inScopePrefixes.clear();
//      }
//      //TODO : it this relevant ?
//      if (inheritedInScopeNamespaces != null) {
//          inheritedInScopeNamespaces.clear();
//          inheritedInScopePrefixes.clear();
//      }
//      loadDefaults(broker.getConfiguration());
//  }

    /**
     * Returns the current default function namespace.
     *
     * @return  current default function namespace
     */
    public String getDefaultFunctionNamespace()
    {
        return( defaultFunctionNamespace );
    }


    /**
     * Set the default function namespace. By default, this points to the namespace for XPath built-in functions.
     *
     * @param   uri
     *
     * @throws  XPathException  
     */
    public void setDefaultFunctionNamespace( String uri ) throws XPathException
    {
        //Not sure for the 2nd clause : eXist forces the function NS as default.
        if( ( defaultFunctionNamespace != null ) && !defaultFunctionNamespace.equals( Function.BUILTIN_FUNCTION_NS ) && !defaultFunctionNamespace.equals( uri ) ) {
            throw( new XPathException( "err:XQST0066: default function namespace is already set to: '" + defaultFunctionNamespace + "'" ) );
        }
        defaultFunctionNamespace = uri;
    }


    /**
     * Returns the current default element namespace.
     *
     * @return  current default element namespace schema
     *
     * @throws  XPathException  
     */
    public String getDefaultElementNamespaceSchema() throws XPathException
    {
        return( defaultElementNamespaceSchema.getStringValue() );
    }


    /**
     * Set the default element namespace. By default, this points to the empty uri.
     *
     * @param   uri
     *
     * @throws  XPathException  
     */
    public void setDefaultElementNamespaceSchema( String uri ) throws XPathException
    {
        // eXist forces the empty element NS as default.
        if( !defaultElementNamespaceSchema.equals( AnyURIValue.EMPTY_URI ) ) {
            throw( new XPathException( "err:XQST0066: default function namespace schema is already set to: '" + defaultElementNamespaceSchema.getStringValue() + "'" ) );
        }
        defaultElementNamespaceSchema = new AnyURIValue( uri );
    }


    /**
     * Returns the current default element namespace.
     *
     * @return  current default element namespace
     *
     * @throws  XPathException  
     */
    public String getDefaultElementNamespace() throws XPathException
    {
        return( defaultElementNamespace.getStringValue() );
    }


    /**
     * Set the default element namespace. By default, this points to the empty uri.
     *
     * @param      uri     a <code>String</code> value
     * @param      schema  a <code>String</code> value
     *
     * @exception  XPathException  if an error occurs
     */
    public void setDefaultElementNamespace( String uri, String schema ) throws XPathException
    {
        // eXist forces the empty element NS as default.
        if( !defaultElementNamespace.equals( AnyURIValue.EMPTY_URI ) ) {
            throw( new XPathException( "err:XQST0066: default element namespace is already set to: '" + defaultElementNamespace.getStringValue() + "'" ) );
        }
        defaultElementNamespace = new AnyURIValue( uri );

        if( schema != null ) {
            defaultElementNamespaceSchema = new AnyURIValue( schema );
        }
    }


    /**
     * Set the default collation to be used by all operators and functions on strings. Throws an exception if the collation is unknown or cannot be
     * instantiated.
     *
     * @param   uri
     *
     * @throws  XPathException
     */
    public void setDefaultCollation( String uri ) throws XPathException
    {
        if( uri.equals( Collations.CODEPOINT ) || uri.equals( Collations.CODEPOINT_SHORT ) ) {
            defaultCollation = Collations.CODEPOINT;
            defaultCollator  = null;
        }

        URI uriTest;

        try {
            uriTest = new URI( uri );
        }
        catch( final URISyntaxException e ) {
            throw( new XPathException( "err:XQST0038: Unknown collation : '" + uri + "'" ) );
        }

        if( uri.startsWith( Collations.EXIST_COLLATION_URI ) || uri.startsWith( "?" ) || uriTest.isAbsolute() ) {
            defaultCollator  = Collations.getCollationFromURI( this, uri );
            defaultCollation = uri;
        } else {
            String absUri = getBaseURI().getStringValue() + uri;
            defaultCollator  = Collations.getCollationFromURI( this, absUri );
            defaultCollation = absUri;
        }
    }


    public String getDefaultCollation()
    {
        return( defaultCollation );
    }


    public Collator getCollator( String uri ) throws XPathException
    {
        if( uri == null ) {
            return( defaultCollator );
        }
        return( Collations.getCollationFromURI( this, uri ) );
    }


    public Collator getDefaultCollator()
    {
        return( defaultCollator );
    }


    /**
     * Set the set of statically known documents for the current execution context. These documents will be processed if no explicit document set has
     * been set for the current expression with fn:doc() or fn:collection().
     *
     * @param  docs
     */
    public void setStaticallyKnownDocuments( XmldbURI[] docs )
    {
        staticDocumentPaths = docs;
    }


    public void setStaticallyKnownDocuments( DocumentSet set )
    {
        staticDocuments = set;
    }


    //TODO : not sure how these 2 options might/have to be related
    public void setCalendar( XMLGregorianCalendar newCalendar )
    {
        this.calendar = (XMLGregorianCalendar)newCalendar.clone();
    }


    public void setTimeZone( TimeZone newTimeZone )
    {
        this.implicitTimeZone = newTimeZone;
    }


    public XMLGregorianCalendar getCalendar()
    {
        //TODO : we might prefer to return null
        if( calendar == null ) {

            try {

                //Initialize to current dateTime
                calendar = DatatypeFactory.newInstance().newXMLGregorianCalendar( new GregorianCalendar() );
            }
            catch( final DatatypeConfigurationException e ) {
                LOG.error( e.getMessage(), e );
            }
        }

        //That's how we ensure stability of that static context function
        return( calendar );
    }


    public TimeZone getImplicitTimeZone()
    {
        if( implicitTimeZone == null ) {
            implicitTimeZone = TimeZone.getDefault();

            if( implicitTimeZone.inDaylightTime( new Date() ) ) {
                implicitTimeZone.setRawOffset( implicitTimeZone.getRawOffset() + implicitTimeZone.getDSTSavings() );
            }
        }

        //That's how we ensure stability of that static context function
        return( this.implicitTimeZone );
    }


    /**
     * Get statically known documents
     *
     * @return  set of statically known documents.
     *
     * @throws  XPathException  
     */
    public DocumentSet getStaticallyKnownDocuments() throws XPathException
    {
        if( staticDocuments != null ) {

            // the document set has already been built, return it
            return( staticDocuments );
        }

        if( protectedDocuments != null ) {
            staticDocuments = protectedDocuments.toDocumentSet();
            return( staticDocuments );
        }
        MutableDocumentSet ndocs = new DefaultDocumentSet( 1031 );

        if( staticDocumentPaths == null ) {

            // no path defined: return all documents in the db
            try {
                getBroker().getAllXMLResources( ndocs );
            } catch(final PermissionDeniedException pde) {
                LOG.warn("Permission denied to read resource all resources" + pde.getMessage(), pde);
                throw new XPathException("Permission denied to read resource all resources" + pde.getMessage(), pde);
            }
        } else {
            DocumentImpl doc;
            Collection   collection;

            for( int i = 0; i < staticDocumentPaths.length; i++ ) {

                try {
                    collection = getBroker().getCollection( staticDocumentPaths[i] );

                    if( collection != null ) {
                        collection.allDocs( getBroker(), ndocs, true);
                    } else {
                        doc = getBroker().getXMLResource( staticDocumentPaths[i], Lock.READ_LOCK );

                        if( doc != null ) {

                            if( doc.getPermissions().validate( 
                            		getBroker().getSubject(), Permission.READ ) ) {
                                
                            	ndocs.add( doc );
                            }
                            doc.getUpdateLock().release( Lock.READ_LOCK );
                        }
                    }
                }
                catch( final PermissionDeniedException e ) {
                    LOG.warn( "Permission denied to read resource " + staticDocumentPaths[i] + ". Skipping it." );
                }
            }
        }
        staticDocuments = ndocs;
        return( staticDocuments );
    }


    public ExtendedXMLStreamReader getXMLStreamReader( NodeValue nv ) throws XMLStreamException, IOException
    {
        ExtendedXMLStreamReader reader;

        if( nv.getImplementationType() == NodeValue.IN_MEMORY_NODE ) {
            final NodeImpl node = (NodeImpl)nv;
            reader = new InMemoryXMLStreamReader( node.getDocument(), node.getDocument() );
        } else {
            final NodeProxy proxy = (NodeProxy)nv;
            reader = getBroker().newXMLStreamReader( new NodeProxy( proxy.getDocument(), NodeId.DOCUMENT_NODE, proxy.getDocument().getFirstChildAddress() ), false );
        }
        return( reader );
    }


    public void setProtectedDocs( LockedDocumentMap map )
    {
        this.protectedDocuments = map;
    }


    public LockedDocumentMap getProtectedDocs()
    {
        return( this.protectedDocuments );
    }


    public boolean inProtectedMode()
    {
        return( protectedDocuments != null );
    }


    /**
     * Should loaded documents be locked?
     *
     * <p>see #setLockDocumentsOnLoad(boolean)</p>
     */
    public boolean lockDocumentsOnLoad()
    {
        return( false );
    }


//  /**
//   * If lock is true, all documents loaded during query execution
//   * will be locked. This way, we avoid that query results become
//   * invalid before the entire result has been processed by the client
//   * code. All attempts to modify nodes which are part of the result
//   * set will be blocked.
//   *
//   * However, it is the client's responsibility to proper unlock
//   * all documents once processing is completed.
//   *
//   * @param lock
//   */
//  public void setLockDocumentsOnLoad(boolean lock) {
//      lockDocumentsOnLoad = lock;
//      if(lock)
//          lockedDocuments = new LockedDocumentMap();
//  }


    public void addLockedDocument( DocumentImpl doc )
    {
//        if (lockedDocuments != null)
//           lockedDocuments.add(doc);
    }


//    /**
//     * Release all locks on documents that have been locked
//     * during query execution.
//     *
//     *@see #setLockDocumentsOnLoad(boolean)
//     */
//  public void releaseLockedDocuments() {
//        if(lockedDocuments != null)
//          lockedDocuments.unlock();
//      lockDocumentsOnLoad = false;
//      lockedDocuments = null;
//  }

//    /**
//     * Release all locks on documents not being referenced by the sequence.
//     * This is called after query execution has completed. Only locks on those
//     * documents contained in the final result set will be preserved. All other
//     * locks are released as they are no longer needed.
//     *
//     * @param seq
//     * @throws XPathException
//     */
//  public LockedDocumentMap releaseUnusedDocuments(Sequence seq) throws XPathException {
//      if(lockedDocuments == null)
//          return null;
//        // determine the set of documents referenced by nodes in the sequence
//        DocumentSet usedDocs = new DocumentSet();
//        for(SequenceIterator i = seq.iterate(); i.hasNext(); ) {
//            Item next = i.nextItem();
//            if(Type.subTypeOf(next.getType(), Type.NODE)) {
//                NodeValue node = (NodeValue) next;
//                if(node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
//                    DocumentImpl doc = ((NodeProxy)node).getDocument();
//                    if(!usedDocs.contains(doc.getDocId()))
//                      usedDocs.add(doc, false);
//                }
//            }
//        }
//        LockedDocumentMap remaining = lockedDocuments.unlockSome(usedDocs);
//        lockDocumentsOnLoad = false;
//      lockedDocuments = null;
//        return remaining;
//    }

    public void setShared( boolean shared )
    {
        isShared = shared;
    }


    public boolean isShared()
    {
        return( isShared );
    }


    public void addModifiedDoc( DocumentImpl document )
    {
        if( modifiedDocuments == null ) {
            modifiedDocuments = new DefaultDocumentSet();
        }
        modifiedDocuments.add( document );
    }


    public void reset()
    {
        reset( false );
    }


    /**
     * Prepare this XQueryContext to be reused. This should be called when adding an XQuery to the cache.
     *
     * @param  keepGlobals  
     */
    public void reset( boolean keepGlobals )
    {
        if( modifiedDocuments != null ) {

            try {
                Modification.checkFragmentation( this, modifiedDocuments );
            }
            catch( final EXistException e ) {
                LOG.warn( "Error while checking modified documents: " + e.getMessage(), e );
            }
            modifiedDocuments = null;
        }
        calendar         = null;
        implicitTimeZone = null;
        
        resetDocumentBuilder();

        contextSequence = null;

        if( !keepGlobals ) {

            // do not reset the statically known documents
            staticDocumentPaths = null;
            staticDocuments     = null;
        }

        if( !isShared ) {
            lastVar = null;
        }
        fragmentStack = new Stack<MemTreeBuilder>();
        callStack.clear();
        protectedDocuments = null;

        if( !keepGlobals ) {
            globalVariables.clear();
        }

        if( dynamicOptions != null ) {
            dynamicOptions.clear(); //clear any dynamic options
        }

        if( !isShared ) {
            watchdog.reset();
        }

        for( final Module module : modules.values() ) {
            if (module instanceof ExternalModule && ((ModuleContext)((ExternalModule)module).getContext()).getParentContext() != this) {
                continue;
            }
            module.reset( this );
        }

        if( !keepGlobals ) {
            mappedModules.clear();
        }

        savedState.restore();
        
        //remove the context-vars, subsequent execution of the query
        //may generate different values for the vars based on the
        //content of the db
        XQueryContextVars.clear();
        
        attributes.clear();

        clearUpdateListeners();

        profiler.reset();
        
        analyzed = false;
    }

    /**
     * Returns true if whitespace between constructed element nodes should be stripped by default.
     */
    public boolean stripWhitespace()
    {
        return( stripWhitespace );
    }


    public void setStripWhitespace( boolean strip )
    {
        this.stripWhitespace = strip;
    }


    /**
     * Returns true if namespaces for constructed element and document nodes should be preserved on copy by default.
     */
    public boolean preserveNamespaces()
    {
        return( preserveNamespaces );
    }


    /**
     * The method <code>setPreserveNamespaces.</code>
     *
     * @param  preserve  a <code>boolean</code> value
     */
    public void setPreserveNamespaces( final boolean preserve )
    {
        this.preserveNamespaces = preserve;
    }


    /**
     * Returns true if namespaces for constructed element and document nodes
     * should be inherited on copy by default.
     */
    public boolean inheritNamespaces()
    {
        return( inheritNamespaces );
    }


    /**
     * The method <code>setInheritNamespaces.</code>
     *
     * @param  inherit  a <code>boolean</code> value
     */
    public void setInheritNamespaces( final boolean inherit )
    {
        this.inheritNamespaces = inherit;
    }


    /**
     * Returns true if order empty is set to greatest, otherwise false for order empty is least.
     */
    public boolean orderEmptyGreatest()
    {
        return( orderEmptyGreatest );
    }


    /**
     * The method <code>setOrderEmptyGreatest.</code>
     *
     * @param  order  a <code>boolean</code> value
     */
    public void setOrderEmptyGreatest( final boolean order )
    {
        this.orderEmptyGreatest = order;
    }


    /**
     * Get modules
     *
     * @return  iterator over all modules imported into this context
     */
    public Iterator<Module> getModules()
    {
        return( modules.values().iterator() );
    }


    /**
     * Get root modules
     *
     * @return  iterator over all modules registered in the entire context tree
     */
    public Iterator<Module> getRootModules()
    {
        return( getAllModules() );
    }


    public Iterator<Module> getAllModules()
    {
        return( allModules.values().iterator() );
    }


    /**
     * Get the built-in module registered for the given namespace URI.
     *
     * @param   namespaceURI
     *
     * @return  built-in module
     */
    public Module getModule( String namespaceURI )
    {
        return( modules.get( namespaceURI ) );
    }


    public Module getRootModule( String namespaceURI )
    {
        return( allModules.get( namespaceURI ) );
    }


    public void setModule( String namespaceURI, Module module )
    {
        if( module == null ) {
            modules.remove( namespaceURI ); // unbind the module
        } else {
            modules.put( namespaceURI, module );
        }
        setRootModule( namespaceURI, module );
    }


    protected void setRootModule( String namespaceURI, Module module )
    {
        if( module == null ) {
            allModules.remove( namespaceURI ); // unbind the module
            return;
        }

        if( allModules.get( namespaceURI ) != module ) {
            setModulesChanged();
        }
        allModules.put( namespaceURI, module );
    }


    void setModulesChanged()
    {
        this.modulesChanged = true;
    }


    /**
     * For compiled expressions: check if the source of any module imported by the current
     * query has changed since compilation.
     */
    public boolean checkModulesValid()
    {
    	for (final Module module : allModules.values() ) {
    		if( !module.isInternalModule() ) {
    			if( !( (ExternalModule)module ).moduleIsValid( getBroker() ) ) {
                    LOG.debug( "Module with URI " + module.getNamespaceURI() + " has changed and needs to be reloaded" );
                    return( false );
                }
    		}
    	}
        return( true );
    }


    public void analyzeAndOptimizeIfModulesChanged( Expression expr ) throws XPathException
    {
    	if (analyzed)
    		{return;}
    	analyzed = true;
    	for (final Module module : expr.getContext().modules.values()) {
            if( !module.isInternalModule() ) {
            	final Expression root = ((ExternalModule)module).getRootExpression();
            	((ExternalModule)module).getContext().analyzeAndOptimizeIfModulesChanged(root);
            }
    	}
        expr.analyze( new AnalyzeContextInfo() );

        if( optimizationsEnabled() ) {
            final Optimizer optimizer = new Optimizer( this );
            expr.accept( optimizer );

            if( optimizer.hasOptimized() ) {
                reset( true );
                expr.resetState( true );
                expr.analyze( new AnalyzeContextInfo() );
            }
        }
        modulesChanged = false;
    }


    /**
     * Load a built-in module from the given class name and assign it to the namespace URI. The specified class should be a subclass of {@link
     * Module}. The method will try to instantiate the class. If the class is not found or an exception is thrown, the method will silently fail. The
     * namespace URI has to be equal to the namespace URI declared by the module class. Otherwise, the module is not loaded.
     *
     * @param   namespaceURI
     * @param   moduleClass
     *
     * @return   Module
     */
    public Module loadBuiltInModule( String namespaceURI, String moduleClass )
    {
        Module module = null;
        if (namespaceURI != null)
            {module = getModule( namespaceURI );}

        if( module != null ) {
//          LOG.debug("module " + namespaceURI + " is already present");
            return( module );
        }
        return( initBuiltInModule( namespaceURI, moduleClass ) );
    }


    @SuppressWarnings( "unchecked" )
    protected Module initBuiltInModule( String namespaceURI, String moduleClass )
    {
        Module module = null;

        try {

            // lookup the class
            final Class<?> mClass = Class.forName( moduleClass );

            if( !( Module.class.isAssignableFrom( mClass ) ) ) {
                LOG.info( "failed to load module. " + moduleClass + " is not an instance of org.exist.xquery.Module." );
                return( null );
            }
            //instantiateModule( namespaceURI, (Class<Module>)mClass );
            // INOTE: expathrepo
             module = instantiateModule( namespaceURI, (Class<Module>)mClass, (Map<String, Map<String, List<? extends Object>>>) getBroker().getConfiguration().getProperty(PROPERTY_MODULE_PARAMETERS));
            //LOG.debug("module " + module.getNamespaceURI() + " loaded successfully.");
        }
        catch( final ClassNotFoundException e ) {
            LOG.warn( "module class " + moduleClass + " not found. Skipping..." );
        }
        return( module );
    }


    protected Module instantiateModule( String namespaceURI, Class<Module> mClass, Map<String, Map<String, List<? extends Object>>> moduleParameters) {
        Module module = null;

        try {

            final Constructor<Module> cnstr = mClass.getConstructor(Map.class);
            
            module = cnstr.newInstance(moduleParameters.get(namespaceURI));

            if(namespaceURI != null && !module.getNamespaceURI().equals(namespaceURI)) {
                LOG.warn( "the module declares a different namespace URI. Expected: " + namespaceURI + " found: " + module.getNamespaceURI() );
                return( null );
            }

            if((getPrefixForURI( module.getNamespaceURI() ) == null) && (module.getDefaultPrefix().length() > 0)) {
                declareNamespace( module.getDefaultPrefix(), module.getNamespaceURI() );
            }

            modules.put(module.getNamespaceURI(), module);
            allModules.put(module.getNamespaceURI(), module);
        } catch(final InstantiationException ie) {
            LOG.warn("error while instantiating module class " + mClass.getName(), ie);
        } catch(final IllegalAccessException iae) {
            LOG.warn("error while instantiating module class " + mClass.getName(), iae);
        } catch(final XPathException xpe) {
            LOG.warn("error while instantiating module class " + mClass.getName(), xpe);
        } catch(final NoSuchMethodException nsme) {
            LOG.warn("error while instantiating module class " + mClass.getName(), nsme);
        } catch(final InvocationTargetException ite) {
            LOG.warn("error while instantiating module class " + mClass.getName(), ite);
        }
        
        return module;
    }


    /**
     * Convenience method that returns the XACML Policy Decision Point for this database instance. If XACML has not been enabled, this returns null.
     *
     * @return  the PDP for this database instance, or null if XACML is disabled
     */
    public ExistPDP getPDP() {
    	return db.getSecurityManager().getPDP();
    }


    /**
     * Declare a user-defined function. All user-defined functions are kept in a single hash map.
     *
     * @param   function
     *
     * @throws  XPathException
     */
    public void declareFunction( UserDefinedFunction function ) throws XPathException
    {
        // TODO: redeclaring functions should be forbidden. however, throwing an
        // exception will currently break util:eval.
    	
    	final QName name = function.getSignature().getName();
    	
        if(Namespaces.XML_NS.equals(name.getNamespaceURI())) {
            throw new XPathException(function, ErrorCodes.XQST0045, "Function '" + name + "' is in the forbidden namespace '" + Namespaces.XML_NS + "'" );
        }

        if(Namespaces.SCHEMA_NS.equals(name.getNamespaceURI())) {
            throw new XPathException(function, ErrorCodes.XQST0045, "Function '" + name + "' is in the forbidden namespace '" + Namespaces.SCHEMA_NS + "'");
        }

        if(Namespaces.SCHEMA_INSTANCE_NS.equals(name.getNamespaceURI())) {
            throw new XPathException(function, ErrorCodes.XQST0045, "Function '" + name + "' is in the forbidden namespace '" + Namespaces.SCHEMA_INSTANCE_NS + "'");
        }

        if(Namespaces.XPATH_FUNCTIONS_NS.equals(name.getNamespaceURI())) {
            throw new XPathException(function, ErrorCodes.XQST0045, "Function '" + name + "' is in the forbidden namespace '" + Namespaces.XPATH_FUNCTIONS_NS + "'");
        }

        if("".equals( name.getNamespaceURI())) {
            throw new XPathException(function, ErrorCodes.XQST0060, "Every declared function name must have a non-null namespace URI, but function '" + name + "' does not meet this requirement.");
        }

        declaredFunctions.put( function.getSignature().getFunctionId(), function );
//      if (declaredFunctions.get(function.getSignature().getFunctionId()) == null)
//              declaredFunctions.put(function.getSignature().getFunctionId(), function);
//      else
//          throw new XPathException("XQST0034: function " + function.getName() + " is already defined with the same arity");
    }


    /**
     * Resolve a user-defined function.
     *
     * @param   name
     * @param   argCount  
     *
     * @return  user-defined function
     *
     * @throws  XPathException
     */
    public UserDefinedFunction resolveFunction( QName name, int argCount ) throws XPathException
    {
        final FunctionId          id   = new FunctionId( name, argCount );
        final UserDefinedFunction func = declaredFunctions.get( id );
        return( func );
    }


    public Iterator<FunctionSignature> getSignaturesForFunction( QName name )
    {
        final ArrayList<FunctionSignature> signatures = new ArrayList<FunctionSignature>( 2 );

        for( final UserDefinedFunction func : declaredFunctions.values() ) {

            if( func.getName().equals( name ) ) {
                signatures.add( func.getSignature() );
            }
        }
        return( signatures.iterator() );
    }


    public Iterator<UserDefinedFunction> localFunctions()
    {
        return( declaredFunctions.values().iterator() );
    }


    /**
     * Declare a local variable. This is called by variable binding expressions like "let" and "for".
     *
     * @param   var
     *
     * @return   LocalVariable
     *
     * @throws  XPathException
     */
    public LocalVariable declareVariableBinding( LocalVariable var ) throws XPathException
    {
        if( lastVar == null ) {
            lastVar = var;
        } else {
            lastVar.addAfter( var );
            lastVar = var;
        }
        var.setStackPosition( getCurrentStackSize() );
        return( var );
    }


    /**
     * Declare a global variable as by "declare variable".
     *
     * @param   var
     *
     * @return  Variable
     *
     * @throws  XPathException
     */
    public Variable declareGlobalVariable( Variable var ) throws XPathException
    {
        globalVariables.put( var.getQName(), var );
        var.setStackPosition( getCurrentStackSize() );
        return( var );
    }

    public void undeclareGlobalVariable( QName name ) {
        globalVariables.remove(name);
    }

    /**
     * Declare a user-defined variable.
     *
     * <p>The value argument is converted into an XPath value (@see XPathUtil#javaObjectToXPath(Object)).</p>
     *
     * @param   qname  the qualified name of the new variable. Any namespaces should have been declared before.
     * @param   value  a Java object, representing the fixed value of the variable
     *
     * @return  the created Variable object
     *
     * @throws  XPathException  if the value cannot be converted into a known XPath value or the variable QName references an unknown
     *                          namespace-prefix.
     */
    public Variable declareVariable( String qname, Object value ) throws XPathException
    {
        return( declareVariable( QName.parse( this, qname, null ), value ) );
    }


    public Variable declareVariable( QName qn, Object value ) throws XPathException
    {
        Variable var;
        final Module   module = getModule( qn.getNamespaceURI() );

        if( module != null ) {
            var = module.declareVariable( qn, value );
            return( var );
        }
        final Sequence val = XPathUtil.javaObjectToXPath( value, this );
        var = globalVariables.get( qn );

        if( var == null ) {
            var = new VariableImpl( qn );
            globalVariables.put( qn, var );
        }

        if( var.getSequenceType() != null ) {
            int actualCardinality;

            if( val.isEmpty() ) {
                actualCardinality = Cardinality.EMPTY;
            } else if( val.hasMany() ) {
                actualCardinality = Cardinality.MANY;
            } else {
                actualCardinality = Cardinality.ONE;
            }

            //Type.EMPTY is *not* a subtype of other types ; checking cardinality first
            if( !Cardinality.checkCardinality( var.getSequenceType().getCardinality(), actualCardinality ) ) {
                throw( new XPathException( "XPTY0004: Invalid cardinality for variable $" + var.getQName() + ". Expected " + Cardinality.getDescription( var.getSequenceType().getCardinality() ) + ", got " + Cardinality.getDescription( actualCardinality ) ) );
            }

            //TODO : ignore nodes right now ; they are returned as xs:untypedAtomicType
            if( !Type.subTypeOf( var.getSequenceType().getPrimaryType(), Type.NODE ) ) {

                if( !val.isEmpty() && !Type.subTypeOf( val.getItemType(), var.getSequenceType().getPrimaryType() ) ) {
                    throw( new XPathException( "XPTY0004: Invalid type for variable $" + var.getQName() + ". Expected " + Type.getTypeName( var.getSequenceType().getPrimaryType() ) + ", got " + Type.getTypeName( val.getItemType() ) ) );
                }

                //Here is an attempt to process the nodes correctly
            } else {

                //Same as above : we probably may factorize
                if( !val.isEmpty() && !Type.subTypeOf( val.getItemType(), var.getSequenceType().getPrimaryType() ) ) {
                    throw( new XPathException( "XPTY0004: Invalid type for variable $" + var.getQName() + ". Expected " + Type.getTypeName( var.getSequenceType().getPrimaryType() ) + ", got " + Type.getTypeName( val.getItemType() ) ) );
                }

            }
        }

        //TODO : should we allow global variable *re*declaration ?
        var.setValue( val );
        return( var );
    }


    /**
     * Try to resolve a variable.
     *
     * @param   name  the qualified name of the variable as string
     *
     * @return  the declared Variable object
     *
     * @throws  XPathException  if the variable is unknown
     */
    public Variable resolveVariable( String name ) throws XPathException
    {
        final QName qn = QName.parse( this, name, null );
        return( resolveVariable( qn ) );
    }


    /**
     * Try to resolve a variable.
     *
     * @param   qname  the qualified name of the variable
     *
     * @return  the declared Variable object
     *
     * @throws  XPathException  if the variable is unknown
     */
    public Variable resolveVariable( QName qname ) throws XPathException
    {
        Variable var;

        // check if the variable is declared local
        var = resolveLocalVariable( qname );

        // check if the variable is declared in a module
        if( var == null ) {
            final Module module = getModule( qname.getNamespaceURI() );

            if( module != null ) {
                var = module.resolveVariable( qname );
            }
        }

        // check if the variable is declared global
        if( var == null ) {
            var = (Variable)globalVariables.get( qname );
        }

        //if (var == null)
        //  throw new XPathException("variable $" + qname + " is not bound");
        return( var );
    }


    protected Variable resolveLocalVariable( QName qname ) throws XPathException
    {
        final LocalVariable end = contextStack.isEmpty() ? null : contextStack.peek();

        for( LocalVariable var = lastVar; var != null; var = var.before ) {

            if( var == end ) {
                return( null );
            }

            if( qname.equals( var.getQName() ) ) {
                return( var );
            }
        }
        return( null );
    }


    public boolean isVarDeclared( QName qname )
    {
        final Module module = getModule( qname.getNamespaceURI() );

        if( module != null ) {

            if( module.isVarDeclared( qname ) ) {
                return( true );
            }
        }
        return( globalVariables.get( qname ) != null );
    }


    public Map<QName, Variable> getVariables()
    {
        final Map<QName, Variable> variables = new HashMap<QName, Variable>();

        variables.putAll( globalVariables );

        final LocalVariable end = contextStack.isEmpty() ? null : (LocalVariable)contextStack.peek();

        for( LocalVariable var = lastVar; var != null; var = var.before ) {

            if( var == end ) {
                break;
            }

            variables.put( var.getQName(), var );
        }

        return( variables );
    }

    public Map<QName, Variable> getLocalVariables() {
        final Map<QName, Variable> variables = new HashMap<QName, Variable>();

        final LocalVariable end = contextStack.isEmpty() ? null : (LocalVariable)contextStack.peek();

        for ( LocalVariable var = lastVar; var != null; var = var.before ) {

            if ( var == end ) {
                break;
            }

            variables.put( var.getQName(), var );
        }

        return ( variables );
    }

    /**
     * Return a copy of all currently visible local variables.
     * Used by {@link InlineFunction} to implement closures.
     * 
     * @return currently visible local variables as a stack
     */
    public List<Variable> getLocalStack() {
    	final List<Variable> variables = new ArrayList<Variable>(10);
    	
    	final LocalVariable end = contextStack.isEmpty() ? null : contextStack.peek();

        for ( LocalVariable var = lastVar; var != null; var = var.before ) {

            if ( var == end ) {
                break;
            }

            variables.add( new LocalVariable(var, true) );
        }

        return ( variables );
    }
    
    public Map<QName, Variable> getGlobalVariables() {
        final Map<QName, Variable> variables = new HashMap<QName, Variable>();

        variables.putAll( globalVariables );

        return( variables );
    }
    
    /**
     * Restore a saved stack of local variables. Used to implement closures.
     * 
     * @param stack
     * @throws XPathException
     */
    public void restoreStack(List<Variable> stack) throws XPathException {
    	for (final Variable var : stack) {
    		declareVariableBinding((LocalVariable) var);
    	}
    }
    
    /**
     * Turn on/off XPath 1.0 backwards compatibility.
     *
     * <p>If turned on, comparison expressions will behave like in XPath 1.0, i.e. if any one of the operands is a number, the other operand will be
     * cast to a double.</p>
     *
     * @param  backwardsCompatible
     */
    public void setBackwardsCompatibility( boolean backwardsCompatible )
    {
        this.backwardsCompatible = backwardsCompatible;
    }


    /**
     * XPath 1.0 backwards compatibility turned on?
     *
     * <p>In XPath 1.0 compatible mode, additional conversions will be applied to values if a numeric value is expected.</p>
     */
    public boolean isBackwardsCompatible()
    {
        return( this.backwardsCompatible );
    }


    public boolean isRaiseErrorOnFailedRetrieval()
    {
        return( raiseErrorOnFailedRetrieval );
    }


    public Database getDatabase() {
    	return db;
    }

    /**
     * Get the DBBroker instance used for the current query.
     *
     * <p>The DBBroker is the main database access object, providing access to all internal database functions.</p>
     *
     * @return  DBBroker instance
     */
    public DBBroker getBroker() {
    	return db.getActiveBroker();
    }

    /**
     * Get the user which executes the current query.
     *
     * @return  user
     * @deprecated use getSubject
     */
    public Subject getUser() {
        return getSubject();
    }

    /**
     * Get the subject which executes the current query.
     *
     * @return  subject
     */
    public Subject getSubject() {
        return getBroker().getSubject();
    }

    
    /**
     * If there is a HTTP Session, and a User has been stored in the session then this will return the user object from the session.
     *
     * @return  The user or null if there is no session or no user
     */
    public Subject getUserFromHttpSession()
    {
        final RequestModule myModule = (RequestModule)getModule( RequestModule.NAMESPACE_URI );

        //Sanity check : one may *not* want to bind the module !
        if( myModule == null ) {
            return( null );
        }

        Variable var = null;

        try {
            var = myModule.resolveVariable( RequestModule.REQUEST_VAR );
        }
        catch( final XPathException xpe ) {
            return( null );
        }

        if( ( var != null ) && ( var.getValue() != null ) ) {

            if( var.getValue().getItemType() == Type.JAVA_OBJECT ) {
                final JavaObjectValue reqValue = (JavaObjectValue)var.getValue().itemAt( 0 );

                if( reqValue.getObject() instanceof RequestWrapper) {
                    final RequestWrapper req = (RequestWrapper) reqValue.getObject();
                    final Object user = req.getAttribute(HTTP_REQ_ATTR_USER);
                    final Object passAttr = req.getAttribute(HTTP_REQ_ATTR_PASS);
                    if (user != null) {
                        final String password = passAttr == null ? null : passAttr.toString();
                        try {
                            return getBroker().getBrokerPool().getSecurityManager().authenticate(user.toString(), password);
                        } catch (final AuthenticationException e) {
                            LOG.error("User can not be authenticated: " + user.toString());
                        }
                    } else {
                        if (req.getSession() != null) {
                            return (Subject) req.getSession().getAttribute(HTTP_SESSIONVAR_XMLDB_USER);
                        }
                    }
                }
            }
        }

        return( null );
    }

    /** The builder used for creating in-memory document fragments. */
    private MemTreeBuilder documentBuilder = null;
    
    /**
     * Get the document builder currently used for creating temporary document fragments. A new document builder will be created on demand.
     *
     * @return  document builder
     */
    @Override
    public MemTreeBuilder getDocumentBuilder() {
        if(documentBuilder == null) {
            documentBuilder = new MemTreeBuilder(this);
            documentBuilder.startDocument();
        }
        return documentBuilder;
    }

    @Override
    public MemTreeBuilder getDocumentBuilder(boolean explicitCreation) {
        if(documentBuilder == null) {
            documentBuilder = new MemTreeBuilder(this);
            documentBuilder.startDocument(explicitCreation);
        }
        return documentBuilder;
    }
    
    private void resetDocumentBuilder() {
        setDocumentBuilder(null);
    }
    
    private void setDocumentBuilder(MemTreeBuilder documentBuilder) {
        this.documentBuilder = documentBuilder;
    }
    
    


    /**
     * Returns the shared name pool used by all in-memory documents which are created within this query context. Create a name pool for every document
     * would be a waste of memory, especially since it is likely that the documents contain elements or attributes with similar names.
     *
     * @return  the shared name pool
     */
    public NamePool getSharedNamePool()
    {
        if( sharedNamePool == null ) {
            sharedNamePool = new NamePool();
        }
        return( sharedNamePool );
    }


    /* DebuggeeJoint methods */

    public XQueryContext getContext()
    {
        return( null );
    }

    public void prologEnter(Expression expr) {
        if (debuggeeJoint != null) {
            debuggeeJoint.prologEnter(expr);
        }
    }

    public void expressionStart( Expression expr ) throws TerminatedException
    {
        if( debuggeeJoint != null ) {
            debuggeeJoint.expressionStart( expr );
        }
    }


    public void expressionEnd( Expression expr )
    {
        if( debuggeeJoint != null ) {
            debuggeeJoint.expressionEnd( expr );
        }
    }


    public void stackEnter( Expression expr ) throws TerminatedException
    {
        if( debuggeeJoint != null ) {
            debuggeeJoint.stackEnter( expr );
        }
    }


    public void stackLeave( Expression expr )
    {
        if( debuggeeJoint != null ) {
            debuggeeJoint.stackLeave( expr );
        }
    }


    /* Methods delegated to the watchdog */

    public void proceed() throws TerminatedException
    {
        getWatchDog().proceed( null );
    }


    public void proceed( Expression expr ) throws TerminatedException
    {
        getWatchDog().proceed( expr );
    }


    public void proceed( Expression expr, MemTreeBuilder builder ) throws TerminatedException
    {
        getWatchDog().proceed( expr, builder );
    }


    public void setWatchDog( XQueryWatchDog watchdog )
    {
        this.watchdog = watchdog;
    }


    public XQueryWatchDog getWatchDog()
    {
        return( watchdog );
    }


    /**
     * Push any document fragment created within the current execution context on the stack.
     */
    public void pushDocumentContext()
    {
        fragmentStack.push(getDocumentBuilder());
        resetDocumentBuilder();
    }


    public void popDocumentContext()
    {
        if( !fragmentStack.isEmpty() ) {
            setDocumentBuilder(fragmentStack.pop());
        }
    }


    /**
     * Set the base URI for the evaluation context.
     *
     * <p>This is the URI returned by the fn:base-uri() function.</p>
     *
     * @param  uri
     */
    public void setBaseURI( AnyURIValue uri )
    {
        setBaseURI( uri, false );
    }


    /**
     * Set the base URI for the evaluation context.
     *
     * <p>A base URI specified via the base-uri directive in the XQuery prolog overwrites any other setting.</p>
     *
     * @param  uri
     * @param  setInProlog
     */
    public void setBaseURI( AnyURIValue uri, boolean setInProlog )
    {
        if( baseURISetInProlog ) {
            return;
        }

        if( uri == null ) {
            baseURI = AnyURIValue.EMPTY_URI;
        }
        baseURI            = uri;
        baseURISetInProlog = setInProlog;
    }


    /**
     * Set the path to a base directory where modules should be loaded from. Relative module paths will be resolved against this directory. The
     * property is usually set by the XQueryServlet or XQueryGenerator, but can also be specified manually.
     *
     * @param  path
     */
    @Override
    public void setModuleLoadPath(String path) {
        this.moduleLoadPath = path;
    }


    @Override
    public String getModuleLoadPath() {
        return moduleLoadPath;
    }


    /**
     * The method <code>isBaseURIDeclared.</code>
     *
     * @return  a <code>boolean</code> value
     */
    public boolean isBaseURIDeclared()
    {
        if( ( baseURI == null ) || baseURI.equals( AnyURIValue.EMPTY_URI ) ) {
            return( false );
        } else {
            return( true );
        }
    }


    /**
     * Get the base URI of the evaluation context.
     *
     * <p>This is the URI returned by the fn:base-uri() function.</p>
     *
     * @return     base URI of the evaluation context
     *
     * @exception  XPathException  if an error occurs
     */
    public AnyURIValue getBaseURI() throws XPathException
    {
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
        if( ( baseURI == null ) || baseURI.equals( AnyURIValue.EMPTY_URI ) ) {
            //throw new XPathException("err:XPST0001: base URI of the static context  has not been assigned a value.");
            // We catch and resolve this to the XmlDbURI.ROOT_COLLECTION_URI
            // at least in DocumentImpl so maybe we should do it here./ljo
        }
        return( baseURI );
    }


    /**
     * Set the current context position, i.e. the position of the currently processed item in the context sequence. This value is required by some
     * expressions, e.g. fn:position().
     *
     * @param  pos
     * @param  sequence  
     */
    public void setContextSequencePosition( int pos, Sequence sequence )
    {
        contextPosition = pos;
        contextSequence = sequence;
    }


    /**
     * Get the current context position, i.e. the position of the currently processed item in the context sequence.
     *
     * @return  current context position
     */
    public int getContextPosition()
    {
        return( contextPosition );
    }


    public Sequence getContextSequence()
    {
        return( contextSequence );
    }


    public void pushInScopeNamespaces()
    {
        pushInScopeNamespaces( true );
    }


    /**
     * Push all in-scope namespace declarations onto the stack.
     *
     * @param  inherit  
     */
    @SuppressWarnings( "unchecked" )
    public void pushInScopeNamespaces( boolean inherit )
    {
        //TODO : push into an inheritedInScopeNamespaces HashMap... and return an empty HashMap
        final HashMap<String, String> m = (HashMap)inScopeNamespaces.clone();
        final HashMap<String, String> p = (HashMap)inScopePrefixes.clone();
        namespaceStack.push( inheritedInScopeNamespaces );
        namespaceStack.push( inheritedInScopePrefixes );
        namespaceStack.push( inScopeNamespaces );
        namespaceStack.push( inScopePrefixes );

        //Current namespaces now become inherited just like the previous inherited ones
        if( inherit ) {
            inheritedInScopeNamespaces = (HashMap)inheritedInScopeNamespaces.clone();
            inheritedInScopeNamespaces.putAll( m );
            inheritedInScopePrefixes = (HashMap)inheritedInScopePrefixes.clone();
            inheritedInScopePrefixes.putAll( p );
        } else {
            inheritedInScopeNamespaces = new HashMap<String, String>();
            inheritedInScopePrefixes   = new HashMap<String, String>();
        }

        //TODO : consider dynamic instanciation
        inScopeNamespaces = new HashMap<String, String>();
        inScopePrefixes   = new HashMap<String, String>();
    }


    public void popInScopeNamespaces()
    {
        inScopePrefixes            = namespaceStack.pop();
        inScopeNamespaces          = namespaceStack.pop();
        inheritedInScopePrefixes   = namespaceStack.pop();
        inheritedInScopeNamespaces = namespaceStack.pop();
    }


    @SuppressWarnings( "unchecked" )
    public void pushNamespaceContext()
    {
        HashMap<String, String> m = (HashMap)staticNamespaces.clone();
        HashMap<String, String> p = (HashMap)staticPrefixes.clone();
        namespaceStack.push( staticNamespaces );
        namespaceStack.push( staticPrefixes );
        staticNamespaces = m;
        staticPrefixes   = p;
    }


    public void popNamespaceContext()
    {
        staticPrefixes   = namespaceStack.pop();
        staticNamespaces = namespaceStack.pop();
    }


    /**
     * Returns the last variable on the local variable stack. The current variable context can be restored by passing the return value to {@link
     * #popLocalVariables(LocalVariable)}.
     *
     * @param   newContext  
     *
     * @return  last variable on the local variable stack
     */
    public LocalVariable markLocalVariables( boolean newContext )
    {
        if( newContext ) {

            if( lastVar == null ) {
                lastVar = new LocalVariable( QName.EMPTY_QNAME );
            }
            contextStack.push( lastVar );
        }
        variableStackSize++;
        return( lastVar );
    }


    public void popLocalVariables(LocalVariable var) {
        popLocalVariables(var, null);
    }

    /**
     * Restore the local variable stack to the position marked by variable var.
     *
     * @param  var
     *
     */
    public void popLocalVariables(LocalVariable var, Sequence resultSeq)
    {
        final LocalVariable end = contextStack.isEmpty() ? null : contextStack.peek();

        for( LocalVariable old = lastVar; old != null; old = old.before ) {

            if( old == end ) {
                break;
            }
            // reset variable unless it is a closure variable (inherited from context)
            if (!old.isClosureVar())
                {old.destroy(this, resultSeq);}
        }
        if( var != null ) {
            var.after = null;

            if( !contextStack.isEmpty() && ( var == contextStack.peek() ) ) {
                contextStack.pop();
            }
        }
        lastVar = var;
        variableStackSize--;
    }

    /**
     * Returns the current size of the stack. This is used to determine where a variable has been declared.
     *
     * @return  current size of the stack
     */
    public int getCurrentStackSize()
    {
        return( variableStackSize );
    }

    /* ----------------- Function call stack ------------------------ */


    /**
     * Report the start of a function execution. Adds the reported function signature to the function call stack.
     *
     * @param  signature  
     */
    public void functionStart( FunctionSignature signature )
    {
        callStack.push( signature );
    }


    /**
     * Report the end of the currently executed function. Pops the last function signature from the function call stack.
     */
    public void functionEnd()
    {
        if( callStack.isEmpty() ) {
            LOG.warn( "Function call stack is empty, but XQueryContext.functionEnd() was called. This " + "could indicate a concurrency issue (shared XQueryContext?)" );
        } else {
            callStack.pop();
        }
    }


    /**
     * Check if the specified function signature is found in the current function called stack. If yes, the function might be tail recursive and needs
     * to be optimized.
     *
     * @param   signature
     */
    public boolean tailRecursiveCall( FunctionSignature signature )
    {
        return( callStack.contains( signature ) );
    }


    /* ----------------- Module imports ------------------------ */

    public void mapModule( String namespace, XmldbURI uri )
    {
        mappedModules.put( namespace, uri );
    }
    
    /**
     * Import a module and make it available in this context. The prefix and location parameters are optional. If prefix is null, the default prefix
     * specified by the module is used. If location is null, the module will be read from the namespace URI.
     *
     * @param   namespaceURI
     * @param   prefix
     * @param   location
     *
     * @throws  XPathException
     */
    public Module importModule( String namespaceURI, String prefix, String location ) throws XPathException {
    	
        if(prefix != null && ("xml".equals(prefix) || "xmlns".equals(prefix))) {
            throw new XPathException(ErrorCodes.XQST0070, "The prefix declared for a module import must not be 'xml' or 'xmlns'.");
        }
        
    	if(namespaceURI != null && namespaceURI.isEmpty()) {
            throw new XPathException(ErrorCodes.XQST0088, "The first URILiteral in a module import must be of nonzero length.");
        }
    	
        Module module = null;

        if (namespaceURI != null)
            {module = getRootModule( namespaceURI );}

        if( module != null ) {
            LOG.debug( "Module " + namespaceURI + " already present." );

            // Set locally to remember the dependency in case it was inherited.
            setModule( namespaceURI, module );
        } else {
            // if location is not specified, try to resolve in expath repo
            if (location == null && namespaceURI != null) {
                module = resolveInEXPathRepository(namespaceURI, prefix);
            }

            if ( module == null ) {

                if( location == null && namespaceURI != null) {

                    // check if there's a static mapping in the configuration
                    location = getModuleLocation( namespaceURI );

                    if( location == null ) {
                        location = namespaceURI;
                    }
                }

                //Is the module's namespace mapped to a URL ?
                if( mappedModules.containsKey( location ) ) {
                    location = mappedModules.get( location ).toString();
                }

                // is it a Java module?
                if( location.startsWith( JAVA_URI_START ) ) {
                    location = location.substring( JAVA_URI_START.length() );
                    module   = loadBuiltInModule( namespaceURI, location );

                } else {
                    Source moduleSource;

                    if( location.startsWith( XmldbURI.XMLDB_URI_PREFIX )
                            || ( ( location.indexOf( ':' ) == -1 ) && moduleLoadPath.startsWith( XmldbURI.XMLDB_URI_PREFIX ) ) ) {

                        // Is the module source stored in the database?
                        try {
                            XmldbURI locationUri = XmldbURI.xmldbUriFor( location );

                            if( moduleLoadPath.startsWith( XmldbURI.XMLDB_URI_PREFIX ) ) {
                                final XmldbURI moduleLoadPathUri = XmldbURI.xmldbUriFor( moduleLoadPath );
                                locationUri = moduleLoadPathUri.resolveCollectionPath( locationUri );
                            }

                            DocumentImpl sourceDoc = null;

                            try {
                                sourceDoc = getBroker().getXMLResource( locationUri.toCollectionPathURI(), Lock.READ_LOCK );

                                if(sourceDoc == null) {
                                    throw moduleLoadException("Module location hint URI '" + location + " does not refer to anything.", location);
                                }

                                if(( sourceDoc.getResourceType() != DocumentImpl.BINARY_FILE ) || !"application/xquery".equals(sourceDoc.getMetadata().getMimeType())) {
                                    throw moduleLoadException("Module location hint URI '" + location + " does not refer to an XQuery.", location);
                                }

                                moduleSource = new DBSource( getBroker(), (BinaryDocument)sourceDoc, true );

                                // we don't know if the module will get returned, oh well
                                module = compileOrBorrowModule( prefix, namespaceURI, location, moduleSource );

                            } catch(final PermissionDeniedException e) {
                                throw moduleLoadException("Permission denied to read module source from location hint URI '" + location + ".", location, e);
                            } finally {
                                if(sourceDoc != null) {
                                    sourceDoc.getUpdateLock().release(Lock.READ_LOCK);
                                }
                            }
                        } catch(final URISyntaxException e) {
                            throw moduleLoadException("Invalid module location hint URI '" + location + ".", location, e);
                        }

                    } else {

                        // No. Load from file or URL
                        try {

                            //TODO: use URIs to ensure proper resolution of relative locations
                            moduleSource = SourceFactory.getSource( getBroker(), moduleLoadPath, location, true );

                        } catch(final MalformedURLException e) {
                            throw moduleLoadException("Invalid module location hint URI '" + location + ".", location, e);
                        } catch(final IOException e) {
                            throw moduleLoadException("Source for module '" + namespaceURI + "' not found module location hint URI '" + location + ".", location, e);
                        } catch(final PermissionDeniedException e) {
                            throw moduleLoadException("Permission denied to read module source from location hint URI '" + location + ".", location, e);
                        }

                        // we don't know if the module will get returned, oh well
                        module = compileOrBorrowModule(prefix, namespaceURI, location, moduleSource);
                    }
                }
            } // NOTE: expathrepo related, closes the EXPath else (if module != null)
        }
        if (namespaceURI == null) {
            namespaceURI = module.getNamespaceURI();
        }
        if( prefix == null ) {
            prefix = module.getDefaultPrefix();
        }
        declareNamespace( prefix, namespaceURI );
        
        return module;
    }

    
    protected XPathException moduleLoadException(final String message, final String moduleLocation) throws XPathException {
        return new XPathException(ErrorCodes.XQST0059, message, new ValueSequence(new StringValue(moduleLocation)));
    }
    
    protected XPathException moduleLoadException(final String message, final String moduleLocation, final Exception e) throws XPathException {
        return new XPathException(ErrorCodes.XQST0059, message, new ValueSequence(new StringValue(moduleLocation)), e);
    }

    /**
     * Returns the static location mapped to an XQuery source module, if known.
     *
     * @param   namespaceURI  the URI of the module
     *
     * @return  the location string
     */
    @SuppressWarnings( "unchecked" )
    public String getModuleLocation( String namespaceURI )
    {
        final Map<String, String> moduleMap = (Map)getBroker().getConfiguration().getProperty( PROPERTY_STATIC_MODULE_MAP );
        return( moduleMap.get( namespaceURI ) );
    }


    /**
     * Returns an iterator over all module namespace URIs which are statically mapped to a known location.
     *
     * @return  an iterator
     */
    @SuppressWarnings( "unchecked" )
    public Iterator<String> getMappedModuleURIs()
    {
        final Map<String, String> moduleMap = (Map)getBroker().getConfiguration().getProperty( PROPERTY_STATIC_MODULE_MAP );
        return( moduleMap.keySet().iterator() );
    }


    private ExternalModule compileOrBorrowModule( String prefix, String namespaceURI, String location, Source source ) throws XPathException
    {
        ExternalModule module = getBroker().getBrokerPool().getXQueryPool().borrowModule( getBroker(), source, this );

        if( module == null ) {
            module = compileModule( prefix, namespaceURI, location, source );
        } else {

            for( final Iterator<Module> it = module.getContext().getAllModules(); it.hasNext(); ) {
                final Module importedModule = it.next();

                if( ( importedModule != null ) && !allModules.containsKey( importedModule.getNamespaceURI() ) ) {
                    setRootModule( importedModule.getNamespaceURI(), importedModule );
                }
            }
        }
        setModule( module.getNamespaceURI(), module );
        declareModuleVars( module );
        return( module );
    }


    /**
     * Compile Module
     *
     * @param   prefix        
     * @param   namespaceURI  
     * @param   location      
     * @param   source        
     *
     * @return  The compiled module.
     *
     * @throws  XPathException
     */
    public ExternalModule compileModule( String prefix, String namespaceURI,
                                        String location, Source source ) throws XPathException
    {
        LOG.debug( "Loading module from " + location );

        Reader reader;

        try {
            reader = source.getReader();

            if( reader == null ) {
                throw( new XPathException( "failed to load module: '" + namespaceURI + "' from: '" + source + "', location: '" + location + "'. Source not found. " ) );
            }

            if (namespaceURI == null) {
                final QName qname = source.isModule();
                if (qname == null)
                    {return null;}
                namespaceURI = qname.getNamespaceURI();
            }
        }
        catch( final IOException e ) {
            throw( new XPathException( "IO exception while loading module '" + namespaceURI + "' from '" + source + "'", e ) );
        }
        final ExternalModuleImpl modExternal = new ExternalModuleImpl(namespaceURI, prefix);
        setModule(namespaceURI, modExternal);
        final XQueryContext    modContext = new ModuleContext( this, prefix, namespaceURI, location );
        modExternal.setContext( modContext );
        final XQueryLexer      lexer      = new XQueryLexer( modContext, reader );
        final XQueryParser     parser     = new XQueryParser( lexer );
        final XQueryTreeParser astParser  = new XQueryTreeParser( modContext, modExternal );

        try {
            parser.xpath();

            if( parser.foundErrors() ) {
                LOG.debug( parser.getErrorMessage() );
                throw( new XPathException( "error found while loading module from " + location + ": " + parser.getErrorMessage() ) );
            }
            final AST      ast  = parser.getAST();

            final PathExpr path = new PathExpr( modContext );
            astParser.xpath( ast, path );

            if( astParser.foundErrors() ) {
                throw( new XPathException( "error found while loading module from " + location + ": " + astParser.getErrorMessage(), astParser.getLastException() ) );
            }
            
            modExternal.setRootExpression(path);

            if(namespaceURI != null && !modExternal.getNamespaceURI().equals(namespaceURI)) {
                throw( new XPathException( "namespace URI declared by module (" + modExternal.getNamespaceURI() + ") does not match namespace URI in import statement, which was: " + namespaceURI ) );
            }

            // Set source information on module context
//            String sourceClassName = source.getClass().getName();
            modContext.setXacmlSource( XACMLSource.getInstance( source ) );
//            modContext.setSourceKey(source.getKey().toString());
            // Extract the source type from the classname by removing the package prefix and the "Source" suffix
//            modContext.setSourceType( sourceClassName.substring( 17, sourceClassName.length() - 6 ) );

            modExternal.setSource( source );
            modContext.setSource(source);
            modExternal.setIsReady(true);
            return( modExternal );
        }
        catch( final RecognitionException e ) {
            throw( new XPathException( e.getLine(), e.getColumn(), "error found while loading module from " + location + ": " + e.getMessage() ) );
        }
        catch( final TokenStreamException e ) {
            throw( new XPathException( "error found while loading module from " + location + ": " + e.getMessage(), e ) );
        }
        catch( final XPathException e ) {
            e.prependMessage( "Error while loading module " + location + ": " );
            throw( e );
        }
        catch( final Exception e ) {
        	e.printStackTrace();
            throw( new XPathException( "Internal error while loading module: " + location, e ) );
        }
        finally {

            try {

                if( reader != null ) {
                    reader.close();
                }
            }
            catch( final IOException e ) {
                LOG.warn( "Error while closing module source: " + e.getMessage(), e );
            }
        }
    }


    private void declareModuleVars( Module module )
    {
        final String moduleNS = module.getNamespaceURI();

        for( final Iterator<Variable> i = globalVariables.values().iterator(); i.hasNext(); ) {
            final Variable var = i.next();

            if( moduleNS.equals( var.getQName().getNamespaceURI() ) ) {
                module.declareVariable( var );
                i.remove();
            }
        }
    }


    /**
     * Add a forward reference to an undeclared function. Forward references will be resolved later.
     *
     * @param  call
     */
    public void addForwardReference( FunctionCall call )
    {
        forwardReferences.push( call );
    }


    /**
     * Resolve all forward references to previously undeclared functions.
     *
     * @throws  XPathException
     */
    public void resolveForwardReferences() throws XPathException
    {
        while( !forwardReferences.empty() ) {
            final FunctionCall        call = forwardReferences.pop();
            final UserDefinedFunction func = call.getContext().resolveFunction( call.getQName(), call.getArgumentCount() );

            if( func == null ) {
                throw( new XPathException( call, ErrorCodes.XPST0017, "Call to undeclared function: " + call.getQName().getStringValue() ) );
            }
            call.resolveForwardReference( func );
        }
    }
    
    /**
     * Get environment variables. The variables shall not change 
     * during execution of query.
     * 
     * @return Map of environment variables
     */
    public Map<String, String> getEnvironmentVariables(){
        if(envs==null){
            envs = System.getenv();
        }
        return envs;
    }

    /* ----------------- Save state ------------------------ */
    
    private class SavedState {
    	
    	private HashMap<String, Module> modulesSaved = null;
    	private HashMap<String, Module> allModulesSaved = null;
    	private HashMap<String, String> staticNamespacesSaved = null;
    	private HashMap<String, String> staticPrefixesSaved = null;
    	
    	@SuppressWarnings("unchecked")
		void save() {
    		if (modulesSaved == null) {
	    		modulesSaved = (HashMap<String, Module>) modules.clone();
	        	allModulesSaved = (HashMap<String, Module>) allModules.clone();
	        	staticNamespacesSaved = (HashMap<String, String>) staticNamespaces.clone();
	        	staticPrefixesSaved = (HashMap<String, String>) staticPrefixes.clone();
    		}
    	}
    	
    	void restore() {
    		if (modulesSaved != null) {
	    		modules = modulesSaved;
	    		modulesSaved = null;
	    		allModules = allModulesSaved;
	    		allModulesSaved = null;
	    		staticNamespaces = staticNamespacesSaved;
	    		staticNamespacesSaved = null;
	    		staticPrefixes = staticPrefixesSaved;
	    		staticPrefixesSaved = null;
    		}
    	}
    }
    
    /**
     * Before a dynamic import, make sure relevant parts of the current context a saved
     * to the stack. This is important for util:import-module. The context will be restored
     * during {@link #reset()}.
     */
    public void saveState() {
    	savedState.save();
    }
    
    public boolean optimizationsEnabled()
    {
        return( enableOptimizer );
    }


    /**
     * for static compile-time options i.e. declare option
     *
     * @param   qnameString  
     * @param   contents     
     *
     * @throws  XPathException  
     */
    public void addOption( String qnameString, String contents ) throws XPathException
    {
        if( staticOptions == null ) {
            staticOptions = new ArrayList<Option>();
        }

        addOption( staticOptions, qnameString, contents );
    }


    /**
     * for dynamic run-time options i.e. util:declare-option
     *
     * @param   qnameString  
     * @param   contents     
     *
     * @throws  XPathException  
     */
    public void addDynamicOption( String qnameString, String contents ) throws XPathException
    {
        if( dynamicOptions == null ) {
            dynamicOptions = new ArrayList<Option>();
        }

        addOption( dynamicOptions, qnameString, contents );
    }


    private void addOption( List<Option> options, String qnameString, String contents ) throws XPathException
    {
        final QName  qn     = QName.parse( this, qnameString, defaultFunctionNamespace );

        final Option option = new Option( qn, contents );

        //if the option exists, remove it so we can add the new option
        for( int i = 0; i < options.size(); i++ ) {

            if( options.get( i ).equals( option ) ) {
                options.remove( i );
                break;
            }
        }

        //add option
        options.add( option );

        // check predefined options
        if( Option.PROFILE_QNAME.compareTo( qn ) == 0 ) {

            // configure profiling
            profiler.configure( option );
        } else if( Option.TIMEOUT_QNAME.compareTo( qn ) == 0 ) {
            watchdog.setTimeoutFromOption( option );
        } else if( Option.OUTPUT_SIZE_QNAME.compareTo( qn ) == 0 ) {
            watchdog.setMaxNodesFromOption( option );
        } else if( Option.OPTIMIZE_QNAME.compareTo( qn ) == 0 ) {
            final String[] params = option.tokenizeContents();

            if( params.length > 0 ) {
                final String[] param = Option.parseKeyValuePair( params[0] );

                if( param != null && "enable".equals( param[0] ) ) {

                    if( "yes".equals( param[1] ) ) {
                        enableOptimizer = true;
                    } else {
                        enableOptimizer = false;
                    }
                }
            }
        }
        //TODO : not sure how these 2 options might/have to be related
        else if( Option.OPTIMIZE_IMPLICIT_TIMEZONE.compareTo( qn ) == 0 ) {

            //TODO : error check
            final Duration duration = TimeUtils.getInstance().newDuration( option.getContents() );
            implicitTimeZone = new SimpleTimeZone( (int)duration.getTimeInMillis( new Date() ), "XQuery context" );
        } else if( Option.CURRENT_DATETIME.compareTo( qn ) == 0 ) {

            //TODO : error check
            final DateTimeValue dtv = new DateTimeValue( option.getContents() );
            calendar = (XMLGregorianCalendar)dtv.calendar.clone();
        }
    }


    public Option getOption( QName qname )
    {
        /*
         * check dynamic options that were declared at run-time
         * first as these have precedence and then check
         * static options that were declare at compile time
         */
        if( dynamicOptions != null ) {

            for( final Option option : dynamicOptions ) {

                if( qname.compareTo( option.getQName() ) == 0 ) {
                    return( option );
                }
            }
        }

        if( staticOptions != null ) {

            for( final Option option : staticOptions ) {

                if( qname.compareTo( option.getQName() ) == 0 ) {
                    return( option );
                }
            }
        }

        return( null );
    }


    public Pragma getPragma( String name, String contents ) throws XPathException
    {
        final QName qname = QName.parse( this, name );

        if( "".equals( qname.getNamespaceURI() ) ) {
            throw( new XPathException( "XPST0081: pragma's ('" + name + "') namespace URI is empty" ) );
        } else if( Namespaces.EXIST_NS.equals( qname.getNamespaceURI() ) ) {
            contents = StringValue.trimWhitespace( contents );

            if( TimerPragma.TIMER_PRAGMA.equalsSimple( qname ) ) {
                return( new TimerPragma( qname, contents ) );
            }

            if( Optimize.OPTIMIZE_PRAGMA.equalsSimple( qname ) ) {
                return( new Optimize( this, qname, contents, true ) );
            }

            if( ForceIndexUse.EXCEPTION_IF_INDEX_NOT_USED_PRAGMA.equalsSimple( qname ) ) {
                return( new ForceIndexUse( qname, contents ) );
            }

            if( ProfilePragma.PROFILING_PRAGMA.equalsSimple( qname ) ) {
                return( new ProfilePragma( qname, contents ) );
            }

            if( NoIndexPragma.NO_INDEX_PRAGMA.equalsSimple( qname ) ) {
                return( new NoIndexPragma( qname, contents ) );
            }
        }
        return( null );
    }


    /**
     * Store the supplied data to a temporary document fragment.
     *
     * @param   doc
     *
     * @return  TemporaryDoc fragment
     *
     * @throws  XPathException
     */
    public DocumentImpl storeTemporaryDoc( org.exist.memtree.DocumentImpl doc ) throws XPathException
    {
        try {
            final DocumentImpl targetDoc = getBroker().storeTempResource( doc );

            if( targetDoc == null ) {
                throw( new XPathException( "Internal error: failed to store temporary doc fragment" ) );
            }
            LOG.warn( "Stored: " + targetDoc.getDocId() + ": " + targetDoc.getURI(), new Throwable() );
            return( targetDoc );
        }
        catch( final EXistException e ) {
            throw( new XPathException( TEMP_STORE_ERROR, e ) );
        }
        catch( final PermissionDeniedException e ) {
            throw( new XPathException( TEMP_STORE_ERROR, e ) );
        }
        catch( final LockException e ) {
            throw( new XPathException( TEMP_STORE_ERROR, e ) );
        }
    }


    public void setAttribute( String attribute, Object value )
    {
        attributes.put( attribute, value );
    }


    public Object getAttribute( String attribute )
    {
        return( attributes.get( attribute ) );
    }


    /**
     * Set an XQuery Context variable. General variable storage in the xquery context
     *
     * @param  name   The variable name
     * @param  XQvar  The variable value, may be of any xs: type
     */
    public void setXQueryContextVar( String name, Object XQvar )
    {
        XQueryContextVars.put( name, XQvar );
    }


    /**
     * Get an XQuery Context variable. General variable storage in the xquery context
     *
     * @param   name  The variable name
     *
     * @return  The variable value indicated by name.
     */
    public Object getXQueryContextVar( String name )
    {
        return( XQueryContextVars.get( name ) );
    }


    /**
     * Load the default prefix/namespace mappings table and set up internal functions.
     *
     * @param  config  
     */
    @SuppressWarnings( "unchecked" )
    protected void loadDefaults( Configuration config )
    {
        this.watchdog = new XQueryWatchDog( this );

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
        
        // Switch: enable optimizer
        Object param = config.getProperty( PROPERTY_ENABLE_QUERY_REWRITING );
        enableOptimizer     = ( param != null ) && "yes".equals(param.toString());

        // Switch: Backward compatibility
        param = config.getProperty( PROPERTY_XQUERY_BACKWARD_COMPATIBLE );
        backwardsCompatible = ( param == null ) || "yes".equals(param.toString());

        // Switch: raiseErrorOnFailedRetrieval
        final Boolean option = ( (Boolean)config.getProperty( PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL ) );
        raiseErrorOnFailedRetrieval = ( option != null ) && option.booleanValue();

        // Get map of built-in modules
        final Map<String, Class<Module>> builtInModules = (Map)config.getProperty( PROPERTY_BUILT_IN_MODULES );

        if( builtInModules != null ) {

            // Iterate on all map entries
            for( final Map.Entry<String, Class<Module>> entry : builtInModules.entrySet() ) {

                // Get URI and class
                final String        namespaceURI = entry.getKey();
                final Class<Module> moduleClass  = entry.getValue();
                
                // first check if the module has already been loaded in the parent context
                final Module        module       = getModule( namespaceURI );

                if( module == null ) {
                    // Module does not exist yet, instantiate
                    instantiateModule( namespaceURI, moduleClass, (Map<String, Map<String, List<? extends Object>>>)config.getProperty(PROPERTY_MODULE_PARAMETERS));

                } else if( ( getPrefixForURI( module.getNamespaceURI() ) == null )
                           && ( module.getDefaultPrefix().length() > 0 ) ) {

                    // make sure the namespaces of default modules are known,
                    // even if they were imported in a parent context
                    try {
                        declareNamespace( module.getDefaultPrefix(), module.getNamespaceURI() );
                        
                    } catch( final XPathException e ) {
                        LOG.warn( "Internal error while loading default modules: " + e.getMessage(), e );
                    }
                }
            }
        }
    }


    /**
     * Load default namespaces, e.g. xml, xsi, xdt, fn, local, exist and dbgp.
     */
    protected void loadDefaultNS(){
        try {

            // default namespaces
            staticNamespaces.put( "xml", Namespaces.XML_NS );
            staticPrefixes.put( Namespaces.XML_NS, "xml" );
            declareNamespace( "xs", Namespaces.SCHEMA_NS );
            declareNamespace( "xsi", Namespaces.SCHEMA_INSTANCE_NS );

            //required for backward compatibility
            declareNamespace( "xdt", Namespaces.XPATH_DATATYPES_NS );
            declareNamespace( "fn", Namespaces.XPATH_FUNCTIONS_NS );
            declareNamespace( "local", Namespaces.XQUERY_LOCAL_NS );

            //*not* as standard NS
            declareNamespace( "exist", Namespaces.EXIST_NS );

            //TODO : include "err" namespace ?
            declareNamespace( "dbgp", Debuggee.NAMESPACE_URI );

        } catch( final XPathException e ) {
            //ignored because it should never happen
            LOG.debug(e);
        }
    }


    public void registerUpdateListener( UpdateListener listener )
    {
        if( updateListener == null ) {
            updateListener = new ContextUpdateListener();
            final DBBroker broker = getBroker();
            broker.getBrokerPool().getNotificationService().subscribe( updateListener );
        }
        updateListener.addListener( listener );
    }


    protected void clearUpdateListeners()
    {
        if( updateListener != null ) {
            final DBBroker broker = getBroker();
            broker.getBrokerPool().getNotificationService().unsubscribe( updateListener );
        }
        updateListener = null;
    }

    /**
     * Check if the XQuery contains options that define serialization settings. If yes,
     * copy the corresponding settings to the current set of output properties.
     *
     * @param   properties  the properties object to which serialization parameters will be added.
     *
     * @throws  XPathException  if an error occurs while parsing the option
     */
    public void checkOptions(Properties properties) throws XPathException {
        checkLegacyOptions(properties);
        if(dynamicOptions != null) {
            for(final Option option : dynamicOptions) {
                if (Namespaces.XSLT_XQUERY_SERIALIZATION_NS.equals(option.getQName().getNamespaceURI())) {
                    properties.put(option.getQName().getLocalName(), option.getContents());
                }
            }
        }

        if( staticOptions != null ) {
            for(final Option option : staticOptions) {
                if (Namespaces.XSLT_XQUERY_SERIALIZATION_NS.equals(option.getQName().getNamespaceURI())) {
                    if (!properties.containsKey(option.getQName().getLocalName()))
                        {properties.put(option.getQName().getLocalName(), option.getContents());}
                }
            }
        }
    }

    /**
     * Legacy method to check serialization properties set via option exist:serialize.
     *
     * @param properties
     * @throws XPathException
     */
    private void checkLegacyOptions( Properties properties ) throws XPathException
    {
        final Option pragma = getOption( Option.SERIALIZE_QNAME );

        if( pragma == null ) {
            return;
        }
        final String[] contents = pragma.tokenizeContents();

        for( int i = 0; i < contents.length; i++ ) {
            final String[] pair = Option.parseKeyValuePair( contents[i] );

            if( pair == null ) {
                throw( new XPathException( "Unknown parameter found in "
                        + pragma.getQName().getStringValue() + ": '" + contents[i] + "'" ) );
            }
            LOG.debug( "Setting serialization property from pragma: " + pair[0] + " = " + pair[1] );
            properties.setProperty( pair[0], pair[1] );
        }
    }


    public void setDebuggeeJoint( DebuggeeJoint joint )
    {
        //XXX: if (debuggeeJoint != null) ???
        debuggeeJoint = joint;
    }


    public DebuggeeJoint getDebuggeeJoint()
    {
        return( debuggeeJoint );
    }


    public boolean isDebugMode()
    {
        return( ( debuggeeJoint != null ) && isVarDeclared( Debuggee.SESSION ) );
    }

    public boolean requireDebugMode() 
    {
        return isVarDeclared( Debuggee.SESSION );
    }

    private List<BinaryValue> binaryValueInstances;
    
    @Override
    public void registerBinaryValueInstance(final BinaryValue binaryValue) {
        if(binaryValueInstances == null) {
             binaryValueInstances = new ArrayList<BinaryValue>();
             
             cleanupTasks.add(new CleanupTask() {
                 
                 @Override
                 public void cleanup(final XQueryContext context) {
                    if(context.binaryValueInstances != null) {
                       for(final BinaryValue bv : context.binaryValueInstances) {
                           try {
                               bv.close();
                           } catch (final IOException ioe) {
                               LOG.error("Unable to close binary value: " + ioe.getMessage(), ioe);
                           }
                       }
                       context.binaryValueInstances.clear();
                   }
                 }
             });
        }
        
        binaryValueInstances.add(binaryValue);
    }

    @Override
    public String getCacheClass() {
        return (String) getBroker().getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY);
    }

    public void destroyBinaryValue(BinaryValue value) {
        if (binaryValueInstances != null) {
            for (int i = binaryValueInstances.size() - 1; i > -1; i--) {
                final BinaryValue bv = binaryValueInstances.get(i);
                if (bv == value) {
                    binaryValueInstances.remove(i);
                    return;
                }
            }
        }
    }
    
    public void setXQueryVersion(int version) {
        xqueryVersion=version;
    }

    public int getXQueryVersion(){
        return xqueryVersion;
    }

    @Override
    public Source getSource() {
        return source;
    }

    @Override
    public void setSource(final Source source) {
        this.source = source;
    }


    // ====================================================================================

    
    private class ContextUpdateListener implements UpdateListener {

        private List<UpdateListener> listeners = new ArrayList<UpdateListener>();

        public void addListener( UpdateListener listener )
        {
			synchronized( listeners ) { // TODO field must be final?
           		listeners.add( listener );
			}
        }


        public void documentUpdated( DocumentImpl document, int event )
        {
			synchronized( listeners ) { // TODO field must be final?
	            for( final UpdateListener listener : listeners ) {
	
	                if( listener != null ) {
	                    listener.documentUpdated( document, event );
	                }
	            }
			}
        }


        public void unsubscribe()
        {
			synchronized( listeners ) { // TODO field must be final?
	            for( final UpdateListener listener : listeners ) {
	
	                if( listener != null ) {
	                    listener.unsubscribe();
	                }
	            }
	            listeners.clear();
			}
        }


        public void nodeMoved( NodeId oldNodeId, StoredNode newNode )
        {
            for( int i = 0; i < listeners.size(); i++ ) {
                final UpdateListener listener = (UpdateListener)listeners.get( i );

                if( listener != null ) {
                    listener.nodeMoved( oldNodeId, newNode );
                }
            }
        }


        public void debug() {
            
            LOG.debug(String.format("XQueryContext: %s document update listeners", listeners.size()));
            for (int i = 0; i < listeners.size(); i++) {
                ((UpdateListener) listeners.get(i)).debug();
            }
        }

    }
    
    private List<CleanupTask> cleanupTasks = new ArrayList<CleanupTask>();
    
    public void registerCleanupTask(final CleanupTask cleanupTask) {
        cleanupTasks.add(cleanupTask);
    }
    
    public interface CleanupTask {
        public void cleanup(final XQueryContext context);
    }
    
    @Override
    public void runCleanupTasks() {
        for(final CleanupTask cleanupTask : cleanupTasks) {
            try {
                cleanupTask.cleanup(this);
            } catch(final Throwable t) {
                LOG.error("Cleaning up XQueryContext: Ignoring: " + t.getMessage(), t);
            }
        }
    }
}
