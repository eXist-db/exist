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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.TreeMap;

import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.memtree.MemTreeBuilder;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.xpath.value.Sequence;

public class StaticContext {

	protected static final String[][] internalFunctions =
		{ { "substring", "org.exist.xpath.functions.FunSubstring" }, {
			"substring-before",
				"org.exist.xpath.functions.FunSubstringBefore" },
				{
			"substring-after",
				"org.exist.xpath.functions.FunSubstringAfter" },
				{
			"normalize-space",
				"org.exist.xpath.functions.FunNormalizeString" },
				{
			"concat", "org.exist.xpath.functions.FunConcat" }, {
			"starts-with", "org.exist.xpath.functions.FunStartsWith" }, {
			"ends-with", "org.exist.xpath.functions.FunEndsWith" }, {
			"contains", "org.exist.xpath.functions.FunContains" }, {
			"not", "org.exist.xpath.functions.FunNot" }, {
			"position", "org.exist.xpath.functions.FunPosition" }, {
			"last", "org.exist.xpath.functions.FunLast" }, {
			"count", "org.exist.xpath.functions.FunCount" }, {
			"string-length", "org.exist.xpath.functions.FunStrLength" }, {
			"boolean", "org.exist.xpath.functions.FunBoolean" }, {
			"string", "org.exist.xpath.functions.FunString" }, {
			"number", "org.exist.xpath.functions.FunNumber" }, {
			"true", "org.exist.xpath.functions.FunTrue" }, {
			"false", "org.exist.xpath.functions.FunFalse" }, {
			"sum", "org.exist.xpath.functions.FunSum" }, {
			"floor", "org.exist.xpath.functions.FunFloor" }, {
			"ceiling", "org.exist.xpath.functions.FunCeiling" }, {
			"round", "org.exist.xpath.functions.FunRound" }, {
			"name", "org.exist.xpath.functions.FunName" }, {
			"local-name", "org.exist.xpath.functions.FunLocalName" }, {
			"namespace-uri", "org.exist.xpath.functions.FunNamespaceURI" }, {
			"match-any", "org.exist.xpath.functions.FunKeywordMatchAny" }, {
			"match-all", "org.exist.xpath.functions.FunKeywordMatchAll" }, {
			"id", "org.exist.xpath.functions.FunId" }, {
			"lang", "org.exist.xpath.functions.FunLang" }, {
			"base-uri", "org.exist.xpath.functions.FunBaseURI" }, {
			"document-uri", "org.exist.xpath.functions.FunDocumentURI" },
			{ "match-all", "org.exist.xpath.functions.ExtRegexp" },
			{ "match-any", "org.exist.xpath.functions.ExtRegexpOr" },
			{ "distinct-values", "org.exist.xpath.functions.FunDistinctValues" }
	};

	private HashMap namespaces;
	private HashMap functions;
	private TreeMap variables;
	private DBBroker broker;
	private String baseURI = "";

	private HashMap inScopeNamespaces = null;

	private Stack stack = new Stack();

	/**
	 * Set to true to enable XPath 1.0
	 * backwards compatibility.
	 */
	private boolean backwardsCompatible = true;

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
		namespaces.put(prefix, uri);
	}

	public void declareInScopeNamespace(String prefix, String uri) {
		if (prefix == null || uri == null)
			throw new IllegalArgumentException("null argument passed to declareNamespace");
		if (inScopeNamespaces == null)
			inScopeNamespaces = new HashMap();
		inScopeNamespaces.put(prefix, uri);
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
	public String getClassForFunction(String fnName) {
		return (String) functions.get(fnName);
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
	 * @return
	 */
	public boolean isBackwardsCompatible() {
		return this.backwardsCompatible;
	}

	/**
	 * Get the DBBroker instance used for the current query.
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

	public MemTreeBuilder getDocumentBuilder() {
		if (builder == null) {
			builder = new MemTreeBuilder();
			builder.startDocument();
		}
		return builder;
	}

	public void setBaseURI(String uri) {
		baseURI = uri;
	}

	public String getBaseURI() {
		return baseURI;
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

		functions = new HashMap(internalFunctions.length);
		for (int i = 0; i < internalFunctions.length; i++)
			functions.put(internalFunctions[i][0], internalFunctions[i][1]);
	}
}
