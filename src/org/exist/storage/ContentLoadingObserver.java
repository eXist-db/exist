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
import org.exist.dom.StoredNode;
import org.exist.dom.TextImpl;
import org.exist.storage.btree.DBException;
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
	//TODO : remove the RangeIndexSpec dependency ASAP
	public void storeAttribute(AttrImpl node, NodePath currentPath, int indexingHint, RangeIndexSpec spec, boolean remove);

	/** store and index given text node */ 
	public void storeText(TextImpl node, NodePath currentPath, int indexingHint);
			
	/**
	 * The given node is being removed from the database. 
	 */
	public void removeNode(StoredNode node, NodePath currentPath, String content );

	/** set the current document; generally called before calling an operation */
	public void setDocument(DocumentImpl document);

	/**
	 * Drop all index entries for the given collection.
	 * 
	 * @param collection
	 */
	public void dropIndex(Collection collection);

	/**
	 * Drop all index entries for the given document.
	 * 
	 * @param doc
	 * @throws ReadOnlyException
	 */
	public void dropIndex(DocumentImpl doc) throws ReadOnlyException;

	/** remove all pending modifications, for the current document. */
	public void remove();
	
	/* The following methods are rather related to file management : create a dedicated interface ? /*

	/** writes the pending items, for the current document's collection */
	public void flush() throws DBException;

	/** triggers a cache sync, i.e. forces to write out all cached pages.	
	 sync() is called from time to time by the background sync daemon. */
	public void sync();

	public boolean close() throws DBException;
	
	public void closeAndRemove();
	
	public void printStatistics();
	
}
