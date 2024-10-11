/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.stream.XMLStreamException;

import com.ibm.icu.text.Collator;
import org.exist.debuggee.DebuggeeJoint;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.security.Subject;
import org.exist.source.Source;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.UpdateListener;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.util.hashtable.NamePool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;

public interface Context {

    /**
     * Returns true if this context has a parent context (means it is a module context).
     *
     * @return true if there is a parent context.
     */
    boolean hasParent();

    XQueryContext getRootContext();

    XQueryContext copyContext();

    /**
     * Update the current dynamic context using the properties of another context.
     *
     * This is needed by {@link org.exist.xquery.functions.util.Eval}.
     *
     * @param from the context to update from
     */
    void updateContext(XQueryContext from);

    /**
     * Prepares the current context before xquery execution.
     */
    void prepareForExecution();

    /**
     * Is profiling enabled?
     *
     * @return true if profiling is enabled for this context.
     */
    boolean isProfilingEnabled();

    boolean isProfilingEnabled(int verbosity);

    /**
     * Returns the {@link Profiler} instance of this context if profiling is enabled.
     *
     * @return the profiler instance.
     */
    Profiler getProfiler();

    /**
     * Called from the XQuery compiler to set the root expression for this context.
     *
     * @param expr the root expression.
     */
    void setRootExpression(Expression expr);

    /**
     * Returns the root expression of the XQuery associated with this context.
     *
     * @return root expression
     */
    Expression getRootExpression();

    /**
     * Returns the number of expression objects in the internal representation of the query. Used to estimate the size of the query.
     *
     * @return number of expression objects
     */
    int getExpressionCount();

    void setSource(Source source);

    Source getSource();

    /**
     * Get the default language.
     *
     * @return the default language
     */
    String getDefaultLanguage();

    /**
     * Declare a user-defined static prefix/namespace mapping.
     *
     * eXist internally keeps a table containing all prefix/namespace mappings it found in documents, which have been previously stored into the
     * database. These default mappings need not to be declared explicitely.
     *
     * @param prefix the namespace prefix.
     * @param uri the namespace URI.
     *
     * @throws XPathException if an error occurs when declaring the namespace
     *     with error codes XQST0033 or XQST0070
     */
    void declareNamespace(String prefix, String uri) throws XPathException;

    void declareNamespaces(Map<String, String> namespaceMap);

    /**
     * Removes the namespace URI from the prefix/namespace mappings table.
     *
     * @param uri the namespace URI.
     */
    void removeNamespace(String uri);

    /**
     * Declare an in-scope namespace. This is called during query execution.
     *
     * @param prefix the namespace prefix.
     * @param uri the namespace uri.
     */
    void declareInScopeNamespace(String prefix, String uri);

    String getInScopeNamespace(String prefix);

    String getInScopePrefix(String uri);

    String getInheritedNamespace(String prefix);

    String getInheritedPrefix(String uri);

    /**
     * Return the namespace URI mapped to the registered prefix or null if the prefix is not registered.
     *
     * @param prefix the namespace prefix.
     *
     * @return the namespace URI.
     */
    String getURIForPrefix(String prefix);

    /**
     * Get URI Prefix.
     *
     * @param uri the namespace URI.
     *
     * @return the prefix mapped to the registered URI or null if the URI is not registered.
     */
    String getPrefixForURI(String uri);

    /**
     * Returns the current default function namespace.
     *
     * @return current default function namespace
     */
    String getDefaultFunctionNamespace();

    /**
     * Set the default function namespace. By default, this points to the namespace for XPath built-in functions.
     *
     * @param uri the namespace URI.
     *
     * @throws XPathException if an error occurs when setting the default function namespace.
     */
    void setDefaultFunctionNamespace(String uri) throws XPathException;

    /**
     * Returns the current default element namespace.
     *
     * @return current default element namespace schema
     *
     * @throws XPathException if an error occurs when getting the default element namespace.
     */
    String getDefaultElementNamespaceSchema() throws XPathException;

    /**
     * Set the default element namespace. By default, this points to the empty uri.
     *
     * @param uri the default element namespace schema uri
     *
     * @throws XPathException if an error occurs when setting the default element namespace schema.
     */
    void setDefaultElementNamespaceSchema(String uri) throws XPathException;

    /**
     * Returns the current default element namespace.
     *
     * @return current default element namespace
     *
     * @throws XPathException if an error occurs when getting the default element namespace.
     */
    String getDefaultElementNamespace() throws XPathException;

