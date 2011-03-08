package org.exist.interpreter;

import java.util.Map;

import org.exist.util.hashtable.NamePool;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AnyURIValue;

public interface ContextNS {

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
	 * Returns the shared name pool used by all in-memory documents which are created within this query context. Create a name pool for every document
	 * would be a waste of memory, especially since it is likely that the documents contain elements or attributes with similar names.
	 *
	 * @return  the shared name pool
	 */
	public NamePool getSharedNamePool();

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

	public void pushInScopeNamespaces();

	/**
	 * Push all in-scope namespace declarations onto the stack.
	 *
	 * @param  inherit  
	 */
	public void pushInScopeNamespaces(boolean inherit);

	public void popInScopeNamespaces();

	public void pushNamespaceContext();

	public void popNamespaceContext();
}