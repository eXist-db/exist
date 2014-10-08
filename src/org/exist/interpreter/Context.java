package org.exist.interpreter;

import java.io.IOException;
import java.text.Collator;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.stream.XMLStreamException;

import org.exist.Database;
import org.exist.collections.triggers.TriggerException;
import org.exist.debuggee.DebuggeeJoint;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.security.Subject;
import org.exist.security.xacml.AccessContext;
import org.exist.security.xacml.ExistPDP;
import org.exist.security.xacml.XACMLSource;
import org.exist.source.Source;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.UpdateListener;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.util.hashtable.NamePool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Expression;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.LocalVariable;
import org.exist.xquery.Module;
import org.exist.xquery.Option;
import org.exist.xquery.PathExpr;
import org.exist.xquery.Pragma;
import org.exist.xquery.Profiler;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XQueryWatchDog;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;

public interface Context {

	/**
	 * Returns true if this context has a parent context (means it is a module context).
	 *
	 * @return  False.
	 */
	public boolean hasParent();

	public XQueryContext getRootContext();

	public XQueryContext copyContext();

	/**
	 * Update the current dynamic context using the properties of another context. This is needed by {@link org.exist.xquery.functions.util.Eval}.
	 *
	 * @param  from
	 */
	public void updateContext(XQueryContext from);

	/**
	 * Prepares the current context before xquery execution.
	 */
	public void prepareForExecution();

	public AccessContext getAccessContext();

	/**
	 * Is profiling enabled?
	 *
	 * @return  true if profiling is enabled for this context.
	 */
	public boolean isProfilingEnabled();

	public boolean isProfilingEnabled(int verbosity);

	/**
	 * Returns the {@link Profiler} instance of this context if profiling is enabled.
	 *
	 * @return  the profiler instance.
	 */
	public Profiler getProfiler();

	/**
	 * Called from the XQuery compiler to set the root expression for this context.
	 *
	 * @param  expr
	 */
	public void setRootExpression(Expression expr);

	/**
	 * Returns the root expression of the XQuery associated with this context.
	 *
	 * @return  root expression
	 */
	public Expression getRootExpression();

	/**
	 * Returns the number of expression objects in the internal representation of the query. Used to estimate the size of the query.
	 *
	 * @return  number of expression objects
	 */
	public int getExpressionCount();
        
        public void setSource(Source source);
        
        public Source getSource();

	public void setXacmlSource(XACMLSource xacmlSource);
        
	public XACMLSource getXacmlSource();

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
	public void declareNamespace(String prefix, String uri) throws XPathException;

	public void declareNamespaces(Map<String, String> namespaceMap);

	/**
	 * Removes the namespace URI from the prefix/namespace mappings table.
	 *
	 * @param  uri
	 */
	public void removeNamespace(String uri);

	/**
	 * Declare an in-scope namespace. This is called during query execution.
	 *
	 * @param  prefix
	 * @param  uri
	 */
	public void declareInScopeNamespace(String prefix, String uri);

	public String getInScopeNamespace(String prefix);

	public String getInScopePrefix(String uri);

	public String getInheritedNamespace(String prefix);

	public String getInheritedPrefix(String uri);

	/**
	 * Return the namespace URI mapped to the registered prefix or null if the prefix is not registered.
	 *
	 * @param   prefix
	 *
	 * @return  namespace
	 */
	public String getURIForPrefix(String prefix);

	/**
	 * Get URI Prefix
	 *
	 * @param   uri
	 *
	 * @return  the prefix mapped to the registered URI or null if the URI is not registered.
	 */
	public String getPrefixForURI(String uri);

	/**
	 * Returns the current default function namespace.
	 *
	 * @return  current default function namespace
	 */
	public String getDefaultFunctionNamespace();

	/**
	 * Set the default function namespace. By default, this points to the namespace for XPath built-in functions.
	 *
	 * @param   uri
	 *
	 * @throws  XPathException  
	 */
	public void setDefaultFunctionNamespace(String uri) throws XPathException;

	/**
	 * Returns the current default element namespace.
	 *
	 * @return  current default element namespace schema
	 *
	 * @throws  XPathException  
	 */
	public String getDefaultElementNamespaceSchema() throws XPathException;

