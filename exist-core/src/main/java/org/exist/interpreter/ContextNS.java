package org.exist.interpreter;

import java.util.Map;

import org.exist.util.hashtable.NamePool;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AnyURIValue;

public interface ContextNS {

	/**
	 * Declare a user-defined static prefix/namespace mapping.
	 *
	 * eXist internally keeps a table containing all prefix/namespace mappings it found in documents, which have been previously stored into the
	 * database. These default mappings need not to be declared explicitely.
	 *
	 * @param prefix the namespace prefix.
	 * @param uri the namespace URI.
	 *
	 * @throws XPathException if an error occurs whilst declaring the namespace.
	 */
	public void declareNamespace(String prefix, String uri) throws XPathException;

	public void declareNamespaces(Map<String, String> namespaceMap);

	/**
	 * Removes the namespace URI from the prefix/namespace mappings table.
	 *
	 * @param uri the namespace URI.
	 */
	public void removeNamespace(String uri);

	/**
	 * Declare an in-scope namespace. This is called during query execution.
	 *
	 * @param prefix the namespace prefix.
	 * @param uri the namespace URI.
	 */
	public void declareInScopeNamespace(String prefix, String uri);

	public String getInScopeNamespace(String prefix);

	public String getInScopePrefix(String uri);

	public String getInheritedNamespace(String prefix);

	public String getInheritedPrefix(String uri);

	/**
	 * Return the namespace URI mapped to the registered prefix or null if the prefix is not registered.
	 *
	 * @param prefix the namespace prefix.
	 *
	 * @return namespace
	 */
	public String getURIForPrefix(String prefix);

	/**
	 * Get URI Prefix
	 *
	 * @param uri the namespace URI.
	 *
	 * @return the prefix mapped to the registered URI or null if the URI is not registered.
	 */
	public String getPrefixForURI(String uri);

	/**
	 * Returns the current default function namespace.
	 *
	 * @return current default function namespace
	 */
	public String getDefaultFunctionNamespace();

	/**
	 * Set the default function namespace. By default, this points to the namespace for XPath built-in functions.
	 *
	 * @param uri the namespace URI.
	 *
	 * @throws XPathException if an error occurs whilst setting the default function namespace.
	 */
	public void setDefaultFunctionNamespace(String uri) throws XPathException;

	/**
	 * Returns the current default element namespace.
	 *
	 * @return current default element namespace schema
	 *
	 * @throws XPathException if an error occurs whilst getting the default element namespace schema.
	 */
	public String getDefaultElementNamespaceSchema() throws XPathException;

	/**
	 * Set the default element namespace. By default, this points to the empty uri.
	 *
	 * @param uri the namespace URI.
	 *
	 * @throws XPathException if an error occurs whilst setting the default element namespace schema.
	 */
	public void setDefaultElementNamespaceSchema(String uri) throws XPathException;

	/**
	 * Returns the current default element namespace.
	 *
	 * @return current default element namespace
	 *
	 * @throws XPathException if an error occurs whilst getting the default element namespace.
	 */
	public String getDefaultElementNamespace() throws XPathException;

	/**
	 * Set the default element namespace. By default, this points to the empty uri.
	 *
	 * @param uri the namespace URI.
	 * @param schema the schema
	 *
	 * @throws XPathException if an error occurs whilst getting the default element namespace.
	 */
	public void setDefaultElementNamespace(String uri, String schema) throws XPathException;

	/**
	 * Returns true if namespaces for constructed element and document nodes should be preserved on copy by default.
	 *
	 * @return true if namespaces are preserved, false otherwise.
	 */
	public boolean preserveNamespaces();

	/**
	 * Set whether namespaces should be preserved.
	 *
	 * @param preserve true if namespaces should be preserved, false otherwise.
	 */
	public void setPreserveNamespaces(final boolean preserve);

	/**
	 * Returns true if namespaces for constructed element and document nodes should be inherited on copy by default.
	 *
	 * @return true if namespaces are inheirted, false otherwise.
	 */
	public boolean inheritNamespaces();

	/**
	 * Set to true if namespaces for constructed element and document nodes should be inherited on copy by default.
	 *
	 * @param inherit true if namespaces are inheirted, false otherwise.
	 */
	public void setInheritNamespaces(final boolean inherit);

	/**
	 * Returns the shared name pool used by all in-memory documents which are created within this query context. Create a name pool for every document
	 * would be a waste of memory, especially since it is likely that the documents contain elements or attributes with similar names.
	 *
	 * @return the shared name pool
	 */
	public NamePool getSharedNamePool();

	/**
	 * Set the base URI for the evaluation context.
	 *
	 * This is the URI returned by the fn:base-uri() function.
	 *
	 * @param uri the namespace URI.
	 */
	public void setBaseURI(AnyURIValue uri);

	/**
	 * Set the base URI for the evaluation context.
	 *
	 * A base URI specified via the base-uri directive in the XQuery prolog overwrites any other setting.
	 *
	 * @param uri the namespace URI.
	 * @param setInProlog true if the base-uri was defined in the prolog.
	 */
	public void setBaseURI(AnyURIValue uri, boolean setInProlog);

	/**
	 * Determine if the base-uri is declared.
	 *
	 * @return true if the base-uri is declared, false otherwise.
	 */
	public boolean isBaseURIDeclared();

	/**
	 * Get the base URI of the evaluation context.
	 *
	 * This is the URI returned by the fn:base-uri() function.
	 *
	 * @return base URI of the evaluation context
	 *
	 * @throws XPathException if an error occurs whilst setting the base-uri
	 */
	public AnyURIValue getBaseURI() throws XPathException;

	public void pushInScopeNamespaces();

	/**
	 * Push all in-scope namespace declarations onto the stack.
	 *
	 * @param  inherit true if namespaces should be inheirted when pushing
	 */
	public void pushInScopeNamespaces(boolean inherit);

	public void popInScopeNamespaces();

	public void pushNamespaceContext();

	public void popNamespaceContext();
}