    /**
     * Set the default element namespace. By default, this points to the empty uri.
     *
     * @param uri the namespace uri
     * @param schema detail of the namespace schema, or null
     *
     * @throws XPathException if an error occurs when setting the default element namespace.
     */
    void setDefaultElementNamespace(String uri, @Nullable String schema) throws XPathException;

    /**
     * Set the default collation to be used by all operators and functions on strings.
     * Throws an exception if the collation is unknown or cannot be instantiated.
     *
     * @param uri the collation URI
     *
     * @throws XPathException if an error occurs when setting the default collation.
     */
    void setDefaultCollation(String uri) throws XPathException;

    String getDefaultCollation();

    /**
     * Get the Collator.
     *
     * @param uri The URI describing the collation and settings
     *
     * @return The Collator for the URI
     *
     * @throws XPathException if the collation is unknown.
     */
    Collator getCollator(String uri) throws XPathException;

    /**
     * Get the Collator.
     *
     * @param uri The URI describing the collation and settings
     * @param errorCode the error code if the URI cannot be resolved
     *
     * @return The Collator for the URI
     *
     * @throws XPathException if the collation is unknown.
     */
    Collator getCollator(String uri, ErrorCodes.ErrorCode errorCode) throws XPathException;

    Collator getDefaultCollator();

    /**
     * Set the set of statically known documents for the current execution context.
     * These documents will be processed if no explicit document set has been set for the current expression
     * with fn:doc() or fn:collection().
     *
     * @param docs the statically known documents
     */
    void setStaticallyKnownDocuments(XmldbURI[] docs);

    void setStaticallyKnownDocuments(DocumentSet set);

    //TODO : not sure how these 2 options might/have to be related
    void setCalendar(XMLGregorianCalendar newCalendar);

    void setTimeZone(TimeZone newTimeZone);

    XMLGregorianCalendar getCalendar();

    TimeZone getImplicitTimeZone();

    /**
     * Get statically known documents
     *
     * @return set of statically known documents.
     *
     * @throws XPathException if an error occurs when getting the statically known documents.
     */
    DocumentSet getStaticallyKnownDocuments() throws XPathException;

    ExtendedXMLStreamReader getXMLStreamReader(NodeValue nv) throws XMLStreamException, IOException;

    void setProtectedDocs(LockedDocumentMap map);

    LockedDocumentMap getProtectedDocs();

    boolean inProtectedMode();

    /**
     * Should loaded documents be locked?
     *
     * @return true if documents should be locked on load.
     */
    boolean lockDocumentsOnLoad();

    void setShared(boolean shared);

    boolean isShared();

    void addModifiedDoc(DocumentImpl document);

    void reset();

    /**
     * Prepare this XQueryContext to be reused. This should be called when adding an XQuery to the cache.
     *
     * @param keepGlobals true if global variables should be preserved.
     */
    void reset(boolean keepGlobals);

    /**
     * Returns true if whitespace between constructed element nodes should be stripped by default.
     *
     * @return true if whitespace should be stripped, false otherwise.
     */
    boolean stripWhitespace();

    void setStripWhitespace(boolean strip);

    /**
     * Returns true if namespaces for constructed element and document nodes should be preserved on copy by default.
     *
     * @return true if namespaces should be preserved, false otherwise.
     */
    boolean preserveNamespaces();

    /**
     * Set whether namespaces should be preserved.
     *
     * @param preserve true if namespaces should be preserved, false otherwise.
     */
    void setPreserveNamespaces(final boolean preserve);

    /**
     * Returns true if namespaces for constructed element and document nodes should be inherited on copy by default.
     *
     * @return true if namespaces are inheirted, false otherwise.
     */
    boolean inheritNamespaces();

    /**
     * Set whether namespaces should be inherited.
     *
     * @param inherit true if namespaces should be inherited, false otherwise.
     */
    void setInheritNamespaces(final boolean inherit);

    /**
     * Returns true if order empty is set to greatest, otherwise false for order empty is least.
     *
     * @return true if the order is empty-greatest, false otherwise.
     */
    boolean orderEmptyGreatest();

    /**
     * The method <code>setOrderEmptyGreatest.</code>
     *
     * @param order a <code>boolean</code> value
     */
    void setOrderEmptyGreatest(final boolean order);

    /**
     * Get modules.
     *
     * @return iterator over all modules imported into this context
     */
    Iterator<Module> getModules();

    /**
     * Get root modules.
     *
     * @return iterator over all modules registered in the entire context tree
     */
    Iterator<Module> getRootModules();

    Iterator<Module> getAllModules();

