/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *  http://exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.dom.persistent;

import org.exist.collections.Collection;
import org.exist.storage.DBBroker;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

import java.util.Iterator;

public interface DocumentSet {

    public static final DocumentSet EMPTY_DOCUMENT_SET = new DefaultDocumentSet(9);

    public Iterator<DocumentImpl> getDocumentIterator();

    public Iterator<Collection> getCollectionIterator();

    public int getDocumentCount();

    public DocumentImpl getDoc(int docId);

    public XmldbURI[] getNames();

    public DocumentSet intersection(DocumentSet other);

    public boolean contains(DocumentSet other);

    public boolean contains(int id);

    public NodeSet docsToNodeSet();

    public void lock(DBBroker broker, boolean exclusive, boolean checkExisting) throws LockException;

    public void unlock(boolean exclusive);

    public boolean equalDocs(DocumentSet other);

}
