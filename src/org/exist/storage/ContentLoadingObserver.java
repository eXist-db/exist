/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeImpl;
import org.exist.util.ReadOnlyException;

/** Receives callback event during document(s) loading;
 * implemented by several classes that generate various indices;
 * Observer Design Pattern: role Observer; 
 * the class @link org.exist.storage.NativeBroker is the subject (alias observable).
 * 
 * Note: when we will have more than one runtime switch , we will refactor 
 * fullTextIndexSwitch into an object */
public interface ContentLoadingObserver {

	/** store and index given element */
	public abstract void storeElement(int xpathType, ElementImpl node,
			String content);

	/** store and index given attribute */
	// TODO public abstract void storeAttribute( AttrImpl node, NodePath currentPath, boolean fullTextIndexSwitch );

	/** store and index given text node */
	// TODO public abstract void storeText( TextImpl node, NodePath currentPath, boolean fullTextIndexSwitch );
	
	/** Mark given Element;
	 * added entries are written to the list of pending entries. Call
     * {@link #flush()} to flush all pending entries.
	 * <br>
	 * Notes: changed name from storeElement() */
	// TODO public abstract void markElement( ElementImpl node, NodePath currentPath, String content );
	
    /** Mark given Node;
	 * add an index entry for the given node.
     * Added entries are written to the list of pending entries. Call
     * {@link #flush()} to flush all pending entries.
     * Note: changed name from addRow()
     */
    // public void addRow(QName qname, NodeProxy proxy);
	// TODO public void markNode(NodeImpl node, NodePath currentPath, boolean fullTextIndexSwitch);
	
	/** set the current document; generally called before calling an operation */
	public abstract void setDocument(DocumentImpl document);

	/** writes the pending items, for the current document's collection */
	public abstract void flush();

	/** triggers a cache sync, i.e. forces to write out all cached pages.	
	 sync() is called from time to time by the background sync daemon. */
	public abstract void sync();

	/**
	 * Drop all index entries for the given collection.
	 * 
	 * @param collection
	 */
	public abstract void dropIndex(Collection collection);

	/**
	 * Drop all index entries for the given document.
	 * 
	 * @param doc
	 * @throws ReadOnlyException
	 */
	public abstract void dropIndex(DocumentImpl doc) throws ReadOnlyException;

	/**
     * Reindexes all pending items for the specified document. Similar to the normal index process,
     * items to be reindexed are added to the internal pending list via methods 
     * {@link #addRow(QName, NodeProxy)}, {@link #storeElement(int, ElementImpl, String),
     * and {@link #storeAttribute(RangeIndexSpec, AttrImpl)}. Method reindex then scans this
     * list and updates the items in the index to reflect the reindexed document.
     * 
     * @param oldDoc the document to be reindexed.
     * @param node if != null, only nodes being descendants of the specified node will be
     * reindexed. Other nodes are not touched. This is used for a partial reindex.
     */
	public abstract void reindex(DocumentImpl oldDoc, NodeImpl node);

	/** remove all pending modifications, for the current document. */
	public abstract void remove();

}
