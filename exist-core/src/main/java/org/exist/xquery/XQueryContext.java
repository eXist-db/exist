/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2018 The eXist Project
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
package org.exist.xquery;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.stream.XMLStreamException;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;
import com.ibm.icu.text.Collator;
import net.jcip.annotations.Immutable;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.debuggee.Debuggee;
import org.exist.debuggee.DebuggeeJoint;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.http.servlets.*;
import org.exist.interpreter.Context;
import org.exist.dom.memtree.InMemoryXMLStreamReader;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.repo.ExistRepository;
import org.exist.security.AuthenticationException;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.source.*;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.UpdateListener;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.util.Collations;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.util.hashtable.NamePool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.parser.*;
import org.exist.xquery.pragmas.*;
import org.exist.xquery.update.Modification;
import org.exist.xquery.util.SerializerUtils;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static javax.xml.XMLConstants.XMLNS_ATTRIBUTE;
import static javax.xml.XMLConstants.XML_NS_PREFIX;
import static org.exist.Namespaces.XML_NS;

/**
 * The current XQuery execution context. Contains the static as well as the dynamic
 * XQuery context components.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class XQueryContext implements BinaryValueManager, Context {

    private static final Logger LOG = LogManager.getLogger(XQueryContext.class);

    public static final String ENABLE_QUERY_REWRITING_ATTRIBUTE = "enable-query-rewriting";
    public static final String XQUERY_BACKWARD_COMPATIBLE_ATTRIBUTE = "backwardCompatible";
    public static final String XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_ATTRIBUTE = "raise-error-on-failed-retrieval";
    public static final String ENFORCE_INDEX_USE_ATTRIBUTE = "enforce-index-use";

    //TODO : move elsewhere ?
    public static final String BUILT_IN_MODULE_URI_ATTRIBUTE = "uri";
    public static final String BUILT_IN_MODULE_CLASS_ATTRIBUTE = "class";
    public static final String BUILT_IN_MODULE_SOURCE_ATTRIBUTE = "src";

    public static final String PROPERTY_XQUERY_BACKWARD_COMPATIBLE = "xquery.backwardCompatible";
    public static final String PROPERTY_ENABLE_QUERY_REWRITING = "xquery.enable-query-rewriting";
    public static final String PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL = "xquery.raise-error-on-failed-retrieval";
    public static final boolean XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_DEFAULT = false;
    public static final String PROPERTY_ENFORCE_INDEX_USE = "xquery.enforce-index-use";

    //TODO : move elsewhere ?
    public static final String PROPERTY_BUILT_IN_MODULES = "xquery.modules";
    public static final String PROPERTY_STATIC_MODULE_MAP = "xquery.modules.static";
    public static final String PROPERTY_MODULE_PARAMETERS = "xquery.modules.parameters";

    public static final String JAVA_URI_START = "java:";
    //private static final String XMLDB_URI_START = "xmldb:exist://";

    private static final String TEMP_STORE_ERROR = "Error occurred while storing temporary data";
    public static final String XQUERY_CONTEXTVAR_XQUERY_UPDATE_ERROR = "_eXist_xquery_update_error";
    public static final String HTTP_SESSIONVAR_XMLDB_USER = "_eXist_xmldb_user";

    public static final String HTTP_REQ_ATTR_USER = "xquery.user";
    public static final String HTTP_REQ_ATTR_PASS = "xquery.password";

    // Static namespace/prefix mappings
    protected Map<String, String> staticNamespaces = new HashMap<>();

    // Static prefix/namespace mappings
    protected Map<String, String> staticPrefixes = new HashMap<>();

    // Local in-scope namespace/prefix mappings in the current context
    Map<String, String> inScopeNamespaces = new HashMap<>();

    // Local prefix/namespace mappings in the current context
    private Map<String, String> inScopePrefixes = new HashMap<>();

    // Inherited in-scope namespace/prefix mappings in the current context
    private Map<String, String> inheritedInScopeNamespaces = new HashMap<>();

    // Inherited prefix/namespace mappings in the current context
    private Map<String, String> inheritedInScopePrefixes = new HashMap<>();

    private Map<String, XmldbURI> mappedModules = new HashMap<>();

    private boolean preserveNamespaces = true;

    private boolean inheritNamespaces = true;

    // Local namespace stack
    private Deque<Map<String, String>> namespaceStack = new ArrayDeque<>();

    // Known user defined functions in the local module
    private TreeMap<FunctionId, UserDefinedFunction> declaredFunctions = new TreeMap<>();

    // Globally declared variables
    protected Map<QName, Variable> globalVariables = new TreeMap<>();

    // The last element in the linked list of local in-scope variables
    private LocalVariable lastVar = null;

    private Deque<LocalVariable> contextStack = new ArrayDeque<>();

    private Deque<FunctionSignature> callStack = new ArrayDeque<>();

    // The current size of the variable stack
    private int variableStackSize = 0;

    // Unresolved references to user defined functions
    private Deque<FunctionCall> forwardReferences = new ArrayDeque<>();

    // Inline functions using closures need to be cleared after execution
    private Deque<UserDefinedFunction> closures = new ArrayDeque<>();

    // List of options declared for this query at compile time - i.e. declare option
    private List<Option> staticOptions = null;

    // List of options declared for this query at run time - i.e. util:declare-option()
    private List<Option> dynamicOptions = null;

    //The Calendar for this context : may be changed by some options
    private XMLGregorianCalendar calendar = null;
    private TimeZone implicitTimeZone = null;

    /**
     * the watchdog object assigned to this query.
     */
    protected XQueryWatchDog watchdog;

    /**
     * Loaded modules.
     */
    protected Map<String, Module> modules = new HashMap<>();

    /**
     * Loaded modules, including ones bubbled up from imported modules.
     */
    private Map<String, Module> allModules = new HashMap<>();

    /**
     * Used to save current state when modules are imported dynamically
     */
    private SavedState savedState = new SavedState();

    /**
     * Whether some modules were rebound to new instances since the last time this context's query was analyzed. (This assumes that each context is
     * attached to at most one query.)
     */
    @SuppressWarnings("unused")
    private boolean modulesChanged = true;

    /**
     * The set of statically known documents specified as an array of paths to documents and collections.
     */
    private XmldbURI[] staticDocumentPaths = null;

    /**
     * The actual set of statically known documents. This will be generated on demand from staticDocumentPaths.
     */
    private DocumentSet staticDocuments = null;

    /**
     * A set of documents which were modified during the query, usually through an XQuery update extension. The documents will be checked after the
     * query completed to see if a defragmentation run is needed.
     */
    protected MutableDocumentSet modifiedDocuments = null;

    /**
     * A general-purpose map to set attributes in the current query context.
     */
    protected Map<String, Object> attributes = new HashMap<>();

    protected AnyURIValue baseURI = AnyURIValue.EMPTY_URI;

    private boolean baseURISetInProlog = false;

    protected String moduleLoadPath = ".";

    private String defaultFunctionNamespace = Function.BUILTIN_FUNCTION_NS;
    private AnyURIValue defaultElementNamespace = AnyURIValue.EMPTY_URI;
    private AnyURIValue defaultElementNamespaceSchema = AnyURIValue.EMPTY_URI;

    /**
     * The default collation URI.
     */
    private String defaultCollation = Collations.UNICODE_CODEPOINT_COLLATION_URI;

    /**
     * Default Collator. Will be null for the default unicode codepoint collation.
     */
    private Collator defaultCollator = null;

    /**
     * Set to true to enable XPath 1.0 backwards compatibility.
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
     * XQuery 3.0 - declare context item :=
     */
    private ContextItemDeclaration contextItemDeclaration = null;

    /**
     * The context item set in the query prolog or externally
     */
    private Sequence contextItem = Sequence.EMPTY_SEQUENCE;

    /**
     * The position of the currently processed item in the context sequence. This field has to be set on demand, for example, before calling the
     * fn:position() function.
     */
    private int contextPosition = 0;
    private Sequence contextSequence = null;

    /**
     * Shared name pool used by all in-memory documents constructed in this query context.
     */
    private NamePool sharedNamePool = null;

    /**
     * Stack for temporary document fragments.
     */
    private Deque<MemTreeBuilder> fragmentStack = new ArrayDeque<>();

    /**
     * The root of the expression tree.
     */
    private Expression rootExpression;

    /**
     * An incremental counter to count the expressions in the current XQuery. Used during compilation to assign a unique ID to every expression.
     */
    private int expressionCounter = 0;

//    /**
//     * Should all documents loaded by the query be locked? If set to true, it is the responsibility of the calling client code to unlock documents
//     * after the query has completed.
//     */
//  private boolean lockDocumentsOnLoad = false;

