/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.xquery.functions.transform.ModuleImpl;
import org.exist.xquery.value.Sequence;

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

	public final static String XML_NS = "http://www.w3.org/XML/1998/namespace";
	public final static String SCHEMA_NS = "http://www.w3.org/2001/XMLSchema";
	public final static String SCHEMA_DATATYPES_NS =
		"http://www.w3.org/2001/XMLSchema-datatypes";
	public final static String SCHEMA_INSTANCE_NS =
		"http://www.w3.org/2001/XMLSchema-instance";
	public final static String XPATH_DATATYPES_NS =
		"http://www.w3.org/2003/05/xpath-datatypes";
	public final static String XQUERY_LOCAL_NS =
		"http://www.w3.org/2003/08/xquery-local-functions";

	private final static Logger LOG = Logger.getLogger(XQueryContext.class);

	private HashMap namespaces;
	private HashMap inScopeNamespaces = new HashMap();

	private HashMap prefixes;
	private HashMap inScopePrefixes = new HashMap();

	private Stack namespaceStack = new Stack();

	private TreeMap declaredFunctions = new TreeMap();

	private TreeMap globalVariables = new TreeMap();
	private TreeMap variables = new TreeMap();
	private Stack variableStack = new Stack();

	private Stack forwardReferences = new Stack();
	
	/**
	 * Loaded modules.
	 */
	private HashMap modules = new HashMap();

	/** 
	 * The set of statically known documents.
	 */
	private DocumentSet staticDocuments = null;

	private DBBroker broker;

	private String baseURI = "";

	private String moduleLoadPath = ".";
	
	private String defaultFunctionNamespace = Function.BUILTIN_FUNCTION_NS;

	/**
	 * Set to true to enable XPath 1.0
	 * backwards compatibility.
	 */
	private boolean backwardsCompatible = true;

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
	private Stack fragmentStack = new Stack();

	public XQueryContext(DBBroker broker) {
		this.broker = broker;
		loadDefaults();
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
	public void declareNamespace(String prefix, String uri) {
		if (prefix == null || uri == null)
			throw new IllegalArgumentException("null argument passed to declareNamespace");
		namespaces.put(prefix, uri);
		prefixes.put(uri, prefix);
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
	 * @return
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
	public void setDefaultFunctionNamespace(String uri) {
		defaultFunctionNamespace = uri;
	}

	/**
	 * Return the namespace URI mapped to the registered prefix
	 * or null if the prefix is not registered.
	 * 
	 * @param prefix
	 * @return
	 */
	public String getURIForPrefix(String prefix) {
		String ns = (String) namespaces.get(prefix);
		if (ns == null)
			// try in-scope namespace declarations
			return inScopeNamespaces == null
				? null
				: (String) inScopeNamespaces.get(prefix);
		else
			return ns;
	}

	/**
	 * Return the prefix mapped to the registered URI or
	 * null if the URI is not registered.
	 * 
	 * @param uri
	 * @return
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
		loadDefaults();
	}

	/**
	 * Set the set of statically known documents for the current
	 * execution context. These documents will be processed if
	 * no explicit document set has been set for the current expression
	 * with fn:doc() or fn:collection().
	 * 
	 * @param docs
	 */
	public void setStaticallyKnownDocuments(DocumentSet docs) {
		staticDocuments = docs;
	}

	/**
	 * Get the set of statically known documents.
	 * 
	 * @return
	 */
	public DocumentSet getStaticallyKnownDocuments() {
		return staticDocuments;
	}

	/**
	 * Returns true if whitespace between constructed element nodes
	 * should be stripped by default.
	 * 
	 * @return
	 */
	public boolean stripWhitespace() {
		return stripWhitespace;
	}

	/**
	 * Return an iterator over all built-in modules currently
	 * registered.
	 * 
	 * @return
	 */
	public Iterator getModules() {
		return modules.values().iterator();
	}

	/**
	 * Get the built-in module registered for the given namespace
	 * URI.
	 * 
	 * @param namespaceURI
	 * @return
	 */
	public Module getModule(String namespaceURI) {
		return (Module) modules.get(namespaceURI);
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
	public void loadBuiltInModule(String namespaceURI, String moduleClass) {
		if (modules.containsKey(namespaceURI)) {
			LOG.debug("module " + namespaceURI + " is already present");
			return;
		}
		try {
			Class mClass = Class.forName(moduleClass);
			if (!(Module.class.isAssignableFrom(mClass))) {
				LOG.warn(
					"failed to load module. "
						+ moduleClass
						+ " is not an instance of org.exist.xpath.Module.");
				return;
			}
			Module module = (Module) mClass.newInstance();
			if (!module.getNamespaceURI().equals(namespaceURI)) {
				LOG.warn("the module declares a different namespace URI. Skipping...");
				return;
			}
			if (getPrefixForURI(module.getNamespaceURI()) == null
				&& module.getDefaultPrefix().length() > 0)
				declareNamespace(module.getDefaultPrefix(), module.getNamespaceURI());

			modules.put(module.getNamespaceURI(), module);
			LOG.debug("module " + module.getNamespaceURI() + " loaded successfully.");
		} catch (ClassNotFoundException e) {
			LOG.warn("module class " + moduleClass + " not found. Skipping...");
		} catch (InstantiationException e) {
			LOG.warn("error while instantiating module class " + moduleClass, e);
		} catch (IllegalAccessException e) {
			LOG.warn("error while instantiating module class " + moduleClass, e);
		}
	}

	/**
	 * Declare a user-defined function. All user-defined functions are kept
	 * in a single hash map.
	 * 
	 * @param function
	 * @throws XPathException
	 */
	public void declareFunction(UserDefinedFunction function) throws XPathException {
		declaredFunctions.put(function.getName(), function);
	}

	/**
	 * Resolve a user-defined function.
	 * 
	 * @param name
	 * @return
	 * @throws XPathException
	 */
	public UserDefinedFunction resolveFunction(QName name) throws XPathException {
		UserDefinedFunction func = (UserDefinedFunction) declaredFunctions.get(name);
		return func;
	}

	/**
	 * Declare a variable. This is called by variable binding expressions like
	 * "let" and "for".
	 * 
	 * @param var
	 * @return
	 * @throws XPathException
	 */
	public Variable declareVariable(Variable var) throws XPathException {
		variables.put(var.getQName(), var);
		var.setStackPosition(variableStack.size());
		return var;
	}

	/**
	 * Declare a global variable as by "declare variable".
	 * 
	 * @param qname
	 * @param value
	 * @return
	 * @throws XPathException
	 */
	public Variable declareGlobalVariable(Variable var) throws XPathException {
		globalVariables.put(var.getQName(), var);
		var.setStackPosition(variableStack.size());
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
		QName qn = QName.parse(this, qname);
		Variable var;
		Module module = getModule(qn.getNamespaceURI());
		if(module != null) {
			var = module.declareVariable(qn, value);
			return var;
		}
		Sequence val = XPathUtil.javaObjectToXPath(value);
		var = (Variable)globalVariables.get(qn);
		if(var == null) {
			var = new Variable(qn);
			globalVariables.put(qn, var);
		}
		var.setValue(val);
		return var;
	}

	/**
	 * Try to resolve a variable.
	 * 
	 * @param qname the qualified name of the variable as string
	 * @return the declared Variable object
	 * @throws XPathException if the variable is unknown
	 */
	public Variable resolveVariable(String name) throws XPathException {
		QName qn = QName.parse(this, name);
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
		// first, check if the variable is declared in a module
		Module module = (Module) modules.get(qname.getNamespaceURI());
		if(module != null) {
			var = module.resolveVariable(qname);
			if(var != null)
				return var;
		}
		var = (Variable) variables.get(qname);
		if (var == null) {
			var = (Variable) globalVariables.get(qname);
		}
		if (var == null)
			throw new XPathException("variable " + qname + " is not bound");
		return var;
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
	 * @return
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
	 * @return
	 */
	public DBBroker getBroker() {
		return broker;
	}

	/**
	 * Get the user which executes the current query.
	 * 
	 * @return
	 */
	public User getUser() {
		return broker.getUser();
	}

	/**
	 * Get the document builder currently used for creating
	 * temporary document fragments. A new document builder
	 * will be created on demand.
	 * 
	 * @return
	 */
	public MemTreeBuilder getDocumentBuilder() {
		if (builder == null) {
			builder = new MemTreeBuilder(this);
			builder.startDocument();
		}
		return builder;
	}

	/**
	 * Push any document fragment created within the current
	 * execution context on the stack.
	 */
	public void pushDocumentContext() {
		if (builder != null)
			fragmentStack.push(builder);
		builder = null;
	}

	public void popDocumentContext() {
		if (!fragmentStack.isEmpty())
			builder = (MemTreeBuilder) fragmentStack.pop();
	}

	/**
	 * Set the base URI for the evaluation context.
	 * 
	 * This is the URI returned by the fn:base-uri()
	 * function.
	 * 
	 * @param uri
	 */
	public void setBaseURI(String uri) {
		baseURI = uri;
	}

	public void setModuleLoadPath(String path) {
		this.moduleLoadPath = path;
	}
	
	/**
	 * Get the base URI of the evaluation context.
	 * 
	 * This is the URI returned by the fn:base-uri() function.
	 * 
	 * @return
	 */
	public String getBaseURI() {
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
	 * @return
	 */
	public int getContextPosition() {
		return contextPosition;
	}

	/**
	 * Push all in-scope namespace declarations onto the stack.
	 */
	public void pushNamespaceContext() {
		HashMap m = (HashMap) inScopeNamespaces.clone();
		namespaceStack.push(inScopeNamespaces);
		inScopeNamespaces = m;
	}

	public void popNamespaceContext() {
		inScopeNamespaces = (HashMap) namespaceStack.pop();
	}

	/**
	 * Save the current context on top of a stack. 
	 * 
	 * Use {@link popContext()} to restore the current state.
	 * This method saves the current in-scope variable
	 * definitions.
	 */
	public void pushLocalContext(boolean emptyContext) {
		variableStack.push(variables);
		if (emptyContext)
			variables = new TreeMap();
		else
			variables = new TreeMap(variables);
	}

	/**
	 * Restore previous state.
	 */
	public void popLocalContext() {
		variables = (TreeMap) variableStack.pop();
	}

	/**
	 * Returns the current size of the stack. This is used to determine
	 * where a variable has been declared.
	 * 
	 * @return
	 */
	public int getCurrentStackSize() {
		return variableStack.size();
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
	public void importModule(String namespaceURI, String prefix, String location)
		throws XPathException {
		if(location == null)
			location = namespaceURI;
		if(location.startsWith("java:")) {
			location = location.substring("java:".length());
			loadBuiltInModule(namespaceURI, location);
			if(prefix != null)
				declareNamespace(prefix, namespaceURI);
			return;
		}
		if(location.indexOf(':') < 0) {
			File f = new File(moduleLoadPath + File.separatorChar + location);
			if(!f.canRead()) {
				f = new File(location);
				if(!f.canRead())
					throw new XPathException("cannot read module source from file at " + f.getAbsolutePath());
			}
			location = f.toURI().toASCIIString();
		}
		LOG.debug("Loading module from " + location);
		InputStreamReader reader;
		try {
			URL url = new URL(location);
			reader = new InputStreamReader(url.openStream(), "UTF-8");
		} catch (MalformedURLException e) {
			throw new XPathException("source location for module " + namespaceURI + " should be a valid URL");
		} catch (UnsupportedEncodingException e) {
			throw new XPathException("unsupported source encoding");
		} catch (IOException e) {
			throw new XPathException("IO exception while loading module " + namespaceURI, e);
		}
		XQueryContext context = new XQueryContext(broker);
		context.setStaticallyKnownDocuments(getStaticallyKnownDocuments());
		
		XQueryLexer lexer = new XQueryLexer(reader);
		XQueryParser parser = new XQueryParser(lexer, false);
		XQueryTreeParser astParser = new XQueryTreeParser(context);
		try {
			parser.xpath();
			if (parser.foundErrors()) {
				LOG.debug(parser.getErrorMessage());
				throw new XPathException(
					"error found while loading module from " + location + ": "
						+ parser.getErrorMessage());
			}
			AST ast = parser.getAST();

			PathExpr path = new PathExpr(context);
			astParser.xpath(ast, path);
			if (astParser.foundErrors()) {
				throw new XPathException(
					"error found while loading module from " + location + ": "
						+ astParser.getErrorMessage(),
					astParser.getLastException());
			}
			
			ExternalModule module = astParser.getModule();
			if(module == null)
				throw new XPathException("source at " + location + " is not a valid module");
			if(!module.getNamespaceURI().equals(namespaceURI))
				throw new XPathException("namespace URI declared by module (" + module.getNamespaceURI() + 
					") does not match namespace URI in import statement, which was: " + namespaceURI);
			if(prefix == null)
				prefix = module.getDefaultPrefix();
			declareNamespace(prefix, namespaceURI);
			modules.put(module.getNamespaceURI(), module);
			context.modules.put(module.getNamespaceURI(), module);
		} catch (RecognitionException e) {
			throw new XPathException(
				"error found while loading module from " + location + ": " + e.getMessage(),
				e);
		} catch (TokenStreamException e) {
			throw new XPathException(
				"error found while loading module from " + location + ": " + e.getMessage(),
				e);
		}
	}

	public void addForwardReference(FunctionCall call) {
		forwardReferences.push(call);
	}
	
	public void resolveForwardReferences() throws XPathException {
		while(!forwardReferences.empty()) {
			FunctionCall call = (FunctionCall)forwardReferences.pop();
			UserDefinedFunction func = resolveFunction(call.getQName());
			if(func == null)
				throw new XPathException("Call to undeclared function: " + call.getQName().toString());
			call.resolveForwardReference(func);
		}
	}
	/**
	 * Load the default prefix/namespace mappings table and set up
	 * internal functions.
	 */
	private void loadDefaults() {
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

		// default namespaces
		declareNamespace("xml", XML_NS);
		declareNamespace("xs", SCHEMA_NS);
		declareNamespace("xdt", XPATH_DATATYPES_NS);
		declareNamespace("local", XQUERY_LOCAL_NS);
		declareNamespace("fn", Function.BUILTIN_FUNCTION_NS);

		// load built-in modules
		loadBuiltInModule(
			Function.BUILTIN_FUNCTION_NS,
			"org.exist.xquery.functions.ModuleImpl");
		loadBuiltInModule(
			Function.UTIL_FUNCTION_NS,
			"org.exist.xquery.functions.util.ModuleImpl");
		loadBuiltInModule(ModuleImpl.NAMESPACE_URI,
			"org.exist.xquery.functions.transform.ModuleImpl");
		loadBuiltInModule(
			Function.XMLDB_FUNCTION_NS,
			"org.exist.xquery.functions.xmldb.ModuleImpl");
		loadBuiltInModule(
			Function.REQUEST_FUNCTION_NS,
			"org.exist.xquery.functions.request.RequestModule");
	}
}
