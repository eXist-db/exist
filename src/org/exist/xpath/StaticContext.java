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
import java.util.Stack;
import java.util.TreeMap;

import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.memtree.MemTreeBuilder;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.xpath.functions.Function;
import org.exist.xpath.functions.FunctionSignature;
import org.exist.xpath.value.Sequence;

public class StaticContext {

	protected static final String[] internalFunctions =
		{ 
			"org.exist.xpath.functions.FunSubstring",
			"org.exist.xpath.functions.FunSubstringBefore",
			"org.exist.xpath.functions.FunSubstringAfter",
			"org.exist.xpath.functions.FunNormalizeSpace",
			"org.exist.xpath.functions.FunConcat",
			"org.exist.xpath.functions.FunStartsWith",
			"org.exist.xpath.functions.FunEndsWith",
			"org.exist.xpath.functions.FunContains",
			"org.exist.xpath.functions.FunNot",
			"org.exist.xpath.functions.FunPosition",
			"org.exist.xpath.functions.FunLast",
			"org.exist.xpath.functions.FunCount",
			"org.exist.xpath.functions.FunStrLength",
			"org.exist.xpath.functions.FunBoolean",
			"org.exist.xpath.functions.FunString",
			"org.exist.xpath.functions.FunNumber",
			"org.exist.xpath.functions.FunTrue",
			"org.exist.xpath.functions.FunFalse",
			"org.exist.xpath.functions.FunSum",
			"org.exist.xpath.functions.FunFloor",
			"org.exist.xpath.functions.FunCeiling",
			"org.exist.xpath.functions.FunRound",
			"org.exist.xpath.functions.FunName",
			"org.exist.xpath.functions.FunLocalName",
			"org.exist.xpath.functions.FunNamespaceURI",
			"org.exist.xpath.functions.FunId",
			"org.exist.xpath.functions.FunLang",
			"org.exist.xpath.functions.FunBaseURI",
			"org.exist.xpath.functions.FunDocumentURI",
			"org.exist.xpath.functions.ExtRegexp",
			"org.exist.xpath.functions.ExtRegexpOr",
			"org.exist.xpath.functions.FunDistinctValues",
			"org.exist.xpath.functions.xmldb.XMLDBCollection",
			"org.exist.xpath.functions.xmldb.XMLDBStore",
			"org.exist.xpath.functions.xmldb.XMLDBRegisterDatabase",
			"org.exist.xpath.functions.xmldb.XMLDBCreateCollection",
			"org.exist.xpath.functions.util.MD5"
		};

	private HashMap namespaces;
	private TreeMap functions;
	private TreeMap variables;
	private DBBroker broker;
	private String baseURI = "";

	private HashMap inScopeNamespaces = null;

	private Stack stack = new Stack();

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
		variables = new TreeMap();
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
		System.out.println(prefix + " = " + uri);
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
		return (Class) functions.get(fnName);
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
	
	/**
	 * Save the current context on top of a stack. 
	 * 
	 * Use {@link popContext()} to restore the current state.
	 * This method saves the current in-scope namespace
	 * definitions and variables.
	 */
	public void pushContext() {
		stack.push(inScopeNamespaces);
		if (inScopeNamespaces != null) {
			HashMap m = new HashMap(inScopeNamespaces);
			inScopeNamespaces = m;
		}
	}

	/**
	 * Restore previous state.
	 */
	public void popContext() {
		if (!stack.isEmpty())
			inScopeNamespaces = (HashMap) stack.pop();
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
		
		functions = new TreeMap();
		for (int i = 0; i < internalFunctions.length; i++) {
			try {
				Class fclass = lookup(internalFunctions[i]);
				Field field = fclass.getDeclaredField("signature");
				FunctionSignature signature = (FunctionSignature)field.get(null);
				QName name = signature.getName();
				functions.put(name, fclass);
			} catch (Exception e) {
				throw new RuntimeException("no instance found for " + internalFunctions[i]);
			}
		}
	}
	
	private Class lookup(String clazzName) throws ClassNotFoundException {
		return Class.forName(clazzName);
	}
}