	/**
	 * Set the default element namespace. By default, this points to the empty uri.
	 *
	 * @param   uri
	 *
	 * @throws  XPathException  
	 */
	public void setDefaultElementNamespaceSchema(String uri) throws XPathException;

	/**
	 * Returns the current default element namespace.
	 *
	 * @return  current default element namespace
	 *
	 * @throws  XPathException  
	 */
	public String getDefaultElementNamespace() throws XPathException;

	/**
	 * Set the default element namespace. By default, this points to the empty uri.
	 *
	 * @param      uri     a <code>String</code> value
	 * @param      schema  a <code>String</code> value
	 *
	 * @exception  XPathException  if an error occurs
	 */
	public void setDefaultElementNamespace(String uri, String schema) throws XPathException;

	/**
	 * Set the default collation to be used by all operators and functions on strings. Throws an exception if the collation is unknown or cannot be
	 * instantiated.
	 *
	 * @param   uri
	 *
	 * @throws  XPathException
	 */
	public void setDefaultCollation(String uri) throws XPathException;

	public String getDefaultCollation();

	public Collator getCollator(String uri) throws XPathException;

	public Collator getDefaultCollator();

	/**
	 * Set the set of statically known documents for the current execution context. These documents will be processed if no explicit document set has
	 * been set for the current expression with fn:doc() or fn:collection().
	 *
	 * @param  docs
	 */
	public void setStaticallyKnownDocuments(XmldbURI[] docs);

	public void setStaticallyKnownDocuments(DocumentSet set);

	//TODO : not sure how these 2 options might/have to be related
	public void setCalendar(XMLGregorianCalendar newCalendar);

	public void setTimeZone(TimeZone newTimeZone);

	public XMLGregorianCalendar getCalendar();

	public TimeZone getImplicitTimeZone();

	/**
	 * Get statically known documents
	 *
	 * @return  set of statically known documents.
	 *
	 * @throws  XPathException  
	 */
	public DocumentSet getStaticallyKnownDocuments() throws XPathException;

	public ExtendedXMLStreamReader getXMLStreamReader(NodeValue nv) throws XMLStreamException, IOException;

	public void setProtectedDocs(LockedDocumentMap map);

	public LockedDocumentMap getProtectedDocs();

	public boolean inProtectedMode();

	/**
	 * Should loaded documents be locked?
	 *
	 * <p>see #setLockDocumentsOnLoad(boolean)</p>  
	 */
	public boolean lockDocumentsOnLoad();

	public void addLockedDocument(DocumentImpl doc);

	public void setShared(boolean shared);

	public boolean isShared();

	public void addModifiedDoc(DocumentImpl document);

	public void reset();

	/**
	 * Prepare this XQueryContext to be reused. This should be called when adding an XQuery to the cache.
	 *
	 * @param  keepGlobals  
	 */
	public void reset(boolean keepGlobals);

	/**
	 * Returns true if whitespace between constructed element nodes should be stripped by default. 
	 */
	public boolean stripWhitespace();

	public void setStripWhitespace(boolean strip);

	/**
	 * Returns true if namespaces for constructed element and document nodes should be preserved on copy by default. 
	 */
	public boolean preserveNamespaces();

	/**
	 * The method <code>setPreserveNamespaces.</code>
	 *
	 * @param  preserve  a <code>boolean</code> value
	 */
	public void setPreserveNamespaces(final boolean preserve);

	/**
	 * Returns true if namespaces for constructed element and document nodes should be inherited on copy by default. 
	 */
	public boolean inheritNamespaces();

	/**
	 * The method <code>setInheritNamespaces.</code>
	 *
	 * @param  inherit  a <code>boolean</code> value
	 */
	public void setInheritNamespaces(final boolean inherit);

	/**
	 * Returns true if order empty is set to gretest, otherwise false for order empty is least. 
	 */
	public boolean orderEmptyGreatest();

	/**
	 * The method <code>setOrderEmptyGreatest.</code>
	 *
	 * @param  order  a <code>boolean</code> value
	 */
	public void setOrderEmptyGreatest(final boolean order);

