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
import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.StoredNode;
import org.exist.dom.TextImpl;
import org.exist.util.ReadOnlyException;

/** Receives callback event during document(s) loading and removal;
 * implemented by several classes that generate various indices;
 * Observer Design Pattern: role Observer; 
 * the class @link org.exist.storage.NativeBroker is the subject (alias observable).
 * 
 * startElement() and endElement() bear the same names as the corresponding SAX events.  
 * However storeXXX() have no corresponding method in SAX.
 * 
 * Note: when we will have more than one runtime switch , we will refactor 
 * fullTextIndexSwitch into an object */
public interface ContentLoadingObserver {

	/** store and index given attribute */
	public abstract void storeAttribute( AttrImpl node, NodePath currentPath, boolean fullTextIndexSwitch );

	/** store and index given text node */ 
	public abstract void storeText( TextImpl node, NodePath currentPath, boolean fullTextIndexSwitch );
			
	/** corresponds to SAX function of the same name */
	public abstract void startElement(ElementImpl impl, NodePath currentPath, boolean index);

	/** store and index given element (called storeElement before) */
	public abstract void endElement(int xpathType, ElementImpl node,
			String content);

	/** Mark given Element for removal;
	 * added entries are written to the list of pending entries.
     * {@link #flush()} is called later to flush all pending entries.
	 * <br>
	 * Notes: changed name from storeElement() */
	public abstract void removeElement( ElementImpl node, NodePath currentPath, String content );

	
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
     * Reindexes all pending items for the specified document. 
     * 
     * @param oldDoc the document to be reindexed.
     * @param node if != null, only nodes being descendants of the specified node will be
     * reindexed. Other nodes are not touched. This is used for a partial reindex.
     */
	public abstract void reindex(DocumentImpl oldDoc, StoredNode node);

	/** remove all pending modifications, for the current document. */
	public abstract void remove();

}