    /**
     * Get the built-in module(s) registered for the given namespace URI.
     *
     * @param namespaceURI the namespace of the module.
     *
     * @return the module, or null
     */
    @Nullable
    Module[] getModules(String namespaceURI);

    Module[] getRootModules(String namespaceURI);

    void setModules(String namespaceURI, @Nullable Module[] modules);

    void addModule(String namespaceURI, Module module);

    /**
     * For compiled expressions: check if the source of any module imported by the current
     * query has changed since compilation.
     *
     * @return true if the modules are valid, false otherwise.
     */
    boolean checkModulesValid();

    void analyzeAndOptimizeIfModulesChanged(Expression expr) throws XPathException;

    /**
     * Load a built-in module from the given class name and assign it to the namespace URI.
     *
     * The specified {@code moduleClass} should be a subclass of {@link Module}. The method will try to instantiate
     * the class.
     *
     * If the class is not found or an exception is thrown, the method will silently fail. The
     * namespace URI has to be equal to the namespace URI declared by the module class. Otherwise,
     * the module is not loaded.
     *
     * @param namespaceURI the namespace URI of the module to load
     * @param moduleClass  the Java class of the module to load
     *
     * @return the loaded module, or null
     */
    @Nullable
    Module loadBuiltInModule(String namespaceURI, String moduleClass);

    /**
     * Declare a user-defined function. All user-defined functions are kept in a single hash map.
     *
     * @param function the function.
     *
     * @throws XPathException if an error orccurs whilst declaring the function.
     */
    void declareFunction(UserDefinedFunction function) throws XPathException;

    /**
     * Resolve a user-defined function.
     *
     * @param name the function name
     * @param argCount the function arity
     *
     * @return the resolved function, or null
     */
    @Nullable
    UserDefinedFunction resolveFunction(QName name, int argCount);

    Iterator<FunctionSignature> getSignaturesForFunction(QName name);

    Iterator<UserDefinedFunction> localFunctions();


    /**
     * Declare a local variable. This is called by variable binding expressions like "let" and "for".
     *
     * @param var the variable
     *
     * @return the declare variable
     *
     * @throws XPathException if an error occurs whilst declaring the variable binding
     */
    LocalVariable declareVariableBinding(LocalVariable var) throws XPathException;

    /**
     * Declare a global variable as by "declare variable".
     *
     * @param var the variable
     *
     * @return variable the declared variable
     *
     * @throws XPathException if an error occurs whilst declaring the global variable
     */
    Variable declareGlobalVariable(Variable var) throws XPathException;

    void undeclareGlobalVariable(QName name);

    /**
     * Declare a user-defined variable.
     *
     * The value argument is converted into an XPath value (@see XPathUtil#javaObjectToXPath(Object)).
     *
     * @param qname the qualified name of the new variable. Any namespaces should have been declared before.
     * @param value a Java object, representing the fixed value of the variable
     *
     * @return the created Variable object
     *
     * @throws XPathException if the value cannot be converted into a known XPath value or the variable QName
     *     references an unknown namespace-prefix.
     */
    Variable declareVariable(String qname, Object value) throws XPathException;

    Variable declareVariable(QName qn, Object value) throws XPathException;

    /**
     * Try to resolve a variable.
     *
     * @param name the qualified name of the variable as string
     * @return the declared Variable object
     * @throws XPathException if the variable is unknown
     */
    Variable resolveVariable(String name) throws XPathException;


    /**
     * Try to resolve a variable.
     *
     * @param contextInfo contextual information
     * @param qname the qualified name of the variable
     *
     * @return the declared Variable object
     *
     * @throws XPathException if the variable is unknown
     */
    Variable resolveVariable(@Nullable AnalyzeContextInfo contextInfo, QName qname) throws XPathException;

    /**
     * Try to resolve a variable.
     *
     * @param qname the qualified name of the variable
     *
     * @return the declared Variable object
     *
     * @throws XPathException if the variable is unknown
     */
    Variable resolveVariable(QName qname) throws XPathException;

    boolean isVarDeclared(QName qname);

    Map<QName, Variable> getVariables();

    Map<QName, Variable> getLocalVariables();

    Map<QName, Variable> getGlobalVariables();

    /**
     * Turn on/off XPath 1.0 backwards compatibility.
     *
     * If turned on, comparison expressions will behave like in XPath 1.0, i.e. if any one of the operands is a number,
     * the other operand will be cast to a double.
     *
     * @param backwardsCompatible true to enable XPath 1.0 backwards compatible mode.
     */
    void setBackwardsCompatibility(boolean backwardsCompatible);

