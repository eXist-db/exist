package org.exist.memtree;

import org.exist.xquery.XQueryContext;

/**
 * Interface to create a new in-memory document using a {@link MemTreeBuilder}.
 * 
 * @see XQueryContext#createDocument(DocBuilder)
 * @author wolf
 *
 */
public interface DocBuilder {

	public void build(MemTreeBuilder builder);
}
