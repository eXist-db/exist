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
package org.exist.storage;

import org.exist.collections.Collection;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.TextImpl;
import org.exist.storage.btree.DBException;
import org.exist.util.ReadOnlyException;

/** Receives callback event during document(s) loading and removal;
 * implemented by several classes that generate various indices;
 * Observer Design Pattern: role Observer; 
 * the class @link org.exist.storage.NativeBroker is the subject (alias observable).
 * 
 * startElement() and endElement() bear the same names as the corresponding SAX events.  
 * However storeXXX() have no corresponding method in SAX.
 */
public interface ContentLoadingObserver extends AutoCloseable {

	/**
	 * Store and index given attribute
	 *
	 * @param attr         The attribute
	 * @param currentPath  The path of the attribute within the document
	 * @param spec         The index specification
	 * @param remove       whether the attribute should be removed
	 */
	void storeAttribute(AttrImpl attr, NodePath currentPath, RangeIndexSpec spec, boolean remove);

	/**
	 * Store and index given text node
	 *
	 * @param node the text node
	 * @param currentPath the node path
	 */
	void storeText(TextImpl node, NodePath currentPath);
			
	/**
	 * The given node is being removed from the database.
	 *
	 * @param node the text node
	 * @param currentPath the node path
	 * @param content the content
	 */
	void removeNode(NodeHandle node, NodePath currentPath, String content );

	/**
	 * set the current document; generally called before calling an operation
	 *
	 * @param document the document
	 */
	void setDocument(DocumentImpl document);

	/**
	 * Drop all index entries for the given collection.
	 * 
	 * @param collection the collection
	 */
	void dropIndex(Collection collection);

	/**
	 * Drop all index entries for the given document.
	 * 
	 * @param doc the document
	 */
	void dropIndex(DocumentImpl doc);

	/**
	 * remove all pending modifications, for the current document.
	 */
	void remove();
	
	/* The following methods are rather related to file management : create a dedicated interface ? */

	/**
     * Writes the pending items, for the current document's collection.
	 *
	 * @throws DBException if an error occurs whilst flushing
	 */
	void flush() throws DBException;

	/**
	 * triggers a cache sync, i.e. forces to write out all cached pages.
	 * sync() is called from time to time by the background sync daemon.
	 */
	void sync();

	@Override
	void close() throws DBException;
	
	void closeAndRemove();
	
	void printStatistics();
	
}
