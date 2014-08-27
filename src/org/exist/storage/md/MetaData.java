/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.Resource;
import org.exist.collections.Collection;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.MetaStorage;
import org.exist.util.function.Consumer;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public abstract class MetaData implements MetaStorage {

    protected static MetaData instance = null;

    public static MetaData get() {
        return instance;
    }

    public final static String PREFIX = "md";
    public final static String NAMESPACE_URI = "http://exist-db.org/metadata";

    protected final static Logger LOG = Logger.getLogger(MetaData.class);

    public abstract DocumentImpl getDocument(String uuid) throws EXistException, PermissionDeniedException;

    public abstract Collection getCollection(String uuid) throws EXistException, PermissionDeniedException;

    public abstract void resources(String key, String value, Consumer<Resource> consumer) throws EXistException;

    public abstract void resourcesByKey(String key, Consumer<Resource> consumer) throws EXistException;

    public abstract void resourcesByValue(String value, Consumer<Resource> consumer) throws EXistException;

    @Deprecated //use public void resources(String key, String value, Consumer<Resource> consumer)
    public abstract List<DocumentImpl> matchDocuments(String key, String value) throws EXistException, PermissionDeniedException;

    @Deprecated //use public void resourcesByKey(String key, Consumer<Resource> consumer)
    public abstract List<DocumentImpl> matchDocumentsByKey(String key) throws EXistException, PermissionDeniedException;

    @Deprecated //use public void resourcesByValue(String value, Consumer<Resource> consumer)
    public abstract List<DocumentImpl> matchDocumentsByValue(String value) throws EXistException, PermissionDeniedException;

    public abstract Metas addMetas(DocumentAtExist doc);

    public abstract Metas addMetas(Collection col);

    public abstract Meta getMeta(String uuid);

    // low level
    public abstract Metas addMetas(XmldbURI url);

    protected abstract Meta _addMeta(Metas metas, String uuid, String key, String value);

    protected abstract Metas _addMetas(String uri, String uuid);

    protected abstract Metas replaceMetas(XmldbURI uri, String uuid);

    public abstract Metas getMetas(DocumentAtExist doc);

    public abstract Metas getMetas(XmldbURI uri);

    public abstract void copyMetas(XmldbURI oldDoc, DocumentImpl newDoc);

    public abstract void copyMetas(XmldbURI oldDoc, Collection newCol);

    public abstract void moveMetas(XmldbURI oldUri, XmldbURI newUri);

    public abstract void delMetas(XmldbURI uri);

    public abstract void sync();

    public abstract void close();

    public abstract XmldbURI UUIDtoURI(String uuid);

    public abstract String URItoUUID(XmldbURI uri);

    // public abstract void indexMetas(Metas metas);

    // public abstract NodeImpl search(String queryText, List<String>
    // toBeMatchedURIs) throws XPathException;
    // public abstract List<String> searchDocuments(String queryText,
    // List<String> toBeMatchedURIs) throws XPathException;
}