//    /**
//     * Documents locked during the query.
//     */
//  private LockedDocumentMap lockedDocuments = null;

    private LockedDocumentMap protectedDocuments = null;

    /**
     * The profiler instance used by this context.
     */
    protected Profiler profiler;

    //For holding XQuery Context variables for general storage in the XQuery Context
    private Map<String, Object> XQueryContextVars = new HashMap<>();

    //For holding the environment variables
    private Map<String, String> envs;

    //For holding the Java System Properties
    private Map<String, String> props;

    private ContextUpdateListener updateListener = null;

    private boolean enableOptimizer = true;

    private boolean raiseErrorOnFailedRetrieval = XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL_DEFAULT;

    private boolean isShared = false;

    private Source source = null;

    private DebuggeeJoint debuggeeJoint = null;

    private int xqueryVersion = 31;

    protected Database db;

    private boolean analyzed = false;

    /**
     * The Subject of the User that requested the execution of the XQuery
     * attached by this Context. This is not the same as the Effective User
     * as we may be executed setUid or setGid. The Effective User can be retrieved
     * through broker.getCurrentSubject()
     */
    private Subject realUser;

    /**
     * Indicates whether a user from a http session
     * was pushed onto the current broker from {@link XQueryContext#prepareForExecution()},
     * if so then we must pop the user in {@link XQueryContext#reset(boolean)}
     */
    private boolean pushedUserFromHttpSession = false;

    /**
     * The HTTP context within which the XQuery
     * is executing, or null if there is no
     * HTTP context.
     */
    @Nullable
    private HttpContext httpContext = null;

    public XQueryContext() {
        profiler = new Profiler(null);
    }

    public XQueryContext(final Database db) {
        this(db, new Profiler(db));
    }

    public XQueryContext(final Database db, Profiler profiler) {
        this();
        this.db = db;
        loadDefaults(db.getConfiguration());
        this.profiler = profiler;
    }

    public XQueryContext(final XQueryContext copyFrom) {
        this();
        this.db = copyFrom.db;
        loadDefaultNS();

        for (final String prefix : copyFrom.staticNamespaces.keySet()) {
            if (XML_NS_PREFIX.equals(prefix) || XMLNS_ATTRIBUTE.equals(prefix)) {
                continue;
            }

            try {
                declareNamespace(prefix, copyFrom.staticNamespaces.get(prefix));
            } catch (final XPathException ex) {
                ex.printStackTrace();
            }
        }
        this.profiler = copyFrom.profiler;
    }


    /**
     * Get the HTTP context of the XQuery.
     *
     * @return the HTTP context, or null if the query
     * is not being executed within an HTTP context.
     */
    public @Nullable
    HttpContext getHttpContext() {
        return httpContext;
    }

    /**
     * Set the HTTP context of the XQuery.
     *
     * @param httpContext the HTTP context within which the XQuery
     *     is being executed.
     */
    public void setHttpContext(final HttpContext httpContext) {
        this.httpContext = httpContext;
    }

    public Optional<ExistRepository> getRepository() {
        return getBroker().getBrokerPool().getExpathRepo();
    }

    private Module resolveInEXPathRepository(final String namespace, final String prefix)
            throws XPathException {
        // the repo and its eXist handler
        final Optional<ExistRepository> repo = getRepository();
        // try an internal module
        if (repo.isPresent()) {
            final Module jMod = repo.get().resolveJavaModule(namespace, this);
            if (jMod != null) {
                return jMod;
            }
        }
        // try an eXist-specific module
        Path resolved = null;
        if (repo.isPresent()) {
            resolved = repo.get().resolveXQueryModule(namespace);
            // use the resolved file or return null
            if (resolved == null) {
                return null;
            }
        }
        // build a module object from the file
        final Source src = new FileSource(resolved, false);
        return compileOrBorrowModule(prefix, namespace, "", src);
    }

    /**
     * Prepares the XQuery Context for use.
     * <p>
     * Should be called before compilation to prepare the query context,
     * or before re-execution if the query was cached.
     */
    public void prepareForReuse() throws XPathException {
        // prepare the variables of the internal modules (which were previously reset)
        try (final Stream<InternalModule> internalModules = allModules
                .values()
                .stream()
                .filter(module -> module instanceof InternalModule)
                .map(module -> (InternalModule) module)) {
            for (final InternalModule internalModule : internalModules.collect(Collectors.toList())) {
                internalModule.prepare(this);
            }
        }
    }

    @Override
    public boolean hasParent() {
        return false;
    }

    @Override
    public XQueryContext getRootContext() {
        return this;
    }

    @Override
    public XQueryContext copyContext() {
        final XQueryContext ctx = new XQueryContext(this);
        copyFields(ctx);
        return ctx;
    }

    @Override
    public void updateContext(final XQueryContext from) {
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
        this.dynamicOptions = from.dynamicOptions;
        this.staticOptions = from.staticOptions;
        this.db = from.db;
        this.httpContext = from.httpContext;
    }

    protected void copyFields(final XQueryContext ctx) {
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

        ctx.declaredFunctions = new TreeMap<>(this.declaredFunctions);
        ctx.globalVariables = new TreeMap<>(this.globalVariables);
        ctx.attributes = new HashMap<>(this.attributes);

        // make imported modules available in the new context
        ctx.modules = new HashMap<>();

        for (final Module module : this.modules.values()) {

            try {
                ctx.modules.put(module.getNamespaceURI(), module);
                final String prefix = this.staticPrefixes.get(module.getNamespaceURI());
                ctx.declareNamespace(prefix, module.getNamespaceURI());
            } catch (final XPathException e) {
                // ignore
            }
        }
        ctx.allModules = new HashMap<>();

        for (final Module module : this.allModules.values()) {

            if (module != null) { //UNDERSTAND: why is it possible? -shabanovd
                ctx.allModules.put(module.getNamespaceURI(), module);
            }
        }

        ctx.watchdog = this.watchdog;
        ctx.profiler = getProfiler();
        ctx.lastVar = this.lastVar;
        ctx.variableStackSize = getCurrentStackSize();
        ctx.contextStack = this.contextStack;
        ctx.mappedModules = new HashMap<>(this.mappedModules);
        ctx.staticNamespaces = new HashMap<>(this.staticNamespaces);
        ctx.staticPrefixes = new HashMap<>(this.staticPrefixes);

        if (this.dynamicOptions != null) {
            ctx.dynamicOptions = new ArrayList<>(this.dynamicOptions);
        }

        if (this.staticOptions != null) {
            ctx.staticOptions = new ArrayList<>(this.staticOptions);
        }

        ctx.source = this.source;
        ctx.httpContext = this.httpContext;
    }

    @Override
    public void prepareForExecution() {
        //if there is an existing user in the current http session
        //then set the DBBroker user
        final Subject user = getUserFromHttpSession();
        if (user != null) {
            getBroker().pushSubject(user);      //this will be popped in {@link XQueryContext#reset(boolean)}
            this.pushedUserFromHttpSession = true;
        }

        setRealUser(getBroker().getCurrentSubject());   //this will be unset in {@link XQueryContext#reset(boolean)}

        //Reset current context position
        setContextSequencePosition(0, null);
        //Note that, for some reasons, an XQueryContext might be used without calling this method
    }

    public void setContextItem(final Sequence contextItem) {
        this.contextItem = contextItem;
    }

    public void setContextItemDeclaration(final ContextItemDeclaration contextItemDeclaration) {
        this.contextItemDeclaration = contextItemDeclaration;
    }

    public ContextItemDeclaration getContextItemDeclartion() {
        return contextItemDeclaration;
    }

    public Sequence getContextItem() {
        return contextItem;
    }

    @Override
    public boolean isProfilingEnabled() {
        return profiler.isEnabled();
    }

    @Override
    public boolean isProfilingEnabled(final int verbosity) {
        return profiler.isEnabled() && profiler.verbosity() >= verbosity;
    }

    @Override
    public Profiler getProfiler() {
        return profiler;
    }

    @Override
    public void setRootExpression(final Expression expr) {
        this.rootExpression = expr;
    }

    @Override
    public Expression getRootExpression() {
        return rootExpression;
    }

    /**
     * Returns the next unique expression id. Every expression in the XQuery is identified by a unique id. During compilation, expressions are
     * assigned their id by calling this method.
     *
     * @return The next unique expression id.
     */
    int nextExpressionId() {
        return expressionCounter++;
    }

    @Override
    public int getExpressionCount() {
        return expressionCounter;
    }

    @Override
    public void declareNamespace(String prefix, String uri) throws XPathException {
        if (prefix == null) {
            prefix = "";
        }

        if (uri == null) {
            uri = "";
        }

        if (XML_NS_PREFIX.equals(prefix) || XMLNS_ATTRIBUTE.equals(prefix)) {
            throw new XPathException(ErrorCodes.XQST0070, "Namespace predefined prefix '" + prefix + "' can not be bound");
        }

        if (uri.equals(XML_NS)) {
            throw new XPathException(ErrorCodes.XQST0070, "Namespace URI '" + uri + "' must be bound to the 'xml' prefix");
        }

        final String prevURI = staticNamespaces.get(prefix);

        //This prefix was not bound
        if (prevURI == null) {

            if (uri.isEmpty()) {
                //Nothing to bind

                //TODO : check the specs : unbinding an NS which is not already bound may be disallowed.
                LOG.warn("Unbinding unbound prefix '" + prefix + "'");
            } else {
                //Bind it
                staticNamespaces.put(prefix, uri);
                staticPrefixes.put(uri, prefix);
            }

        } else {
            //This prefix was bound

            //Unbind it
            if (uri.isEmpty()) {
                // if an empty namespace is specified,
                // remove any existing mapping for this namespace
                //TODO : improve, since XML_NS can't be unbound
                staticPrefixes.remove(uri);
                staticNamespaces.remove(prefix);
                return;
            }

            //those prefixes can be rebound to different URIs
            if (("xs".equals(prefix) && Namespaces.SCHEMA_NS.equals(prevURI))
                    || ("xsi".equals(prefix) && Namespaces.SCHEMA_INSTANCE_NS.equals(prevURI))
                    || ("xdt".equals(prefix) && Namespaces.XPATH_DATATYPES_NS.equals(prevURI))
                    || ("fn".equals(prefix) && Namespaces.XPATH_FUNCTIONS_NS.equals(prevURI))
                    || ("math".equals(prefix)) && Namespaces.XPATH_FUNCTIONS_MATH_NS.equals(prevURI)
                    || ("local".equals(prefix) && Namespaces.XQUERY_LOCAL_NS.equals(prevURI))) {

                staticPrefixes.remove(prevURI);
                staticNamespaces.remove(prefix);

                staticNamespaces.put(prefix, uri);
                staticPrefixes.put(uri, prefix);

            } else {

                //Forbids rebinding the *same* prefix in a *different* namespace in this *same* context
                if (!uri.equals(prevURI)) {
                    throw new XPathException(ErrorCodes.XQST0033, "Cannot bind prefix '" + prefix + "' to '" + uri + "' it is already bound to '" + prevURI + "'");
                }
            }
        }
    }

    @Override
    public void declareNamespaces(final Map<String, String> namespaceMap) {
        for (final Map.Entry<String, String> entry : namespaceMap.entrySet()) {
            String prefix = entry.getKey();
            String uri = entry.getValue();

            if (prefix == null) {
                prefix = "";
            }

            if (uri == null) {
                uri = "";
            }
            staticNamespaces.put(prefix, uri);
            staticPrefixes.put(uri, prefix);
        }
    }

    @Override
    public void removeNamespace(final String uri) {
        staticPrefixes.remove(uri);

        for (final Iterator<String> i = staticNamespaces.values().iterator(); i.hasNext(); ) {
            if (i.next().equals(uri)) {
                i.remove();
                return;
            }
        }
        inScopePrefixes.remove(uri);

        if (inScopeNamespaces != null) {
            for (final Iterator<String> i = inScopeNamespaces.values().iterator(); i.hasNext(); ) {
                if (i.next().equals(uri)) {
                    i.remove();
                    return;
                }
            }
        }
        inheritedInScopePrefixes.remove(uri);

        if (inheritedInScopeNamespaces != null) {
            for (final Iterator<String> i = inheritedInScopeNamespaces.values().iterator(); i.hasNext(); ) {
                if (i.next().equals(uri)) {
                    i.remove();
                    return;
                }
            }
        }
    }

    @Override
    public void declareInScopeNamespace(final String prefix, final String uri) {
        if (prefix == null || uri == null) {
            throw new IllegalArgumentException("null argument passed to declareNamespace");
        }

        //Activate the namespace by removing it from the inherited namespaces
        if (inheritedInScopePrefixes.containsKey(getURIForPrefix(prefix))) {
            inheritedInScopePrefixes.remove(uri);
        }

        inheritedInScopeNamespaces.remove(prefix);

        inScopePrefixes.put(uri, prefix);
        inScopeNamespaces.put(prefix, uri);
    }

    @Override
    public String getInScopeNamespace(final String prefix) {
        return inScopeNamespaces == null ? null : inScopeNamespaces.get(prefix);
    }

    @Override
    public String getInScopePrefix(final String uri) {
        return inScopePrefixes == null ? null : inScopePrefixes.get(uri);
    }

    public Map<String, String> getInScopePrefixes() {
        return inScopePrefixes;
    }

    @Override
    public String getInheritedNamespace(final String prefix) {
        return inheritedInScopeNamespaces == null ? null : inheritedInScopeNamespaces.get(prefix);
    }

    @Override
    public String getInheritedPrefix(final String uri) {
        return inheritedInScopePrefixes == null ? null : inheritedInScopePrefixes.get(uri);
    }

    @Override
    public String getURIForPrefix(final String prefix) {
        // try in-scope namespace declarations
        String uri = (inScopeNamespaces == null) ? null : inScopeNamespaces.get(prefix);

        if (uri != null) {
            return uri;
        }

        if (inheritNamespaces) {
            uri = (inheritedInScopeNamespaces == null) ? null : inheritedInScopeNamespaces.get(prefix);

            if (uri != null) {
                return uri;
            }
        }
        return staticNamespaces.get(prefix);
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

    @Override
    public String getPrefixForURI(final String uri) {
        String prefix = (inScopePrefixes == null) ? null : inScopePrefixes.get(uri);

        if (prefix != null) {
            return prefix;
        }

        if (inheritNamespaces) {
            prefix = (inheritedInScopePrefixes == null) ? null : inheritedInScopePrefixes.get(uri);

            if (prefix != null) {
                return prefix;
            }
        }
        return staticPrefixes.get(uri);
    }

    @Override
    public String getDefaultFunctionNamespace() {
        return defaultFunctionNamespace;
    }

    @Override
    public void setDefaultFunctionNamespace(final String uri) throws XPathException {
        //Not sure for the 2nd clause : eXist-db forces the function NS as default.
        if ((defaultFunctionNamespace != null) && !defaultFunctionNamespace.equals(Function.BUILTIN_FUNCTION_NS) && !defaultFunctionNamespace.equals(uri)) {
            throw new XPathException(ErrorCodes.XQST0066, "Default function namespace is already set to: '" + defaultFunctionNamespace + "'");
        }
        defaultFunctionNamespace = uri;
    }

    @Override
    public String getDefaultElementNamespaceSchema() throws XPathException {
        return defaultElementNamespaceSchema.getStringValue();
    }

    @Override
    public void setDefaultElementNamespaceSchema(final String uri) throws XPathException {
        // eXist forces the empty element NS as default.
        if (!defaultElementNamespaceSchema.equals(AnyURIValue.EMPTY_URI)) {
            throw new XPathException(ErrorCodes.XQST0066, "Default function namespace schema is already set to: '" + defaultElementNamespaceSchema.getStringValue() + "'");
        }
        defaultElementNamespaceSchema = new AnyURIValue(uri);
    }

    @Override
    public String getDefaultElementNamespace() throws XPathException {
        return defaultElementNamespace.getStringValue();
    }

    @Override
    public void setDefaultElementNamespace(final String uri, @Nullable final String schema) throws XPathException {
        // eXist forces the empty element NS as default.
        if (!defaultElementNamespace.equals(AnyURIValue.EMPTY_URI)) {
            throw new XPathException(ErrorCodes.XQST0066,
                    "Default element namespace is already set to: '" + defaultElementNamespace.getStringValue() + "'");
        }
        defaultElementNamespace = new AnyURIValue(uri);

        if (schema != null) {
            defaultElementNamespaceSchema = new AnyURIValue(schema);
        }
    }

    @Override
    public void setDefaultCollation(final String uri) throws XPathException {
        if (uri.equals(Collations.UNICODE_CODEPOINT_COLLATION_URI) || uri.equals(Collations.CODEPOINT_SHORT)) {
            defaultCollation = Collations.UNICODE_CODEPOINT_COLLATION_URI;
            defaultCollator = null;
        }

        final URI uriTest;
        try {
            uriTest = new URI(uri);
        } catch (final URISyntaxException e) {
            throw new XPathException(ErrorCodes.XQST0038, "Unknown collation : '" + uri + "'");
        }

        if (uri.startsWith(Collations.EXIST_COLLATION_URI) || uri.charAt(0) == '?' || uriTest.isAbsolute()) {
            defaultCollator = Collations.getCollationFromURI(uri);
            defaultCollation = uri;
        } else {
            String absUri = getBaseURI().getStringValue() + uri;
            defaultCollator = Collations.getCollationFromURI(absUri);
            defaultCollation = absUri;
        }
    }

    @Override
    public String getDefaultCollation() {
        return defaultCollation;
    }

    @Override
    public Collator getCollator(final String uri) throws XPathException {
        if (uri == null) {
            return defaultCollator;
        }
        return Collations.getCollationFromURI(uri);
    }

    @Override
    public Collator getDefaultCollator() {
        return defaultCollator;
    }

    @Override
    public void setStaticallyKnownDocuments(final XmldbURI[] docs) {
        staticDocumentPaths = docs;
    }

    @Override
    public void setStaticallyKnownDocuments(final DocumentSet set) {
        staticDocuments = set;
    }

    @Override
    public void setCalendar(final XMLGregorianCalendar newCalendar) {
        this.calendar = (XMLGregorianCalendar) newCalendar.clone();
    }

    @Override
    public void setTimeZone(final TimeZone newTimeZone) {
        this.implicitTimeZone = newTimeZone;
    }

    @Override
    public XMLGregorianCalendar getCalendar() {
        //TODO : we might prefer to return null
        if (calendar == null) {
            try {
                //Initialize to current dateTime
                calendar = DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar());
            } catch (final DatatypeConfigurationException e) {
                LOG.error(e.getMessage(), e);
            }
        }

        //That's how we ensure stability of that static context function
        return calendar;
    }

    @Override
    public TimeZone getImplicitTimeZone() {
        if (implicitTimeZone == null) {
            implicitTimeZone = TimeZone.getDefault();

            if (implicitTimeZone.inDaylightTime(new Date())) {
                implicitTimeZone.setRawOffset(implicitTimeZone.getRawOffset() + implicitTimeZone.getDSTSavings());
            }
        }

        //That's how we ensure stability of that static context function
        return this.implicitTimeZone;
    }

    @Override
    public DocumentSet getStaticallyKnownDocuments() throws XPathException {
        if (staticDocuments != null) {

            // the document set has already been built, return it
            return staticDocuments;
        }

        if (protectedDocuments != null) {
            staticDocuments = protectedDocuments.toDocumentSet();
            return staticDocuments;
        }
        final MutableDocumentSet ndocs = new DefaultDocumentSet(40);

        if (staticDocumentPaths == null) {

            // no path defined: return all documents in the db
            try {
                getBroker().getAllXMLResources(ndocs);
            } catch (final PermissionDeniedException e) {
                LOG.warn(e);
                throw new XPathException("Permission denied to read resource all resources: " + e.getMessage(), e);
            }
        } else {
            for (final XmldbURI staticDocumentPath : staticDocumentPaths) {

                try {
                    final Collection collection = getBroker().getCollection(staticDocumentPath);

                    if (collection != null) {
                        collection.allDocs(getBroker(), ndocs, true);
                    } else {
                        DocumentImpl doc = null;
                        try {
                            doc = getBroker().getXMLResource(staticDocumentPath, LockMode.READ_LOCK);
                            if (doc != null) {

                                if (doc.getPermissions().validate(
                                        getBroker().getCurrentSubject(), Permission.READ)) {

                                    ndocs.add(doc);
                                }
                            }
                        } finally {
                            if (doc != null) {
                                doc.getUpdateLock().release(LockMode.READ_LOCK);
                            }
                        }
                    }
                } catch (final PermissionDeniedException e) {
                    LOG.warn("Permission denied to read resource " + staticDocumentPath + ". Skipping it.");
                }
            }
        }
        staticDocuments = ndocs;
        return staticDocuments;
    }

    public DocumentSet getStaticDocs() {
        return staticDocuments;
    }

    @Override
    public ExtendedXMLStreamReader getXMLStreamReader(final NodeValue nv) throws XMLStreamException, IOException {
        final ExtendedXMLStreamReader reader;
        if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
            final NodeImpl node = (NodeImpl) nv;
            final org.exist.dom.memtree.DocumentImpl ownerDoc = node.getNodeType() == Node.DOCUMENT_NODE ? (org.exist.dom.memtree.DocumentImpl) node : node.getOwnerDocument();
            reader = new InMemoryXMLStreamReader(ownerDoc, ownerDoc);
        } else {
            final NodeProxy proxy = (NodeProxy) nv;
            reader = getBroker().newXMLStreamReader(new NodeProxy(proxy.getOwnerDocument(), NodeId.DOCUMENT_NODE, proxy.getOwnerDocument().getFirstChildAddress()), false);
        }
        return reader;
    }

    @Override
    public void setProtectedDocs(final LockedDocumentMap map) {
        this.protectedDocuments = map;
    }

    @Override
    public LockedDocumentMap getProtectedDocs() {
        return this.protectedDocuments;
    }

    @Override
    public boolean inProtectedMode() {
        return protectedDocuments != null;
    }

    @Override
    public boolean lockDocumentsOnLoad() {
        return false;
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


    @Override
    public void addLockedDocument(final DocumentImpl doc) {
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

    @Override
    public void setShared(final boolean shared) {
        isShared = shared;
    }

    @Override
    public boolean isShared() {
        return isShared;
    }

    @Override
    public void addModifiedDoc(final DocumentImpl document) {
        if (modifiedDocuments == null) {
            modifiedDocuments = new DefaultDocumentSet();
        }
        modifiedDocuments.add(document);
    }

    @Override
    public void reset() {
        reset(false);
    }

    @Override
    public void reset(final boolean keepGlobals) {
        setRealUser(null);

        if (this.pushedUserFromHttpSession) {
            try {
                getBroker().popSubject();
            } finally {
                this.pushedUserFromHttpSession = false;
            }
        }

        if (modifiedDocuments != null) {
            try {
                Modification.checkFragmentation(this, modifiedDocuments);
            } catch (final EXistException e) {
                LOG.warn("Error while checking modified documents: " + e.getMessage(), e);
            }
            modifiedDocuments = null;
        }

        calendar = null;
        implicitTimeZone = null;

        resetDocumentBuilder();

        contextSequence = null;
        contextItem = Sequence.EMPTY_SEQUENCE;

        if (!keepGlobals) {
            // do not reset the statically known documents
            staticDocumentPaths = null;
            staticDocuments = null;
        }

        if (!isShared) {
            lastVar = null;
        }

        // clear inline functions using closures
        closures.forEach(func -> func.setClosureVariables(null));
        closures.clear();

        fragmentStack = new ArrayDeque<>();
        callStack.clear();
        protectedDocuments = null;

        if (!keepGlobals) {
            globalVariables.clear();
        }

        if (dynamicOptions != null) {
            dynamicOptions.clear(); //clear any dynamic options
        }

        if (!isShared) {
            watchdog.reset();
        }

        for (final Module module : allModules.values()) {
            module.reset(this, keepGlobals);
        }

        if (!keepGlobals) {
            mappedModules.clear();
        }

        savedState.restore();

        //remove the context-vars, subsequent execution of the query
        //may generate different values for the vars based on the
        //content of the db
        if (!keepGlobals) {
            XQueryContextVars.clear();
        }

        attributes.clear();

        clearUpdateListeners();

        profiler.reset();

        if (!keepGlobals) {
            httpContext = null;
        }

        analyzed = false;
    }

    @Override
    public boolean stripWhitespace() {
        return stripWhitespace;
    }

    @Override
    public void setStripWhitespace(final boolean strip) {
        this.stripWhitespace = strip;
    }

    @Override
    public boolean preserveNamespaces() {
        return preserveNamespaces;
    }

    @Override
    public void setPreserveNamespaces(final boolean preserve) {
        this.preserveNamespaces = preserve;
    }

    @Override
    public boolean inheritNamespaces() {
        return inheritNamespaces;
    }

    @Override
    public void setInheritNamespaces(final boolean inherit) {
        this.inheritNamespaces = inherit;
    }

    @Override
    public boolean orderEmptyGreatest() {
        return orderEmptyGreatest;
    }

    @Override
    public void setOrderEmptyGreatest(final boolean order) {
        this.orderEmptyGreatest = order;
    }

    @Override
    public Iterator<Module> getModules() {
        return modules.values().iterator();
    }

    @Override
    public Iterator<Module> getRootModules() {
        return getAllModules();
    }

    @Override
    public Iterator<Module> getAllModules() {
        return allModules.values().iterator();
    }

    @Override
    @Nullable
    public Module getModule(final String namespaceURI) {
        return modules.get(namespaceURI);
    }

    @Override
    public Module getRootModule(final String namespaceURI) {
        return allModules.get(namespaceURI);
    }

    @Override
    public void setModule(final String namespaceURI, final Module module) {
        if (module == null) {
            modules.remove(namespaceURI); // unbind the module
        } else {
            modules.put(namespaceURI, module);
        }
        setRootModule(namespaceURI, module);
    }

    protected void setRootModule(final String namespaceURI, final Module module) {
        if (module == null) {
            allModules.remove(namespaceURI); // unbind the module
            return;
        }

        if (allModules.get(namespaceURI) != module) {
            setModulesChanged();
        }
        allModules.put(namespaceURI, module);
    }

    protected void setModulesChanged() {
        this.modulesChanged = true;
    }

    @Override
    public boolean checkModulesValid() {
        for (final Module module : allModules.values()) {
            if (!module.isInternalModule()) {
                if (!((ExternalModule) module).moduleIsValid(getBroker())) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Module with URI " + module.getNamespaceURI() + " has changed and needs to be reloaded");
                    }
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void analyzeAndOptimizeIfModulesChanged(final Expression expr) throws XPathException {
        if (analyzed) {
            return;
        }
        analyzed = true;
        for (final Module module : expr.getContext().modules.values()) {
            if (!module.isInternalModule()) {
                final Expression root = ((ExternalModule) module).getRootExpression();
                ((ExternalModule) module).getContext().analyzeAndOptimizeIfModulesChanged(root);
            }
        }
        expr.analyze(new AnalyzeContextInfo());

        if (optimizationsEnabled()) {
            final Optimizer optimizer = new Optimizer(this);
            expr.accept(optimizer);

            if (optimizer.hasOptimized()) {
                reset(true);
                expr.resetState(true);
                expr.analyze(new AnalyzeContextInfo());
            }
        }
        modulesChanged = false;
    }

    @Override
    @Nullable
    public Module loadBuiltInModule(final String namespaceURI, final String moduleClass) {
        Module module = null;
        if (namespaceURI != null) {
            module = getModule(namespaceURI);
        }

        if (module != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("module " + namespaceURI + " is already present");
            }
            return module;
        }
        return initBuiltInModule(namespaceURI, moduleClass);
    }

    @SuppressWarnings("unchecked")
    Module initBuiltInModule(final String namespaceURI, final String moduleClass) {
        Module module = null;
        try {
            // lookup the class
            final ClassLoader existClassLoader = getBroker().getBrokerPool().getClassLoader();
            final Class<?> mClass = Class.forName(moduleClass, false, existClassLoader);

            if (!(Module.class.isAssignableFrom(mClass))) {
                LOG.info("failed to load module. " + moduleClass + " is not an instance of org.exist.xquery.Module.");
                return null;
            }
            //instantiateModule( namespaceURI, (Class<Module>)mClass );
            // INOTE: expathrepo
            module = instantiateModule(namespaceURI, (Class<Module>) mClass, (Map<String, Map<String, List<? extends Object>>>) getBroker().getConfiguration().getProperty(PROPERTY_MODULE_PARAMETERS));
            if (LOG.isDebugEnabled()) {
                LOG.debug("module " + module.getNamespaceURI() + " loaded successfully.");
            }
        } catch (final ClassNotFoundException e) {
            LOG.warn("module class " + moduleClass + " not found. Skipping...");
        }
        return module;
    }

    @SuppressWarnings("unchecked")
    private Module instantiateModule(final String namespaceURI, final Class<Module> mClazz,
                                     final Map<String, Map<String, List<? extends Object>>> moduleParameters) {
        Module module = null;
        try {
            try {
                // attempt for a constructor that takes 1 argument
                final Constructor<Module> cstr1 = mClazz.getConstructor(Map.class);
                module = cstr1.newInstance(moduleParameters.get(namespaceURI));

            } catch (final NoSuchMethodException nsme) {
                // attempt for a constructor that takes 0 arguments
                module = mClazz.newInstance();
            }

            if (namespaceURI != null && !module.getNamespaceURI().equals(namespaceURI)) {
                LOG.warn("the module declares a different namespace URI. Expected: " + namespaceURI + " found: " + module.getNamespaceURI());
                return null;
            }

            if (getPrefixForURI(module.getNamespaceURI()) == null && !module.getDefaultPrefix().isEmpty()) {
                declareNamespace(module.getDefaultPrefix(), module.getNamespaceURI());
            }

            modules.put(module.getNamespaceURI(), module);
            allModules.put(module.getNamespaceURI(), module);

            if (module instanceof InternalModule) {
                ((InternalModule) module).prepare(this);
            }
        } catch (final Throwable e) {
            if (e instanceof InterruptedException) {
                // NOTE: must set interrupted flag
                Thread.currentThread().interrupt();
            }

            LOG.warn("error while instantiating module class " + mClazz.getName(), e);
        }

        return module;
    }

    @Override
    public void declareFunction(final UserDefinedFunction function) throws XPathException {
        // TODO: redeclaring functions should be forbidden. however, throwing an
        // exception will currently break util:eval.

        final QName name = function.getSignature().getName();

        if (XML_NS.equals(name.getNamespaceURI())) {
            throw new XPathException(function, ErrorCodes.XQST0045, "Function '" + name + "' is in the forbidden namespace '" + XML_NS + "'");
        }

        if (Namespaces.SCHEMA_NS.equals(name.getNamespaceURI())) {
            throw new XPathException(function, ErrorCodes.XQST0045, "Function '" + name + "' is in the forbidden namespace '" + Namespaces.SCHEMA_NS + "'");
        }

        if (Namespaces.SCHEMA_INSTANCE_NS.equals(name.getNamespaceURI())) {
            throw new XPathException(function, ErrorCodes.XQST0045, "Function '" + name + "' is in the forbidden namespace '" + Namespaces.SCHEMA_INSTANCE_NS + "'");
        }

        if (Namespaces.XPATH_FUNCTIONS_NS.equals(name.getNamespaceURI())) {
            throw new XPathException(function, ErrorCodes.XQST0045, "Function '" + name + "' is in the forbidden namespace '" + Namespaces.XPATH_FUNCTIONS_NS + "'");
        }

        if (name.getNamespaceURI().isEmpty()) {
            throw new XPathException(function, ErrorCodes.XQST0060, "Every declared function name must have a non-null namespace URI, but function '" + name + "' does not meet this requirement.");
        }

        declaredFunctions.put(function.getSignature().getFunctionId(), function);
//      if (declaredFunctions.get(function.getSignature().getFunctionId()) == null)
//              declaredFunctions.put(function.getSignature().getFunctionId(), function);
//      else
//          throw new XPathException("XQST0034: function " + function.getName() + " is already defined with the same arity");
    }

    @Override
    @Nullable
    public UserDefinedFunction resolveFunction(final QName name, final int argCount) throws XPathException {
        final FunctionId id = new FunctionId(name, argCount);
        return declaredFunctions.get(id);
    }

    @Override
    public Iterator<FunctionSignature> getSignaturesForFunction(final QName name) {
        final ArrayList<FunctionSignature> signatures = new ArrayList<>(2);

        for (final UserDefinedFunction func : declaredFunctions.values()) {
            if (func.getName().equals(name)) {
                signatures.add(func.getSignature());
            }
        }
        return signatures.iterator();
    }

    @Override
    public Iterator<UserDefinedFunction> localFunctions() {
        return declaredFunctions.values().iterator();
    }

    @Override
    public LocalVariable declareVariableBinding(final LocalVariable var) throws XPathException {
        if (lastVar == null) {
            lastVar = var;
        } else {
            lastVar.addAfter(var);
            lastVar = var;
        }
        var.setStackPosition(getCurrentStackSize());
        return var;
    }

    @Override
    public Variable declareGlobalVariable(final Variable var) {
        globalVariables.put(var.getQName(), var);
        var.setStackPosition(getCurrentStackSize());
        return var;
    }

    @Override
    public void undeclareGlobalVariable(final QName name) {
        globalVariables.remove(name);
    }

    @Override
    public Variable declareVariable(final String qname, final Object value) throws XPathException {
        try {
            return declareVariable(QName.parse(this, qname, null), value);
        } catch (final QName.IllegalQNameException e) {
            throw new XPathException(ErrorCodes.XPST0081, "No namespace defined for prefix: " + qname);
        }
    }

    @Override
    public Variable declareVariable(final QName qn, final Object value) throws XPathException {
        Variable var;
        final Module module = getModule(qn.getNamespaceURI());

        if (module != null) {
            var = module.declareVariable(qn, value);
            return var;
        }
        final Sequence val = XPathUtil.javaObjectToXPath(value, this);
        var = globalVariables.get(qn);

        if (var == null) {
            var = new VariableImpl(qn);
            globalVariables.put(qn, var);
        }

        if (var.getSequenceType() != null) {
            int actualCardinality;

            if (val.isEmpty()) {
                actualCardinality = Cardinality.EMPTY;
            } else if (val.hasMany()) {
                actualCardinality = Cardinality.MANY;
            } else {
                actualCardinality = Cardinality.ONE;
            }

            //Type.EMPTY is *not* a subtype of other types ; checking cardinality first
            if (!Cardinality.checkCardinality(var.getSequenceType().getCardinality(), actualCardinality)) {
                throw new XPathException("XPTY0004: Invalid cardinality for variable $" + var.getQName() + ". Expected " + Cardinality.getDescription(var.getSequenceType().getCardinality()) + ", got " + Cardinality.getDescription(actualCardinality));
            }

            //TODO : ignore nodes right now ; they are returned as xs:untypedAtomicType
            if (!Type.subTypeOf(var.getSequenceType().getPrimaryType(), Type.NODE)) {
                if (!val.isEmpty() && !Type.subTypeOf(val.getItemType(), var.getSequenceType().getPrimaryType())) {
                    throw new XPathException("XPTY0004: Invalid type for variable $" + var.getQName() + ". Expected " + Type.getTypeName(var.getSequenceType().getPrimaryType()) + ", got " + Type.getTypeName(val.getItemType()));
                }

                //Here is an attempt to process the nodes correctly
            } else {

                //Same as above : we probably may factorize
                if (!val.isEmpty() && !Type.subTypeOf(val.getItemType(), var.getSequenceType().getPrimaryType())) {
                    throw new XPathException("XPTY0004: Invalid type for variable $" + var.getQName() + ". Expected " + Type.getTypeName(var.getSequenceType().getPrimaryType()) + ", got " + Type.getTypeName(val.getItemType()));
                }

            }
        }

        //TODO : should we allow global variable *re*declaration ?
        var.setValue(val);
        return var;
    }

    @Override
    public Variable resolveVariable(final String name) throws XPathException {
        try {
            final QName qn = QName.parse(this, name, null);
            return resolveVariable(qn);
        } catch (final QName.IllegalQNameException e) {
            throw new XPathException(ErrorCodes.XPST0081, "No namespace defined for prefix " + name);
        }
    }

    @Override
    public Variable resolveVariable(final QName qname) throws XPathException {
        // check if the variable is declared local
        Variable var = resolveLocalVariable(qname);

        // check if the variable is declared in a module
        if (var == null) {
            final Module module = getModule(qname.getNamespaceURI());

            if (module != null) {
                var = module.resolveVariable(qname);
            }
        }

        // check if the variable is declared global
        if (var == null) {
            var = globalVariables.get(qname);
        }

        //if (var == null)
        //  throw new XPathException("variable $" + qname + " is not bound");
        return var;
    }

    Variable resolveGlobalVariable(final QName qname) {
        return globalVariables.get(qname);
    }

    protected Variable resolveLocalVariable(final QName qname) throws XPathException {
        final LocalVariable end = contextStack.peek();
        for (LocalVariable var = lastVar; var != null; var = var.before) {
            if (var == end) {
                return null;
            }
            if (qname.equals(var.getQName())) {
                return var;
            }
        }
        return null;
    }

    @Override
    public boolean isVarDeclared(final QName qname) {
        final Module module = getModule(qname.getNamespaceURI());
        if (module != null) {
            if (module.isVarDeclared(qname)) {
                return true;
            }
        }
        return globalVariables.get(qname) != null;
    }

    @Override
    public Map<QName, Variable> getVariables() {
        final Map<QName, Variable> variables = new HashMap<>(globalVariables);
        LocalVariable end = contextStack.peek();
        for (LocalVariable var = lastVar; var != null; var = var.before) {
            if (var == end) {
                break;
            }
            variables.put(var.getQName(), var);
        }
        return variables;
    }

    @Override
    public Map<QName, Variable> getLocalVariables() {
        final Map<QName, Variable> variables = new HashMap<>();
        LocalVariable end = contextStack.peek();
        for (LocalVariable var = lastVar; var != null; var = var.before) {
            if (var == end) {
                break;
            }
            variables.put(var.getQName(), var);
        }
        return variables;
    }

    /**
     * Return a copy of all currently visible local variables.
     * Used by {@link InlineFunction} to implement closures.
     *
     * @return currently visible local variables as a stack
     */
    public List<ClosureVariable> getLocalStack() {
        List<ClosureVariable> closure = null;
        final LocalVariable end = contextStack.peek();
        for (LocalVariable var = lastVar; var != null; var = var.before) {

            if (var == end) {
                break;
            }

            if (closure == null) {
                closure = new ArrayList<>(6);
            }
            closure.add(new ClosureVariable(var));
        }

        return closure;
    }

    @Override
    public Map<QName, Variable> getGlobalVariables() {
        return new HashMap<>(globalVariables);
    }

    /**
     * Restore a saved stack of local variables. Used to implement closures.
     *
     * @param stack the stack of local variables
     * @throws XPathException if the stack cannot be restored
     */
    public void restoreStack(final List<ClosureVariable> stack) throws XPathException {
        for (int i = stack.size() - 1; i > -1; i--) {
            declareVariableBinding(new ClosureVariable(stack.get(i)));
        }
    }

    @Override
    public void setBackwardsCompatibility(boolean backwardsCompatible) {
        this.backwardsCompatible = backwardsCompatible;
    }

    @Override
    public boolean isBackwardsCompatible() {
        return this.backwardsCompatible;
    }

    @Override
    public boolean isRaiseErrorOnFailedRetrieval() {
        return raiseErrorOnFailedRetrieval;
    }

    public Database getDatabase() {
        return db;
    }

    @Override
    public DBBroker getBroker() {
        return db.getActiveBroker();
    }

    /**
     * Get the user which executes the current query.
     *
     * @return user
     * @deprecated Use {@link #getSubject()}.
     */
    @Deprecated
    public Subject getUser() {
        return getSubject();
    }

    @Override
    public Subject getSubject() {
        return getBroker().getCurrentSubject();
    }

    /**
     * If there is a HTTP Session, and a User has been stored in the session then this will return the user object from the session.
     *
     * @return The user or null if there is no session or no user
     */
    Subject getUserFromHttpSession() {
        final Optional<RequestWrapper> maybeRequest = Optional.ofNullable(getHttpContext())
                .map(HttpContext::getRequest);

        if (maybeRequest.isPresent()) {
            final RequestWrapper request = maybeRequest.get();
            final Object user = request.getAttribute(HTTP_REQ_ATTR_USER);
            final Object passAttr = request.getAttribute(HTTP_REQ_ATTR_PASS);
            if (user != null) {
                final String password = passAttr == null ? null : passAttr.toString();
                try {
                    return getBroker().getBrokerPool().getSecurityManager().authenticate(user.toString(), password);
                } catch (final AuthenticationException e) {
                    LOG.error("User can not be authenticated: " + user.toString());
                }
            } else {
                final Optional<SessionWrapper> maybeSession = Optional.ofNullable(getHttpContext())
                        .map(HttpContext::getSession);
                if (maybeSession.isPresent()) {
                    return (Subject) maybeSession.get().getAttribute(HTTP_SESSIONVAR_XMLDB_USER);
                }
            }
        }

        return null;
    }

    /**
     * The builder used for creating in-memory document fragments.
     */
    private MemTreeBuilder documentBuilder = null;

    @Override
    public MemTreeBuilder getDocumentBuilder() {
        if (documentBuilder == null) {
            documentBuilder = new MemTreeBuilder(this);
            documentBuilder.startDocument();
        }
        return documentBuilder;
    }

    @Override
    public MemTreeBuilder getDocumentBuilder(final boolean explicitCreation) {
        if (documentBuilder == null) {
            documentBuilder = new MemTreeBuilder(this);
            documentBuilder.startDocument(explicitCreation);
        }
        return documentBuilder;
    }

    private void resetDocumentBuilder() {
        this.documentBuilder = null;
    }

    private void setDocumentBuilder(final MemTreeBuilder documentBuilder) {
        this.documentBuilder = documentBuilder;
    }

    @Override
    public NamePool getSharedNamePool() {
        if (sharedNamePool == null) {
            sharedNamePool = new NamePool();
        }
        return sharedNamePool;
    }

    @Override
    public XQueryContext getContext() {
        return null;
    }

    @Override
    public void prologEnter(final Expression expr) {
        if (debuggeeJoint != null) {
            debuggeeJoint.prologEnter(expr);
        }
    }

    @Override
    public void expressionStart(final Expression expr) throws TerminatedException {
        if (debuggeeJoint != null) {
            debuggeeJoint.expressionStart(expr);
        }
    }

    @Override
    public void expressionEnd(final Expression expr) {
        if (debuggeeJoint != null) {
            debuggeeJoint.expressionEnd(expr);
        }
    }

    @Override
    public void stackEnter(final Expression expr) throws TerminatedException {
        if (debuggeeJoint != null) {
            debuggeeJoint.stackEnter(expr);
        }
    }

    @Override
    public void stackLeave(final Expression expr) {
        if (debuggeeJoint != null) {
            debuggeeJoint.stackLeave(expr);
        }
    }

    @Override
    public void proceed() throws TerminatedException {
        getWatchDog().proceed(null);
    }

    @Override
    public void proceed(final Expression expr) throws TerminatedException {
        getWatchDog().proceed(expr);
    }

    @Override
    public void proceed(final Expression expr, final MemTreeBuilder builder) throws TerminatedException {
        getWatchDog().proceed(expr, builder);
    }

    @Override
    public void setWatchDog(final XQueryWatchDog watchdog) {
        this.watchdog = watchdog;
    }

    @Override
    public XQueryWatchDog getWatchDog() {
        return watchdog;
    }

    @Override
    public void pushDocumentContext() {
        fragmentStack.push(getDocumentBuilder());
        resetDocumentBuilder();
    }

    @Override
    public void popDocumentContext() {
        if (!fragmentStack.isEmpty()) {
            setDocumentBuilder(fragmentStack.pop());
        }
    }

    @Override
    public void setBaseURI(final AnyURIValue uri) {
        setBaseURI(uri, false);
    }

    @Override
    public void setBaseURI(final AnyURIValue uri, final boolean setInProlog) {
        if (baseURISetInProlog) {
            return;
        }

        if (uri == null) {
            baseURI = AnyURIValue.EMPTY_URI;
        }
        baseURI = uri;
        baseURISetInProlog = setInProlog;
    }

    @Override
    public void setModuleLoadPath(final String path) {
        this.moduleLoadPath = path;
    }

    @Override
    public String getModuleLoadPath() {
        return moduleLoadPath;
    }

    @Override
    public boolean isBaseURIDeclared() {
        return baseURI != null && !baseURI.equals(AnyURIValue.EMPTY_URI);
    }

    @Override
    public AnyURIValue getBaseURI() throws XPathException {
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
//        if ((baseURI == null) || baseURI.equals(AnyURIValue.EMPTY_URI)) {
//            //throw new XPathException(ErrorCodes.XPST0001, "Base URI of the static context  has not been assigned a value.");
//            // We catch and resolve this to the XmlDbURI.ROOT_COLLECTION_URI
//            // at least in DocumentImpl so maybe we should do it here./ljo
//        }
        return baseURI;
    }

    @Override
    public void setContextSequencePosition(final int pos, final Sequence sequence) {
        contextPosition = pos;
        contextSequence = sequence;
    }

    @Override
    public int getContextPosition() {
        return contextPosition;
    }

    @Override
    public Sequence getContextSequence() {
        return contextSequence;
    }

    @Override
    public void pushInScopeNamespaces() {
        pushInScopeNamespaces(true);
    }

    @Override
    public void pushInScopeNamespaces(final boolean inherit) {
        //TODO : push into an inheritedInScopeNamespaces HashMap... and return an empty HashMap
        final Map<String, String> m = new HashMap<>(inScopeNamespaces);
        final Map<String, String> p = new HashMap<>(inScopePrefixes);
        namespaceStack.push(inheritedInScopeNamespaces);
        namespaceStack.push(inheritedInScopePrefixes);
        namespaceStack.push(inScopeNamespaces);
        namespaceStack.push(inScopePrefixes);

        //Current namespaces now become inherited just like the previous inherited ones
        if (inherit) {
            inheritedInScopeNamespaces = new HashMap<>(inheritedInScopeNamespaces);
            inheritedInScopeNamespaces.putAll(m);
            inheritedInScopePrefixes = new HashMap<>(inheritedInScopePrefixes);
            inheritedInScopePrefixes.putAll(p);
        } else {
            inheritedInScopeNamespaces = new HashMap<>();
            inheritedInScopePrefixes = new HashMap<>();
        }

        //TODO : consider dynamic instanciation
        inScopeNamespaces = new HashMap<>();
        inScopePrefixes = new HashMap<>();
    }

    @Override
    public void popInScopeNamespaces() {
        inScopePrefixes = namespaceStack.pop();
        inScopeNamespaces = namespaceStack.pop();
        inheritedInScopePrefixes = namespaceStack.pop();
        inheritedInScopeNamespaces = namespaceStack.pop();
    }

    @Override
    public void pushNamespaceContext() {
        final Map<String, String> m = new HashMap<>(staticNamespaces);
        final Map<String, String> p = new HashMap<>(staticPrefixes);
        namespaceStack.push(staticNamespaces);
        namespaceStack.push(staticPrefixes);
        staticNamespaces = m;
        staticPrefixes = p;
    }

    @Override
    public void popNamespaceContext() {
        staticPrefixes = namespaceStack.pop();
        staticNamespaces = namespaceStack.pop();
    }

    @Override
    public LocalVariable markLocalVariables(final boolean newContext) {
        if (newContext) {
            if (lastVar == null) {
                lastVar = new LocalVariable(QName.EMPTY_QNAME);
            }
            contextStack.push(lastVar);
        }
        variableStackSize++;
        return lastVar;
    }

    @Override
    public void popLocalVariables(@Nullable final LocalVariable var) {
        popLocalVariables(var, null);
    }

    /**
     * Restore the local variable stack to the position marked by variable var.
     *
     * @param var       only clear variables after this variable, or null
     * @param resultSeq the result sequence
     */
    public void popLocalVariables(@Nullable final LocalVariable var, @Nullable final Sequence resultSeq) {
        if (var != null) {
            // clear all variables registered after var. they should be out of scope.
            LocalVariable outOfScope = var.after;
            while (outOfScope != null) {
                if (outOfScope != var && !outOfScope.isClosureVar()) {
                    outOfScope.destroy(this, resultSeq);
                }
                outOfScope = outOfScope.after;
            }
            // reset the stack
            var.after = null;

            if (!contextStack.isEmpty() && (var == contextStack.peek())) {
                contextStack.pop();
            }
        }
        lastVar = var;
        variableStackSize--;
    }

    /**
     * Register a inline function using closure variables so it can be cleared
     * after query execution.
     *
     * @param func an inline function definition using closure variables
     */
    void pushClosure(final UserDefinedFunction func) {
        closures.add(func);
    }

    @Override
    public int getCurrentStackSize() {
        return variableStackSize;
    }

    @Override
    public void functionStart(final FunctionSignature signature) {
        callStack.push(signature);
    }

    @Override
    public void functionEnd() {
        if (callStack.isEmpty()) {
            LOG.warn("Function call stack is empty, but XQueryContext.functionEnd() was called. This "
                    + "could indicate a concurrency issue (shared XQueryContext?)");
        } else {
            callStack.pop();
        }
    }

    @Override
    public boolean tailRecursiveCall(final FunctionSignature signature) {
        return callStack.contains(signature);
    }

    @Override
    public void mapModule(final String namespace, final XmldbURI uri) {
        mappedModules.put(namespace, uri);
    }

    @Override
    public Module importModule(String namespaceURI, String prefix, String location)
            throws XPathException {

        if (XML_NS_PREFIX.equals(prefix) || XMLNS_ATTRIBUTE.equals(prefix)) {
            throw new XPathException(ErrorCodes.XQST0070, "The prefix declared for a module import must not be 'xml' or 'xmlns'.");
        }

        if (namespaceURI != null && namespaceURI.isEmpty()) {
            throw new XPathException(ErrorCodes.XQST0088, "The first URILiteral in a module import must be of nonzero length.");
        }

        Module module = null;

        if (namespaceURI != null) {
            module = getRootModule(namespaceURI);
        }

        if (module != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Module " + namespaceURI + " already present.");
            }
            // Set locally to remember the dependency in case it was inherited.
            setModule(namespaceURI, module);

        } else {
            // if location is not specified, try to resolve in expath repo
            if (location == null && namespaceURI != null) {
                module = resolveInEXPathRepository(namespaceURI, prefix);
            }

            if (module == null) {

                if (location == null && namespaceURI != null) {
                    // check if there's a static mapping in the configuration
                    location = getModuleLocation(namespaceURI);

                    if (location == null) {
                        location = namespaceURI;
                    }
                }

                //Is the module's namespace mapped to a URL ?
                if (mappedModules.containsKey(location)) {
                    location = mappedModules.get(location).toString();
                }

                // is it a Java module?
                if (location.startsWith(JAVA_URI_START)) {
                    location = location.substring(JAVA_URI_START.length());
                    module = loadBuiltInModule(namespaceURI, location);

                } else {
                    Source moduleSource;

                    if (location.startsWith(XmldbURI.XMLDB_URI_PREFIX)
                            || ((location.indexOf(':') == -1) && moduleLoadPath.startsWith(XmldbURI.XMLDB_URI_PREFIX))) {

                        // Is the module source stored in the database?
                        try {
                            XmldbURI locationUri = XmldbURI.xmldbUriFor(location);

                            if (moduleLoadPath.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
                                final XmldbURI moduleLoadPathUri = XmldbURI.xmldbUriFor(moduleLoadPath);
                                locationUri = moduleLoadPathUri.resolveCollectionPath(locationUri);
                            }

                            DocumentImpl sourceDoc = null;

                            try {
                                sourceDoc = getBroker().getXMLResource(locationUri.toCollectionPathURI(), LockMode.READ_LOCK);

                                if (sourceDoc == null) {
                                    throw moduleLoadException("Module location hint URI '" + location + "' does not refer to anything.", location);
                                }

                                if ((sourceDoc.getResourceType() != DocumentImpl.BINARY_FILE) || !"application/xquery".equals(sourceDoc.getMetadata().getMimeType())) {
                                    throw moduleLoadException("Module location hint URI '" + location + "' does not refer to an XQuery.", location);
                                }

                                moduleSource = new DBSource(getBroker(), (BinaryDocument) sourceDoc, true);

                                // we don't know if the module will get returned, oh well
                                module = compileOrBorrowModule(prefix, namespaceURI, location, moduleSource);

                            } catch (final PermissionDeniedException e) {
                                throw moduleLoadException("Permission denied to read module source from location hint URI '" + location + ".", location, e);
                            } finally {
                                if(sourceDoc != null) {
                                    sourceDoc.getUpdateLock().release(LockMode.READ_LOCK);
                                }
                            }
                        } catch (final URISyntaxException e) {
                            throw moduleLoadException("Invalid module location hint URI '" + location + "'.", location, e);
                        }

                    } else {

                        // No. Load from file or URL
                        try {
                            //TODO: use URIs to ensure proper resolution of relative locations
                            moduleSource = SourceFactory.getSource(getBroker(), moduleLoadPath, location, true);
                            if (moduleSource == null) {
                                throw moduleLoadException("Source for module '" + namespaceURI + "' not found module location hint URI '" + location + "'.", location);
                            }

                        } catch (final MalformedURLException e) {
                            throw moduleLoadException("Invalid module location hint URI '" + location + "'.", location, e);
                        } catch (final IOException e) {
                            throw moduleLoadException("Source for module '" + namespaceURI + "' could not be read, module location hint URI '" + location + "'.", location, e);
                        } catch (final PermissionDeniedException e) {
                            throw moduleLoadException("Permission denied to read module source from location hint URI '" + location + ".", location, e);
                        }

                        // we don't know if the module will get returned, oh well
                        module = compileOrBorrowModule(prefix, namespaceURI, location, moduleSource);
                    }
                }
            } // NOTE: expathrepo related, closes the EXPath else (if module != null)
        }

        if (module != null) {
            if (namespaceURI == null) {
                namespaceURI = module.getNamespaceURI();
            }
            if (prefix == null) {
                prefix = module.getDefaultPrefix();
            }
            declareNamespace(prefix, namespaceURI);
        }

        return module;
    }

    protected XPathException moduleLoadException(final String message, final String moduleLocation)
            throws XPathException {
        return new XPathException(ErrorCodes.XQST0059, message, new ValueSequence(new StringValue(moduleLocation)));
    }

    protected XPathException moduleLoadException(final String message, final String moduleLocation, final Exception e)
            throws XPathException {
        return new XPathException(ErrorCodes.XQST0059, message, new ValueSequence(new StringValue(moduleLocation)), e);
    }

    @SuppressWarnings("unchecked")
    @Override
    public String getModuleLocation(final String namespaceURI) {
        final Map<String, String> moduleMap =
                (Map) getBroker().getConfiguration().getProperty(PROPERTY_STATIC_MODULE_MAP);
        return moduleMap.get(namespaceURI);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<String> getMappedModuleURIs() {
        final Map<String, String> moduleMap =
                (Map) getBroker().getConfiguration().getProperty(PROPERTY_STATIC_MODULE_MAP);
        return moduleMap.keySet().iterator();
    }

    private ExternalModule compileOrBorrowModule(final String prefix, final String namespaceURI, final String location,
                                                 final Source source) throws XPathException {
        final ExternalModule module = compileModule(prefix, namespaceURI, location, source);
        if (module != null) {
            setModule(module.getNamespaceURI(), module);
            declareModuleVars(module);
        }
        return module;
    }

    /**
     * Compile an XQuery Module
     *
     * @param prefix       the namespace prefix of the module.
     * @param namespaceURI the namespace URI of the module.
     * @param location     the location of the module
     * @param source       the source of the module.
     * @return The compiled module, or null if the source is not a module
     * @throws XPathException if the module could not be loaded or compiled
     */
    private @Nullable
    ExternalModule compileModule(final String prefix, String namespaceURI, final String location,
                                 final Source source) throws XPathException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Loading module from " + location);
        }

        try (final Reader reader = source.getReader()) {
            if (reader == null) {
                throw moduleLoadException("failed to load module: '" + namespaceURI + "' from: " +
                        "'" + source + "', location: '" + location + "'. Source not found. ", location);
            }

            if (namespaceURI == null) {
                final QName qname = source.isModule();
                if (qname == null) {
                    return null;
                }
                namespaceURI = qname.getNamespaceURI();
            }

            final ExternalModuleImpl modExternal = new ExternalModuleImpl(namespaceURI, prefix);
            setModule(namespaceURI, modExternal);
            final XQueryContext modContext = new ModuleContext(this, prefix, namespaceURI, location);
            modExternal.setContext(modContext);
            final XQueryLexer lexer = new XQueryLexer(modContext, reader);
            final XQueryParser parser = new XQueryParser(lexer);
            final XQueryTreeParser astParser = new XQueryTreeParser(modContext, modExternal);

            try {
                parser.xpath();

                if (parser.foundErrors()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(parser.getErrorMessage());
                    }
                    throw new XPathException("error found while loading module from " + location + ": " + parser.getErrorMessage());
                }
                final AST ast = parser.getAST();

                final PathExpr path = new PathExpr(modContext);
                astParser.xpath(ast, path);

                if (astParser.foundErrors()) {
                    throw new XPathException("error found while loading module from " + location + ": " + astParser.getErrorMessage(), astParser.getLastException());
                }

                modExternal.setRootExpression(path);

                if (namespaceURI != null && !modExternal.getNamespaceURI().equals(namespaceURI)) {
                    throw new XPathException("namespace URI declared by module (" + modExternal.getNamespaceURI() + ") does not match namespace URI in import statement, which was: " + namespaceURI);
                }

                // Set source information on module context
//            String sourceClassName = source.getClass().getName();
//            modContext.setSourceKey(source.getKey().toString());
                // Extract the source type from the classname by removing the package prefix and the "Source" suffix
//            modContext.setSourceType( sourceClassName.substring( 17, sourceClassName.length() - 6 ) );

                modExternal.setSource(source);
                modContext.setSource(source);
                modExternal.setIsReady(true);
                return modExternal;
            } catch (final RecognitionException e) {
                throw new XPathException(e.getLine(), e.getColumn(), "error found while loading module from " + location + ": " + e.getMessage());
            } catch (final TokenStreamException e) {
                throw new XPathException("error found while loading module from " + location + ": " + e.getMessage(), e);
            } catch (final XPathException e) {
                e.prependMessage("Error while loading module " + location + ": ");
                throw e;
            }
        } catch (final IOException e) {
            throw moduleLoadException("IO exception while loading module '" + namespaceURI + "'" +
                    " from '" + source + "'", location, e);
        }
    }

    private void declareModuleVars(final Module module) {
        final String moduleNS = module.getNamespaceURI();

        for (final Iterator<Variable> i = globalVariables.values().iterator(); i.hasNext(); ) {
            final Variable var = i.next();

            if (moduleNS.equals(var.getQName().getNamespaceURI())) {
                module.declareVariable(var);
                i.remove();
            }
        }
    }

    @Override
    public void addForwardReference(final FunctionCall call) {
        forwardReferences.add(call);
    }

    @Override
    public void resolveForwardReferences() throws XPathException {
        while (!forwardReferences.isEmpty()) {
            final FunctionCall call = forwardReferences.pop();
            final UserDefinedFunction func = call.getContext().resolveFunction(call.getQName(), call.getArgumentCount());

            if (func == null) {
                throw new XPathException(call, ErrorCodes.XPST0017, "Call to undeclared function: " + call.getQName().getStringValue());
            } else {
                call.resolveForwardReference(func);
            }
        }
    }

    /**
     * Get environment variables. The variables shall not change
     * during execution of query.
     *
     * @return Map of environment variables
     */
    public Map<String, String> getEnvironmentVariables() {
        if (envs == null) {
            envs = Collections.unmodifiableMap(new HashMap<>(System.getenv()));
        }
        return envs;
    }

    /**
     * Get Java System properties. The properties shall not change
     * during execution of query.
     *
     * @return Map of Java System Properties
     */
    public Map<String, String> getJavaSystemProperties() {
        if (props == null) {
            final Map<String, String> strProps = new HashMap<>();
            for (final Map.Entry<Object, Object> prop : System.getProperties().entrySet()) {
                final Object value = prop.getValue();
                if (value instanceof String) {
                    strProps.put(prop.getKey().toString(), (String) value);
                }
            }
            props = Collections.unmodifiableMap(strProps);
        }
        return props;
    }

    /**
     * Gets the Effective user
     * i.e. the user that the query is executing as
     *
     * @return The Effective User
     */
    public Subject getEffectiveUser() {
        return getBroker().getCurrentSubject();
    }

    /**
     * Gets the Real User
     * i.e. the user that initiated execution of the query
     * Note this is not necessarily the same as the user that the
     * query is executing as
     *
     * @return The Real User
     * @see org.exist.xquery.XQueryContext#getEffectiveUser()
     */
    public Subject getRealUser() {
        return realUser;
    }

    private void setRealUser(final Subject realUser) {
        this.realUser = realUser;
    }

    /**
     * Save state
     */
    private class SavedState {
        private Map<String, Module> modulesSaved = null;
        private Map<String, Module> allModulesSaved = null;
        private Map<String, String> staticNamespacesSaved = null;
        private Map<String, String> staticPrefixesSaved = null;

        @SuppressWarnings("unchecked")
        void save() {
            if (modulesSaved == null) {
                modulesSaved = new HashMap<>(modules);
                allModulesSaved = new HashMap(allModules);
                staticNamespacesSaved = new HashMap(staticNamespaces);
                staticPrefixesSaved = new HashMap(staticPrefixes);
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

    @Override
    public boolean optimizationsEnabled() {
        return enableOptimizer;
    }

    @Override
    public void addOption(final String name, final String value) throws XPathException {
        if (staticOptions == null) {
            staticOptions = new ArrayList<>();
        }
        addOption(staticOptions, name, value);
    }

    @Override
    public void addDynamicOption(final String name, final String value) throws XPathException {
        if (dynamicOptions == null) {
            dynamicOptions = new ArrayList<>();
        }
        addOption(dynamicOptions, name, value);
    }

    private void addOption(final List<Option> options, final String name, final String value) throws XPathException {
        final QName qn;
        try {
            qn = QName.parse(this, name, defaultFunctionNamespace);
        } catch (final QName.IllegalQNameException e) {
            throw new XPathException(ErrorCodes.XPST0081, "No namespace defined for prefix " + name);
        }

        final Option option = new Option(qn, value);

        //if the option exists, remove it so we can add the new option
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equals(option)) {
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

        } else if (Option.TIMEOUT_QNAME.compareTo(qn) == 0) {
            watchdog.setTimeoutFromOption(option);

        } else if (Option.OUTPUT_SIZE_QNAME.compareTo(qn) == 0) {
            watchdog.setMaxNodesFromOption(option);

        } else if (Option.OPTIMIZE_QNAME.compareTo(qn) == 0) {
            final String[] params = option.tokenizeContents();
            if (params.length > 0) {
                final String[] param = Option.parseKeyValuePair(params[0]);
                if (param != null && "enable".equals(param[0])) {
                    enableOptimizer = "yes".equals(param[1]);
                }
            }
        }
        //TODO : not sure how these 2 options might/have to be related
        else if (Option.OPTIMIZE_IMPLICIT_TIMEZONE.compareTo(qn) == 0) {
            //TODO : error check
            final Duration duration = TimeUtils.getInstance().newDuration(option.getContents());
            implicitTimeZone = new SimpleTimeZone((int) duration.getTimeInMillis(new Date()), "XQuery context");

        } else if (Option.CURRENT_DATETIME.compareTo(qn) == 0) {
            //TODO : error check
            final DateTimeValue dtv = new DateTimeValue(option.getContents());
            calendar = (XMLGregorianCalendar) dtv.calendar.clone();
        }
    }

    @Override
    public Option getOption(final QName qname) {
        if (dynamicOptions != null) {
            for (final Option option : dynamicOptions) {
                if (qname.compareTo(option.getQName()) == 0) {
                    return option;
                }
            }
        }

        if (staticOptions != null) {
            for (final Option option : staticOptions) {
                if (qname.compareTo(option.getQName()) == 0) {
                    return option;
                }
            }
        }

        return null;
    }

    @Override
    public Pragma getPragma(final String name, String contents) throws XPathException {
        final QName qname;
        try {
            qname = QName.parse(this, name);
        } catch (final QName.IllegalQNameException e) {
            throw new XPathException(ErrorCodes.XPST0081, "No namespace defined for prefix " + name);
        }

        if (qname.getNamespaceURI().isEmpty()) {
            throw new XPathException("XPST0081: pragma's ('" + name + "') namespace URI is empty");
        } else if (Namespaces.EXIST_NS.equals(qname.getNamespaceURI())) {
            contents = StringValue.trimWhitespace(contents);

            if (TimerPragma.TIMER_PRAGMA.equals(qname)) {
                return new TimerPragma(qname, contents);
            }

            if (Optimize.OPTIMIZE_PRAGMA.equals(qname)) {
                return new Optimize(this, qname, contents, true);
            }

            if (ForceIndexUse.EXCEPTION_IF_INDEX_NOT_USED_PRAGMA.equals(qname)) {
                return new ForceIndexUse(qname, contents);
            }

            if (ProfilePragma.PROFILING_PRAGMA.equals(qname)) {
                return new ProfilePragma(qname, contents);
            }

            if (NoIndexPragma.NO_INDEX_PRAGMA.equals(qname)) {
                return new NoIndexPragma(qname, contents);
            }
        }

        return null;
    }

    @Override
    public DocumentImpl storeTemporaryDoc(final org.exist.dom.memtree.DocumentImpl doc) throws XPathException {
        try {
            final DocumentImpl targetDoc = getBroker().storeTempResource(doc);

            if (targetDoc == null) {
                throw new XPathException("Internal error: failed to store temporary doc fragment");
            }
            LOG.warn("Stored: " + targetDoc.getDocId() + ": " + targetDoc.getURI(), new Throwable());
            return targetDoc;
        } catch (final EXistException | LockException | PermissionDeniedException e) {
            throw new XPathException(TEMP_STORE_ERROR, e);
        }
    }

    @Override
    public void setAttribute(final String attribute, final Object value) {
        attributes.put(attribute, value);
    }

    @Override
    public Object getAttribute(final String attribute) {
        return attributes.get(attribute);
    }

    @Override
    public void setXQueryContextVar(final String name, final Object xqVar) {
        XQueryContextVars.put(name, xqVar);
    }

    @Override
    public Object getXQueryContextVar(final String name) {
        return XQueryContextVars.get(name);
    }

    /**
     * Load the default prefix/namespace mappings table and set up internal functions.
     *
     * @param config the configuration
     */
    @SuppressWarnings("unchecked")
    void loadDefaults(final Configuration config) {
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

        // Switch: enable optimizer
        Object param = config.getProperty(PROPERTY_ENABLE_QUERY_REWRITING);
        enableOptimizer = (param != null) && "yes".equals(param.toString());

        // Switch: Backward compatibility
        param = config.getProperty(PROPERTY_XQUERY_BACKWARD_COMPATIBLE);
        backwardsCompatible = (param == null) || "yes".equals(param.toString());

        // Switch: raiseErrorOnFailedRetrieval
        final Boolean option = ((Boolean) config.getProperty(PROPERTY_XQUERY_RAISE_ERROR_ON_FAILED_RETRIEVAL));
        raiseErrorOnFailedRetrieval = (option != null) && option;

        // Get map of built-in modules
        final Map<String, Class<Module>> builtInModules = (Map) config.getProperty(PROPERTY_BUILT_IN_MODULES);

        if (builtInModules != null) {

            // Iterate on all map entries
            for (final Map.Entry<String, Class<Module>> entry : builtInModules.entrySet()) {

                // Get URI and class
                final String namespaceURI = entry.getKey();
                final Class<Module> moduleClass = entry.getValue();

                // first check if the module has already been loaded in the parent context
                final Module module = getModule(namespaceURI);

                if (module == null) {
                    // Module does not exist yet, instantiate
                    instantiateModule(namespaceURI, moduleClass,
                            (Map<String, Map<String, List<? extends Object>>>) config.getProperty(PROPERTY_MODULE_PARAMETERS));

                } else if (getPrefixForURI(module.getNamespaceURI()) == null && !module.getDefaultPrefix().isEmpty()) {

                    // make sure the namespaces of default modules are known,
                    // even if they were imported in a parent context
                    try {
                        declareNamespace(module.getDefaultPrefix(), module.getNamespaceURI());

                    } catch (final XPathException e) {
                        LOG.warn("Internal error while loading default modules: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * Load default namespaces, e.g. xml, xsi, xdt, fn, local, exist and dbgp.
     */
    private void loadDefaultNS() {
        try {
            // default namespaces
            staticNamespaces.put(XML_NS_PREFIX, XML_NS);
            staticPrefixes.put(XML_NS, XML_NS_PREFIX);
            declareNamespace("xs", Namespaces.SCHEMA_NS);
            declareNamespace("xsi", Namespaces.SCHEMA_INSTANCE_NS);

            //required for backward compatibility
            declareNamespace("xdt", Namespaces.XPATH_DATATYPES_NS);
            declareNamespace("fn", Namespaces.XPATH_FUNCTIONS_NS);
            declareNamespace("local", Namespaces.XQUERY_LOCAL_NS);
            declareNamespace(Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX, Namespaces.W3C_XQUERY_XPATH_ERROR_NS);

            //*not* as standard NS
            declareNamespace(Namespaces.EXIST_NS_PREFIX, Namespaces.EXIST_NS);
            declareNamespace(Namespaces.EXIST_JAVA_BINDING_NS_PREFIX, Namespaces.EXIST_JAVA_BINDING_NS);
            declareNamespace(Namespaces.EXIST_XQUERY_XPATH_ERROR_PREFIX, Namespaces.EXIST_XQUERY_XPATH_ERROR_NS);

            //TODO : include "err" namespace ?
            declareNamespace("dbgp", Debuggee.NAMESPACE_URI);

        } catch (final XPathException e) {
            //ignored because it should never happen
            if (LOG.isDebugEnabled()) {
                LOG.debug(e);
            }
        }
    }

    @Override
    public void registerUpdateListener(final UpdateListener listener) {
        if (updateListener == null) {
            updateListener = new ContextUpdateListener();
            final DBBroker broker = getBroker();
            broker.getBrokerPool().getNotificationService().subscribe(updateListener);
        }
        updateListener.addListener(listener);
    }

    protected void clearUpdateListeners() {
        if (updateListener != null) {
            final DBBroker broker = getBroker();
            broker.getBrokerPool().getNotificationService().unsubscribe(updateListener);
        }
        updateListener = null;
    }

    @Override
    public void checkOptions(final Properties properties) throws XPathException {
        checkLegacyOptions(properties);
        if (dynamicOptions != null) {
            for (final Option option : dynamicOptions) {
                if (Namespaces.XSLT_XQUERY_SERIALIZATION_NS.equals(option.getQName().getNamespaceURI())) {
                    SerializerUtils.setProperty(option.getQName().getLocalPart(), option.getContents(), properties,
                            inScopeNamespaces::get);
                }
            }
        }

        if (staticOptions != null) {
            for (final Option option : staticOptions) {
                if (Namespaces.XSLT_XQUERY_SERIALIZATION_NS.equals(option.getQName().getNamespaceURI())
                        && !properties.containsKey(option.getQName().getLocalPart())) {
                    SerializerUtils.setProperty(option.getQName().getLocalPart(), option.getContents(), properties,
                            inScopeNamespaces::get);
                }
            }
        }
    }

    /**
     * Legacy method to check serialization properties set via option exist:serialize.
     *
     * @param properties the serialization properties
     * @throws XPathException if there is an unknown serialization property
     */
    private void checkLegacyOptions(final Properties properties) throws XPathException {
        final Option pragma = getOption(Option.SERIALIZE_QNAME);
        if (pragma == null) {
            return;
        }

        final String[] contents = pragma.tokenizeContents();

        for (final String content : contents) {
            final String[] pair = Option.parseKeyValuePair(content);

            if (pair == null) {
                throw new XPathException("Unknown parameter found in " + pragma.getQName().getStringValue()
                        + ": '" + content + "'");
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Setting serialization property from pragma: " + pair[0] + " = " + pair[1]);
            }

            properties.setProperty(pair[0], pair[1]);
        }
    }

    @Override
    public void setDebuggeeJoint(final DebuggeeJoint joint) {
        //XXX: if (debuggeeJoint != null) ???
        debuggeeJoint = joint;
    }

    @Override
    public DebuggeeJoint getDebuggeeJoint() {
        return debuggeeJoint;
    }

    @Override
    public boolean isDebugMode() {
        return debuggeeJoint != null && isVarDeclared(Debuggee.SESSION);
    }

    @Override
    public boolean requireDebugMode() {
        return isVarDeclared(Debuggee.SESSION);
    }

    private Deque<BinaryValue> binaryValueInstances;

    void enterEnclosedExpr() {
        if (binaryValueInstances != null) {
            final Iterator<BinaryValue> it = binaryValueInstances.descendingIterator();
            while (it.hasNext()) {
                it.next().incrementSharedReferences();
            }
        }
    }

    void exitEnclosedExpr() {
        if (binaryValueInstances != null) {
            final Iterator<BinaryValue> it = binaryValueInstances.iterator();
            List<BinaryValue> destroyable = null;
            while (it.hasNext()) {
                try {
                    final BinaryValue bv = it.next();
                    bv.close(); // really just decrements a reference
                    if (bv.isClosed()) {
                        if (destroyable == null) {
                            destroyable = new ArrayList<>();
                        }
                        destroyable.add(bv);
                    }
                } catch (final IOException e) {
                    LOG.warn("Unable to close binary reference on exiting enclosed expression: " + e.getMessage(), e);
                }
            }

            // eagerly cleanup those BinaryValues that are not used outside the EnclosedExpr (to release memory)
            if (destroyable != null) {
                for (final BinaryValue bvd : destroyable) {
                    binaryValueInstances.remove(bvd);
                }
            }
        }
    }

    @Override
    public void registerBinaryValueInstance(final BinaryValue binaryValue) {
        if (binaryValueInstances == null) {
            binaryValueInstances = new ArrayDeque<>();
        }

        if (cleanupTasks.isEmpty() || cleanupTasks.stream().noneMatch(ct -> ct instanceof BinaryValueCleanupTask)) {
            cleanupTasks.add(new BinaryValueCleanupTask());
        }

        binaryValueInstances.push(binaryValue);
    }

    /**
     * Cleanup Task which is responsible for relasing the streams
     * of any {@link BinaryValue} which have been used during
     * query execution
     */
    public static class BinaryValueCleanupTask implements CleanupTask {
        @Override
        public void cleanup(final XQueryContext context, final Predicate<Object> predicate) {
            if (context.binaryValueInstances != null) {
                List<BinaryValue> removable = null;
                for (final BinaryValue bv : context.binaryValueInstances) {
                    try {
                        if (predicate.test(bv)) {
                            bv.close();
                            if (removable == null) {
                                removable = new ArrayList<>();
                            }
                            removable.add(bv);
                        }
                    } catch (final IOException e) {
                        LOG.error("Unable to close binary value: " + e.getMessage(), e);
                    }
                }

                if (removable != null) {
                    for (final BinaryValue bv : removable) {
                        context.binaryValueInstances.remove(bv);
                    }
                }
            }
        }
    }

    @Override
    public String getCacheClass() {
        return (String) getBroker().getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY);
    }

    public void destroyBinaryValue(final BinaryValue value) {
        if (binaryValueInstances != null) {
            binaryValueInstances.remove(value);
        }
    }

    public void setXQueryVersion(int version) {
        xqueryVersion = version;
    }

    public int getXQueryVersion() {
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

    /**
     * NOTE: the {@link #unsubscribe()} method can be called
     * from {@link org.exist.storage.NotificationService#unsubscribe(UpdateListener)}
     * by another thread, so this class needs to be thread-safe.
     */
    @ThreadSafe
    private static class ContextUpdateListener implements UpdateListener {
        /*
         * We use Concurrent safe data structures here, so that we don't have
         * to block any calling threads.
         *
         * The AtomicReference enables us to quickly clear the listeners
         * in #unsubscribe() and maintain happens-before integrity whilst
         * unsubcribing them. The CopyOnWriteArrayList allows
         * us to add listeners whilst iterating over a snapshot
         * of existing iterators in other methods.
         */
        private final AtomicReference<List<UpdateListener>> listeners = new AtomicReference<>(new CopyOnWriteArrayList<>());

        private void addListener(final UpdateListener listener) {
            listeners.get().add(listener);
        }

        @Override
        public void documentUpdated(final DocumentImpl document, final int event) {
            listeners.get().forEach(listener -> listener.documentUpdated(document, event));
        }

        @Override
        public void unsubscribe() {
            List<UpdateListener> prev = listeners.get();
            while (!listeners.compareAndSet(prev, new CopyOnWriteArrayList<>())) {
                prev = listeners.get();
            }

            prev.forEach(UpdateListener::unsubscribe);
        }

        @Override
        public void nodeMoved(final NodeId oldNodeId, final NodeHandle newNode) {
            listeners.get().forEach(listener -> listener.nodeMoved(oldNodeId, newNode));
        }

        @Override
        public void debug() {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("XQueryContext: %s document update listeners", listeners.get().size()));
            }

            listeners.get().forEach(UpdateListener::debug);
        }
    }

    private final List<CleanupTask> cleanupTasks = new ArrayList<>();

    public void registerCleanupTask(final CleanupTask cleanupTask) {
        cleanupTasks.add(cleanupTask);
    }

    public interface CleanupTask {
        void cleanup(final XQueryContext context, final Predicate<Object> predicate);
    }

    @Override
    public void runCleanupTasks(final Predicate<Object> predicate) {
        for (final CleanupTask cleanupTask : cleanupTasks) {
            try {
                cleanupTask.cleanup(this, predicate);
            } catch (final Throwable t) {
                LOG.error("Cleaning up XQueryContext: Ignoring: " + t.getMessage(), t);
            }
        }
        // now it is safe to clear the cleanup tasks list as we know they have run
        // do not move this anywhere else
        cleanupTasks.clear();
    }

    @Immutable
    public static class HttpContext {
        private final RequestWrapper request;
        private final ResponseWrapper response;
        private final SessionWrapper session;

        public HttpContext(final RequestWrapper request, final ResponseWrapper response, final SessionWrapper session) {
            this.request = request;
            this.response = response;
            this.session = session;
        }

        public HttpContext(final RequestWrapper request, final ResponseWrapper response) {
            this.request = request;
            this.response = response;
            this.session = request.getSession(false);
        }

        public RequestWrapper getRequest() {
            return request;
        }

        public ResponseWrapper getResponse() {
            return response;
        }

        public SessionWrapper getSession() {
            return session;
        }

        /**
         * Returns a new HttpContext with the new session set.
         * <p>
         * The request and response are referenced from this object.
         *
         * @param newSession the new session to set.
         * @return the new HttpContext.
         */
        public HttpContext setSession(final SessionWrapper newSession) {
            return new HttpContext(request, response, newSession);
        }
    }
}