	/**
	 * Get modules
	 *
	 * @return  iterator over all modules imported into this context
	 */
	public Iterator<Module> getModules();

	/**
	 * Get root modules
	 *
	 * @return  iterator over all modules registered in the entire context tree
	 */
	public Iterator<Module> getRootModules();

	public Iterator<Module> getAllModules();

	/**
	 * Get the built-in module registered for the given namespace URI.
	 *
	 * @param   namespaceURI
	 *
	 * @return  built-in module
	 */
	public Module getModule(String namespaceURI);

	public Module getRootModule(String namespaceURI);

	public void setModule(String namespaceURI, Module module);

	/**
	 * For compiled expressions: check if the source of any module imported by the current query has changed since compilation. 
	 */
	public boolean checkModulesValid();

	public void analyzeAndOptimizeIfModulesChanged(Expression expr) throws XPathException;

	/**
	 * Load a built-in module from the given class name and assign it to the namespace URI. The specified class should be a subclass of {@link
	 * Module}. The method will try to instantiate the class. If the class is not found or an exception is thrown, the method will silently fail. The
	 * namespace URI has to be equal to the namespace URI declared by the module class. Otherwise, the module is not loaded.
	 *
	 * @param   namespaceURI
	 * @param   moduleClass 
	 */
	public Module loadBuiltInModule(String namespaceURI, String moduleClass);

	/**
	 * Convenience method that returns the XACML Policy Decision Point for this database instance. If XACML has not been enabled, this returns null.
	 *
	 * @return  the PDP for this database instance, or null if XACML is disabled
	 */
	public ExistPDP getPDP();

	/**
	 * Declare a user-defined function. All user-defined functions are kept in a single hash map.
	 *
	 * @param   function
	 *
	 * @throws  XPathException
	 */
	public void declareFunction(UserDefinedFunction function) throws XPathException;

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
	public UserDefinedFunction resolveFunction(QName name, int argCount) throws XPathException;

	public Iterator<FunctionSignature> getSignaturesForFunction(QName name);

	public Iterator<UserDefinedFunction> localFunctions();

	/**
	 * Declare a local variable. This is called by variable binding expressions like "let" and "for".
	 *
	 * @param   var
	 *
	 * @throws  XPathException
	 */
	public LocalVariable declareVariableBinding(LocalVariable var) throws XPathException;

	/**
	 * Declare a global variable as by "declare variable".
	 *
	 * @param   var
	 *
	 * @return  variable
	 *
	 * @throws  XPathException
	 */
	public Variable declareGlobalVariable(Variable var) throws XPathException;

	public void undeclareGlobalVariable(QName name);

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
	public Variable declareVariable(String qname, Object value) throws XPathException;

	public Variable declareVariable(QName qn, Object value) throws XPathException;

	/**
	 * Try to resolve a variable.
	 *
	 * @param   name  the qualified name of the variable as string
	 *
	 * @return  the declared Variable object
	 *
	 * @throws  XPathException  if the variable is unknown
	 */
	public Variable resolveVariable(String name) throws XPathException;

	/**
	 * Try to resolve a variable.
	 *
	 * @param   qname  the qualified name of the variable
	 *
	 * @return  the declared Variable object
	 *
	 * @throws  XPathException  if the variable is unknown
	 */
	public Variable resolveVariable(QName qname) throws XPathException;

	public boolean isVarDeclared(QName qname);

	public Map<QName, Variable> getVariables();

	public Map<QName, Variable> getLocalVariables();

	public Map<QName, Variable> getGlobalVariables();

	/**
	 * Turn on/off XPath 1.0 backwards compatibility.
	 *
	 * <p>If turned on, comparison expressions will behave like in XPath 1.0, i.e. if any one of the operands is a number, the other operand will be
	 * cast to a double.</p>
	 *
	 * @param  backwardsCompatible
	 */
	public void setBackwardsCompatibility(boolean backwardsCompatible);

	/**
	 * XPath 1.0 backwards compatibility turned on?
	 *
	 * <p>In XPath 1.0 compatible mode, additional conversions will be applied to values if a numeric value is expected.</p>  
	 */
	public boolean isBackwardsCompatible();

	public boolean isRaiseErrorOnFailedRetrieval();