    /**
     * XPath 1.0 backwards compatibility turned on?
     *
     * In XPath 1.0 compatible mode, additional conversions will be applied to values if a numeric value is expected.
     *
     * @return true if XPath 1.0 compatible mode is enabled.
     */
    boolean isBackwardsCompatible();

    boolean isRaiseErrorOnFailedRetrieval();

    /**
     * Get the DBBroker instance used for the current query.
     *
     * The DBBroker is the main database access object, providing access to all internal database functions.
     *
     * @return DBBroker instance
     */
    DBBroker getBroker();

    /**
     * Get the subject which executes the current query.
     *
     * @return subject
     */
    Subject getSubject();

    /**
     * Get the document builder currently used for creating temporary document fragments.
     * A new document builder will be created on demand.
     *
     * @return document builder
     */
    MemTreeBuilder getDocumentBuilder();

    MemTreeBuilder getDocumentBuilder(boolean explicitCreation);

    /**
     * Returns the shared name pool used by all in-memory documents which are created within this query context.
     * Create a name pool for every document would be a waste of memory, especially since it is likely that the
     * documents contain elements or attributes with similar names.
     *
     * @return the shared name pool
     */
    NamePool getSharedNamePool();

    XQueryContext getContext();

    void prologEnter(Expression expr);

    void expressionStart(Expression expr) throws TerminatedException;

    void expressionEnd(Expression expr);

    void stackEnter(Expression expr) throws TerminatedException;

    void stackLeave(Expression expr);

    void proceed() throws TerminatedException;

    void proceed(Expression expr) throws TerminatedException;

    void proceed(Expression expr, MemTreeBuilder builder) throws TerminatedException;

    void setWatchDog(XQueryWatchDog watchdog);

    XQueryWatchDog getWatchDog();

    /**
     * Push any document fragment created within the current execution context on the stack.
     */
    void pushDocumentContext();

    /**
     * Pop the last document fragment created within the current execution context off the stack.
     */
    void popDocumentContext();

    /**
     * Set the base URI for the evaluation context.
     *
     * This is the URI returned by the {@code fn:base-uri()} function.
     *
     * @param uri the base URI
     */
    void setBaseURI(AnyURIValue uri);

    /**
     * Set the base URI for the evaluation context.
     *
     * A base URI specified via the base-uri directive in the XQuery prolog overwrites any other setting.
     *
     * @param uri the base URI
     * @param setInProlog true if it was set by a declare option in the XQuery prolog
     */
    void setBaseURI(AnyURIValue uri, boolean setInProlog);

    /**
     * Set the path to a base directory where modules should be loaded from. Relative module paths will be resolved
     * against this directory. The property is usually set by the XQueryServlet or XQueryGenerator, but can also
     * be specified manually.
     *
     * @param path the module load path.
     */
    void setModuleLoadPath(String path);

    String getModuleLoadPath();

    /**
     * Returns true if the baseURI is declared.
     *
     * @return true if the baseURI is declared, false otherwise.
     */
    boolean isBaseURIDeclared();

    /**
     * Get the base URI of the evaluation context.
     *
     * This is the URI returned by the fn:base-uri() function.
     *
     * @return base URI of the evaluation context
     *
     * @throws XPathException if an error occurs
     */
    AnyURIValue getBaseURI() throws XPathException;

    /**
     * Set the current context position, i.e. the position of the currently processed item in the context sequence.
     * This value is required by some expressions, e.g. fn:position().
     *
     * @param pos the position
     * @param sequence the sequence
     */
    void setContextSequencePosition(int pos, Sequence sequence);

    /**
     * Get the current context position, i.e. the position of the currently processed item in the context sequence.
     *
     * @return current context position
     */
    int getContextPosition();

    Sequence getContextSequence();

    void pushInScopeNamespaces();

    /**
     * Push all in-scope namespace declarations onto the stack.
     *
     * @param inherit true if the current namespaces become inherited
     *                just like the previous inherited ones
     */
    @SuppressWarnings("unchecked")
    void pushInScopeNamespaces(boolean inherit);

    void popInScopeNamespaces();

    @SuppressWarnings("unchecked")
    void pushNamespaceContext();

    void popNamespaceContext();

    /**
     * Returns the last variable on the local variable stack. The current variable context can be restored by
     * passing the return value to {@link #popLocalVariables(LocalVariable)}.
     *
     * @param newContext true if there is a new context
     *
     * @return last variable on the local variable stack
     */
    LocalVariable markLocalVariables(boolean newContext);

