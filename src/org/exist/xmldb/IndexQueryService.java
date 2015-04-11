/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
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
}