	/**
	 * Get the DBBroker instance used for the current query.
	 *
	 * <p>The DBBroker is the main database access object, providing access to all internal database functions.</p>
	 *
	 * @return  DBBroker instance
	 */
	public DBBroker getBroker();

	/**
	 * Get the subject which executes the current query.
	 *
	 * @return  subject
	 */
	public Subject getSubject();

	/**
	 * Get the document builder currently used for creating temporary document fragments. A new document builder will be created on demand.
	 *
	 * @return  document builder
	 */
	public MemTreeBuilder getDocumentBuilder();

	public MemTreeBuilder getDocumentBuilder(boolean explicitCreation);

	/**
	 * Returns the shared name pool used by all in-memory documents which are created within this query context. Create a name pool for every document
	 * would be a waste of memory, especially since it is likely that the documents contain elements or attributes with similar names.
	 *
	 * @return  the shared name pool
	 */
	public NamePool getSharedNamePool();

	public XQueryContext getContext();

	public void prologEnter(Expression expr);

	public void expressionStart(Expression expr) throws TerminatedException;

	public void expressionEnd(Expression expr);

	public void stackEnter(Expression expr) throws TerminatedException;

	public void stackLeave(Expression expr);

	public void proceed() throws TerminatedException;

	public void proceed(Expression expr) throws TerminatedException;

	public void proceed(Expression expr, MemTreeBuilder builder) throws TerminatedException;

	public void setWatchDog(XQueryWatchDog watchdog);

	public XQueryWatchDog getWatchDog();

	/**
	 * Push any document fragment created within the current execution context on the stack.
	 */
	public void pushDocumentContext();

	public void popDocumentContext();

	/**
	 * Set the base URI for the evaluation context.
	 *
	 * <p>This is the URI returned by the fn:base-uri() function.</p>
	 *
	 * @param  uri
	 */
	public void setBaseURI(AnyURIValue uri);

	/**
	 * Set the base URI for the evaluation context.
	 *
	 * <p>A base URI specified via the base-uri directive in the XQuery prolog overwrites any other setting.</p>
	 *
	 * @param  uri
	 * @param  setInProlog
	 */
	public void setBaseURI(AnyURIValue uri, boolean setInProlog);

	/**
	 * Set the path to a base directory where modules should be loaded from. Relative module paths will be resolved against this directory. The
	 * property is usually set by the XQueryServlet or XQueryGenerator, but can also be specified manually.
	 *
	 * @param  path
	 */
	public void setModuleLoadPath(String path);

	public String getModuleLoadPath();

	/**
	 * The method <code>isBaseURIDeclared.</code>
	 *
	 * @return  a <code>boolean</code> value
	 */
	public boolean isBaseURIDeclared();

	/**
	 * Get the base URI of the evaluation context.
	 *
	 * <p>This is the URI returned by the fn:base-uri() function.</p>
	 *
	 * @return     base URI of the evaluation context
	 *
	 * @exception  XPathException  if an error occurs
	 */
	public AnyURIValue getBaseURI() throws XPathException;

	/**
	 * Set the current context position, i.e. the position of the currently processed item in the context sequence. This value is required by some
	 * expressions, e.g. fn:position().
	 *
	 * @param  pos
	 * @param  sequence  
	 */
	public void setContextSequencePosition(int pos, Sequence sequence);

	/**
	 * Get the current context position, i.e. the position of the currently processed item in the context sequence.
	 *
	 * @return  current context position
	 */
	public int getContextPosition();

	public Sequence getContextSequence();

	public void pushInScopeNamespaces();

	/**
	 * Push all in-scope namespace declarations onto the stack.
	 *
	 * @param  inherit  
	 */
	@SuppressWarnings("unchecked")
	public void pushInScopeNamespaces(boolean inherit);

	public void popInScopeNamespaces();

	@SuppressWarnings("unchecked")
	public void pushNamespaceContext();

	public void popNamespaceContext();

	/**
	 * Returns the last variable on the local variable stack. The current variable context can be restored by passing the return value to {@link
	 * #popLocalVariables(LocalVariable)}.
	 *
	 * @param   newContext  
	 *
	 * @return  last variable on the local variable stack
	 */
	public LocalVariable markLocalVariables(boolean newContext);

