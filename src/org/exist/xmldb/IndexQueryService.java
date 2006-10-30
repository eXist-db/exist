/*
 * eXist Open Source Native XML Database
 *   
 * Copyright (C) 2001-04 Wolfgang M. Meier wolfgang@exist-db.org
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 */
package org.exist.xmldb;

import org.exist.util.Occurrences;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

/**
 * Provides additional methods related to eXist's indexing system.
 * 
 * @author wolf
 *
 */
public interface IndexQueryService extends Service {

	public void configureCollection(String configData) throws XMLDBException;
	
    /**
     * Reindex the current collection, i.e. the collection from which
     * this service has been retrieved.
     * 
     * @throws XMLDBException
     */
    public void reindexCollection() throws XMLDBException;
    
    /**
     * Reindex the collection specified by its path.
     * 
     * @param collectionPath
     * @throws XMLDBException
     * @deprecated Use XmldbURI version instead
     */
    public void reindexCollection(String collectionPath) throws XMLDBException;
    
    /**
     * Reindex the collection specified by its path.
     * 
     * @param collectionPath
     * @throws XMLDBException
     */
    public void reindexCollection(XmldbURI collectionPath) throws XMLDBException;
    
    /**
     * Returns frequency statistics on all elements and attributes contained in the
     * structure index for the current collection.
     * 
     * @param inclusive
     * @throws XMLDBException
     */
	public Occurrences[] getIndexedElements(boolean inclusive) throws XMLDBException;
	
	/**
	 * Queries the fulltext index to retrieve information on indexed words contained
	 * in the index for the current collection. Returns a list of {@link Occurrences} for all 
	 * words contained in the index. If param end is null, all words starting with 
	 * the string sequence param start are returned. Otherwise, the method 
	 * returns all words that come after start and before end in lexical order.
	 * 
	 * @param start
	 * @param end
	 * @param inclusive
	 * @throws XMLDBException
	 */
	public Occurrences[] scanIndexTerms(String start, String end, 
	boolean inclusive) throws XMLDBException;
	
	/**
     * Queries the fulltext index to retrieve information on indexed words occurring within
     * the set of nodes identified by a given XPath expression. Returns a list of {@link Occurrences} for all 
     * words contained in the index. If param end is null, all words starting with 
     * the string sequence param start are returned. Otherwise, the method 
     * returns all words that come after start and before end in lexical order.
     * 
     * 
     * @param xpath 
     * @param start 
     * @param end 
     * @throws XMLDBException 
     */
	public Occurrences[] scanIndexTerms(
			String xpath,
			String start,
			String end)
			throws XMLDBException;
}
	
