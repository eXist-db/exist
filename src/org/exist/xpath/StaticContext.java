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
package org.exist.xpath;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.memtree.MemTreeBuilder;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.xpath.functions.UserDefinedFunction;
import org.exist.xpath.value.Sequence;

public class StaticContext {

	public final static String XML_NS = "http://www.w3.org/XML/1998/namespace";
	public final static String SCHEMA_NS = "http://www.w3.org/2001/XMLSchema";
	public final static String SCHEMA_DATATYPES_NS = "http://www.w3.org/2001/XMLSchema-datatypes";
	public final static String SCHEMA_INSTANCE_NS = "http://www.w3.org/2001/XMLSchema-instance";
	public final static String XPATH_DATATYPES_NS = "http://www.w3.org/2003/05/xpath-datatypes";
	public final static String XQUERY_LOCAL_NS = "http://www.w3.org/2003/08/xquery-local-functions";
	
	private HashMap namespaces;
	private HashMap inScopeNamespaces = new HashMap();
	private Stack namespaceStack = new Stack();
	
	private TreeMap builtinFunctions;
	private TreeMap declaredFunctions = new TreeMap();

	/** TODO: don't put global variables here */
	private TreeMap variables = new TreeMap();
	private Stack variableStack = new Stack();
	
	private DBBroker broker;
	private String baseURI = "";

	private String defaultFunctionNamespace = Function.BUILTIN_FUNCTION_NS;
	
	/**
	 * Set to true to enable XPath 1.0
	 * backwards compatibility.
	 */
	private boolean backwardsCompatible = true;

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

	public StaticContext(DBBroker broker) {
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
	}

	public void declareInScopeNamespace(String prefix, String uri) {
		if (prefix == null || uri == null)
			throw new IllegalArgumentException("null argument passed to declareNamespace");
		if (inScopeNamespaces == null)
			inScopeNamespaces = new HashMap();
		inScopeNamespaces.put(prefix, uri);
	}

	public String getDefaultFunctionNamespace() {
		return defaultFunctionNamespace;
	}
	
	public void setDefaultFunctionNamespace(String uri) {
		defaultFunctionNamespace = uri;
	}
	
	/**
	 * Returns a namespace URI for the prefix or
	 * null if no such prefix exists.
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
	
	public String getPrefixForURI(String uri) {
		for(Iterator i = namespaces.entrySet().iterator(); i.hasNext(); ) {
			Map.Entry entry = (Map.Entry)i.next();
			if(entry.getValue().equals(uri))
				return (String)entry.getKey();
		}
		if(inScopeNamespaces != null) {
			for(Iterator i = inScopeNamespaces.entrySet().iterator(); i.hasNext(); ) {
				Map.Entry entry = (Map.Entry)i.next();
				if(entry.getValue().equals(uri))
					return (String)entry.getKey();
			}
		}
		return null;
	}

	/**
	 * Removes the namespace URI from the prefix/namespace 
	 * mappings table.
	 * 
	 * @param uri
	 */
	public void removeNamespace(String uri) {
		for (Iterator i = namespaces.values().iterator(); i.hasNext();) {
			if (((String) i.next()).equals(uri)) {
				i.remove();
				return;
			}
		}
		if (inScopeNamespaces != null) {
			for (Iterator i = inScopeNamespaces.values().iterator();
				i.hasNext();
				) {
				if (((String) i.next()).equals(uri)) {
					i.remove();
					return;
				}
			}
		}
	}

	/**
	 * Clear all user-defined prefix/namespace mappings.
	 *
	 */
	public void clearNamespaces() {
		namespaces.clear();
		if (inScopeNamespaces != null)
			inScopeNamespaces.clear();
		loadDefaults();
	}

	/**
	 * Find the implementing class for a function name.
	 * 
	 * @param fnName
	 * @return
	 */
	public Class getClassForFunction(QName fnName) {
		return (Class) builtinFunctions.get(fnName);
	}

	public void declareFunction(UserDefinedFunction function)  throws XPathException {
		declaredFunctions.put(function.getName(), function);
	}
	
	public UserDefinedFunction resolveFunction(QName name) throws XPathException {
		UserDefinedFunction func = (UserDefinedFunction)declaredFunctions.get(name);
		if(func == null)
			throw new XPathException("Function " + name + " is unknown");
		return func;
	}
	
	public Iterator getBuiltinFunctions() {
		return builtinFunctions.values().iterator();
	}
	
	/**
	 * Declare a variable.
	 * 
	 * @param var
	 * @return
	 * @throws XPathException
	 */
	public Variable declareVariable(Variable var) throws XPathException {
		variables.put(var.getQName(), var);
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
	public Variable declareVariable(String qname, Object value)
		throws XPathException {
		Sequence val = XPathUtil.javaObjectToXPath(value);
		QName qn = QName.parse(this, qname);
		Variable var = new Variable(qn);
		var.setValue(val);
		variables.put(qn, var);
		return var;
	}

	/**
	 * Try to resolve a variable.
	 * 
	 * @param qname the qualified name of the variable
	 * @return the declared Variable object
	 * @throws XPathException if the variable is unknown
	 */
	public Variable resolveVariable(String qname) throws XPathException {
		QName qn = QName.parse(this, qname);
		Variable var = (Variable) variables.get(qn);
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
			builder = new MemTreeBuilder();
			builder.startDocument();
		}
		return builder;
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
	
	public int getContextPosition() {
		return contextPosition;
	}
	
	public void pushNamespaceContext() {
		HashMap m = (HashMap)inScopeNamespaces.clone();
		namespaceStack.push(inScopeNamespaces);
		inScopeNamespaces = m;
	}
	
	public void popNamespaceContext() {
		inScopeNamespaces = (HashMap)namespaceStack.pop();
	}
	
	/**
	 * Save the current context on top of a stack. 
	 * 
	 * Use {@link popContext()} to restore the current state.
	 * This method saves the current in-scope namespace
	 * definitions and variables.
	 */
	public void pushLocalContext(boolean emptyContext) {
		variableStack.push(variables);
		if(emptyContext)
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
	 * Load the default prefix/namespace mappings table and set up
	 * internal functions.
	 */
	private void loadDefaults() {
		SymbolTable syms = DBBroker.getSymbols();
		String[] prefixes = syms.defaultPrefixList();
		namespaces = new HashMap(prefixes.length);
		for (int i = 0; i < prefixes.length; i++) {
			namespaces.put(prefixes[i], syms.getDefaultNamespace(prefixes[i]));
		}
		
		// default namespaces
		declareNamespace("xml", XML_NS);
		declareNamespace("xs", SCHEMA_NS);
		declareNamespace("xdt", XPATH_DATATYPES_NS);
		declareNamespace("local", XQUERY_LOCAL_NS);
		declareNamespace("fn", Function.BUILTIN_FUNCTION_NS);
		declareNamespace("util", Function.UTIL_FUNCTION_NS);
		declareNamespace("xmldb", Function.XMLDB_FUNCTION_NS);
		
		builtinFunctions = new TreeMap();
		for (int i = 0; i < SystemFunctions.internalFunctions.length; i++) {
			try {
				Class fclass = lookup(SystemFunctions.internalFunctions[i]);
				Field field = fclass.getDeclaredField("signature");
				FunctionSignature signature = (FunctionSignature)field.get(null);
				QName name = signature.getName();
				builtinFunctions.put(name, fclass);
			} catch (Exception e) {
				throw new RuntimeException("no instance found for " + SystemFunctions.internalFunctions[i]);
			}
		}
	}
	
	private Class lookup(String clazzName) throws ClassNotFoundException {
		return Class.forName(clazzName);
	}
}