	/**
	 * Restore the local variable stack to the position marked by variable var.
	 *
	 * @param  var
	 */
	public void popLocalVariables(LocalVariable var);

	/**
	 * Returns the current size of the stack. This is used to determine where a variable has been declared.
	 *
	 * @return  current size of the stack
	 */
	public int getCurrentStackSize();

	/**
	 * Report the start of a function execution. Adds the reported function signature to the function call stack.
	 *
	 * @param  signature  
	 */
	public void functionStart(FunctionSignature signature);

	/**
	 * Report the end of the currently executed function. Pops the last function signature from the function call stack.
	 */
	public void functionEnd();

	/**
	 * Check if the specified function signature is found in the current function called stack. If yes, the function might be tail recursive and needs
	 * to be optimized.
	 *
	 * @param   signature 
	 */
	public boolean tailRecursiveCall(FunctionSignature signature);

	public void mapModule(String namespace, XmldbURI uri);

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
	public Module importModule(String namespaceURI, String prefix, String location) throws XPathException;

	/**
	 * Returns the static location mapped to an XQuery source module, if known.
	 *
	 * @param   namespaceURI  the URI of the module
	 *
	 * @return  the location string
	 */
	@SuppressWarnings("unchecked")
	public String getModuleLocation(String namespaceURI);

	/**
	 * Returns an iterator over all module namespace URIs which are statically mapped to a known location.
	 *
	 * @return  an iterator
	 */
	@SuppressWarnings("unchecked")
	public Iterator<String> getMappedModuleURIs();

	/**
	 * Add a forward reference to an undeclared function. Forward references will be resolved later.
	 *
	 * @param  call
	 */
	public void addForwardReference(FunctionCall call);

	/**
	 * Resolve all forward references to previously undeclared functions.
	 *
	 * @throws  XPathException
	 */
	public void resolveForwardReferences() throws XPathException;

	public boolean optimizationsEnabled();

	/**
	 * for static compile-time options i.e. declare option
	 *
	 * @param   qnameString  
	 * @param   contents     
	 *
	 * @throws  XPathException  
	 */
	public void addOption(String qnameString, String contents) throws XPathException;

	/**
	 * for dynamic run-time options i.e. util:declare-option
	 *
	 * @param   qnameString  
	 * @param   contents     
	 *
	 * @throws  XPathException  
	 */
	public void addDynamicOption(String qnameString, String contents) throws XPathException;

	public Option getOption(QName qname);

	public Pragma getPragma(String name, String contents) throws XPathException;

	/**
	 * Store the supplied data to a temporary document fragment.
	 *
	 * @param   doc
	 *
	 * @throws  XPathException
	 */
	public DocumentImpl storeTemporaryDoc(org.exist.dom.memtree.DocumentImpl doc) throws XPathException;

	public void setAttribute(String attribute, Object value);

	public Object getAttribute(String attribute);

	/**
	 * Set an XQuery Context variable. General variable storage in the xquery context
	 *
	 * @param  name   The variable name
	 * @param  XQvar  The variable value, may be of any xs: type
	 */
	public void setXQueryContextVar(String name, Object XQvar);

	/**
	 * Get an XQuery Context variable. General variable storage in the xquery context
	 *
	 * @param   name  The variable name
	 *
	 * @return  The variable value indicated by name.
	 */
	public Object getXQueryContextVar(String name);

	public void registerUpdateListener(UpdateListener listener);

	/**
	 * Check if the XQuery contains pragmas that define serialization settings. If yes,
	 * copy the corresponding settings to the current set of output properties.
	 *
	 * @param   properties  the properties object to which serialization parameters will be added.
	 *
	 * @throws  XPathException  if an error occurs while parsing the option
	 */
	public void checkOptions(Properties properties) throws XPathException;

	public void setDebuggeeJoint(DebuggeeJoint joint);

	public DebuggeeJoint getDebuggeeJoint();

	public boolean isDebugMode();

	public boolean requireDebugMode();

	public void registerBinaryValueInstance(BinaryValue binaryValue);

	public void runCleanupTasks();

}