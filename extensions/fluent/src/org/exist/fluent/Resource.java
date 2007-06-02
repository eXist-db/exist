package org.exist.fluent;

import org.exist.xquery.value.Sequence;

/**
 * A database object that can be further queried.
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public abstract class Resource {
	
	protected final Database db;
	protected final NamespaceMap namespaceBindings;
	private QueryService query;
	
	protected Resource(NamespaceMap namespaceBindings, Database db) {
		this.db = db;
		this.namespaceBindings = namespaceBindings;
	}
	
	/**
	 * Return a query service for running queries in the context of this resource.
	 * The query service will inherit this object's namespace bindings.
	 *
	 * @return a query service with this object as context
	 */
	public QueryService query() {
		if (query == null) {
			query = createQueryService();
			if (query == null) {
				Sequence seq = convertToSequence();
				query = new QueryService(this, seq.getDocumentSet(), seq);
			}
		}
		return query;
	}

	/**
	 * Return the namespace bindings for this resource.  These mappings are applied to
	 * all queries and other objects derived from this one, directly or indirectly.  The
	 * default namespace mapping uses an empty string key. 
	 *
	 * @return the namespace mappings for this collection
	 */
	public final NamespaceMap namespaceBindings() {
		return namespaceBindings;
	}
	
	/**
	 * Return the database to which this resource belongs.  The returned database will inherit
	 * its namespace bindings from this resource.
	 *
	 * @return the database that contains this object
	 */
	public final Database database() {
		return new Database(db, namespaceBindings);
	}
	
	/**
	 * Create a query service facet for this resource.  Override this method to create a custom
	 * query service.  If this method returns <code>null</code>, a default query service will
	 * be created based on this object's {@link #convertToSequence()} result.
	 *
	 * @return a new query service or <code>null</code>
	 */
	QueryService createQueryService() {
		return null;
	}
	
	/**
	 * Return a representation of this resource that will work with eXist's query engine.
	 *
	 * @return an eXist-compatible representation of this object
	 * @throws UnsupportedOperationException if conversion is not supported
	 */
	abstract Sequence convertToSequence();
}
