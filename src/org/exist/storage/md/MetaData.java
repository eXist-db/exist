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
package org.exist.storage.md;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.MetaStorage;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class MetaData implements MetaStorage {

	protected static MetaData inst = null;
	
	public static MetaData get() {
		return inst;
	}
	
	protected final static Logger LOG = LogManager.getLogger(MetaData.class);

	public abstract DocumentImpl getDocument(String uuid) throws EXistException, PermissionDeniedException;
	public abstract Collection getCollection(String uuid) throws EXistException, PermissionDeniedException;

	public abstract List<DocumentImpl> matchDocuments(String key, String value) throws EXistException, PermissionDeniedException;
    public abstract List<DocumentImpl> matchDocumentsByKey(String key) throws EXistException, PermissionDeniedException;
    public abstract List<DocumentImpl> matchDocumentsByValue(String value) throws EXistException, PermissionDeniedException;

	public abstract Metas addMetas(Document doc);
	public abstract Metas addMetas(Collection col);

	public abstract Meta getMeta(String uuid);

	//low level
	protected abstract Meta _addMeta(Metas metas, String uuid, String key, String value);
	protected abstract Metas _addMetas(String uri, String uuid);
	protected abstract Metas replaceMetas(XmldbURI uri, String uuid);

	public abstract Metas getMetas(Document doc);
	public abstract Metas getMetas(XmldbURI uri);

	public abstract void copyMetas(XmldbURI oldDoc, DocumentImpl newDoc);
	public abstract void copyMetas(XmldbURI oldDoc, Collection newCol);

	public abstract void moveMetas(XmldbURI oldUri, XmldbURI newUri);

	public abstract void delMetas(XmldbURI uri);

	public abstract void sync();
	public abstract void close();

	public abstract XmldbURI UUIDtoURI(String uuid);
	public abstract String URItoUUID(XmldbURI uri);
	
//    public abstract void indexMetas(Metas metas);
	
//    public abstract NodeImpl search(String queryText, List<String> toBeMatchedURIs) throws XPathException;
//    public abstract List<String> searchDocuments(String queryText, List<String> toBeMatchedURIs) throws XPathException;
}