    /**
     * Restore the local variable stack to the position marked by variable {@code var}.
     *
     * @param var only clear variables after this variable, or null
     */
    void popLocalVariables(@Nullable LocalVariable var);

    /**
     * Returns the current size of the stack. This is used to determine where a variable has been declared.
     *
     * @return current size of the stack
     */
    int getCurrentStackSize();

    /**
     * Report the start of a function execution. Adds the reported function signature to the function call stack.
     *
     * @param signature the function signature
     */
    void functionStart(FunctionSignature signature);

    /**
     * Report the end of the currently executed function. Pops the last function signature from the function call stack.
     */
    void functionEnd();

    /**
     * Check if the specified function signature is found in the current function called stack.
     * If yes, the function might be tail recursive and needs
     * to be optimized.
     *
     * @param signature the function signature
     *
     * @return true if the function call is tail recursive
     */
    boolean tailRecursiveCall(FunctionSignature signature);

    /**
     * Import one or more library modules into the function signatures and in-scope variables of the importing module.
     *
     * The prefix and location parameters are optional.
     * If prefix is null, the first default prefix specified by the module(s) is used.
     * If locationHints are empty or null, the module(s) may be read from the namespace URI.
     *
     * @param namespaceURI the namespace URI of the module(s)
     * @param prefix the namespace prefix of the module(s), or null
     * @param locationHints hints as to the location of the module(s), or null
     *
     * @return the imported module(s)
     *
     * @throws XPathException if an error occurs whilst importing the module, with the error codes:
     *      XPST0003
     *      XQST0033
     *      XQST0046
     *      XQST0059
     *      XQST0070
     *      XQST0088
     */
    @Nullable Module[] importModule(@Nullable String namespaceURI, @Nullable String prefix, @Nullable AnyURIValue[] locationHints) throws XPathException;

    /**
     * Returns the static location mapped to an XQuery source module, if known.
     *
     * @param namespaceURI the URI of the module
     *
     * @return the location string
     */
    String getModuleLocation(String namespaceURI);

    /**
     * Returns an iterator over all module namespace URIs which are statically mapped to a known location.
     *
     * @return an iterator
     */
    Iterator<String> getMappedModuleURIs();

    /**
     * Add a forward reference to an undeclared function. Forward references will be resolved later.
     *
     * @param call the undeclared function
     */
    void addForwardReference(FunctionCall call);

    /**
     * Resolve all forward references to previously undeclared functions.
     *
     * @throws XPathException of an exception occurs whilst resolving the forward references
     */
    void resolveForwardReferences() throws XPathException;

    boolean optimizationsEnabled();

    /**
     * Add a static compile-time option i.e. declare option
     *
     * @param name  the name of the option
     * @param value the value of the option
     *
     * @throws XPathException of an exception occurs whilst adding the option
     */
    void addOption(String name, String value) throws XPathException;

    /**
     * Add a dynamic run-time option i.e. util:declare-option
     *
     * @param name  the name of the dynamic option
     * @param value the value of the dynamic option
     *
     * @throws XPathException if an exception occurs whilst adding the dynamic option
     */
    void addDynamicOption(String name, String value) throws XPathException;

    /**
     * Get dynamic options that were declared at run-time
     * first as these have precedence, and then if not found
     * get static options that were declare at compile time
     *
     * @param qname option name
     * @return the option
     */
    Option getOption(QName qname);

    Pragma getPragma(String name, String contents) throws XPathException;

    /**
     * Store the supplied in-memory document to a temporary document fragment.
     *
     * @param doc the in-memory document
     * @return The temporary document
     *
     * @throws XPathException if an exception occurs whilst storing the temporary document
     */
    DocumentImpl storeTemporaryDoc(org.exist.dom.memtree.DocumentImpl doc) throws XPathException;

    void setAttribute(String attribute, Object value);

    Object getAttribute(String attribute);

    void registerUpdateListener(UpdateListener listener);

    /**
     * Check if the XQuery contains options that define serialization settings. If yes,
     * copy the corresponding settings to the current set of output properties.
     *
     * @param properties the properties object to which serialization parameters will be added.
     *
     * @throws XPathException if an error occurs while parsing the option
     */
    void checkOptions(Properties properties) throws XPathException;

    void setDebuggeeJoint(DebuggeeJoint joint);

    DebuggeeJoint getDebuggeeJoint();

    boolean isDebugMode();

    boolean requireDebugMode();

    void registerBinaryValueInstance(BinaryValue binaryValue);

    void runCleanupTasks(final Predicate<Object> predicate);

}
