/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.Resource;
import org.exist.backup.BackupHandler;
import org.exist.backup.RestoreHandler;
import org.exist.collections.Collection;
import org.exist.config.Configuration;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.plugin.Plug;
import org.exist.plugin.PluginsManager;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.MetaStorage;
import org.exist.storage.md.xquery.MetadataModule;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xquery.XQueryContext;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import static org.exist.storage.md.MetaData.*;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class MDStorageManager implements Plug, BackupHandler, RestoreHandler {

    protected final static Logger LOG = LogManager.getLogger(MDStorageManager.class);

//    public final static String LUCENE_ID = "org.exist.indexing.lucene.LuceneIndex";

    public final static String UUID = "uuid";
    public final static String META = "meta";
    public final static String KEY = "key";
    public final static String VALUE = "value";
    public final static String VALUE_IS_DOCUMENT = "value-is-document";

    public final static String PREFIX_UUID = PREFIX+":"+UUID;
    public final static String PREFIX_KEY = PREFIX+":"+KEY;
    public final static String PREFIX_META = PREFIX+":"+META;
    public final static String PREFIX_VALUE = PREFIX+":"+VALUE;
    public final static String PREFIX_VALUE_IS_DOCUMENT = PREFIX+":"+VALUE_IS_DOCUMENT;

    protected static MDStorageManager instance = null;

//    protected static MDStorageManager get() {
//        return inst;
//    }

    protected static MetaData storage() {
        return instance.md;
    }

    MetaData md;

    public MDStorageManager(PluginsManager manager) throws PermissionDeniedException {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends MetaData> backend =
                (Class<? extends MetaData>) Class.forName("org.exist.storage.md.MetaDataImpl");

            Constructor<? extends MetaData> ctor = backend.getConstructor(Database.class);
            md = ctor.newInstance(manager.getDatabase());
        } catch (Exception e) {
                e.printStackTrace();
            throw new PermissionDeniedException(e);
        }

        instance = this;

        Database db = manager.getDatabase();

        inject(db, md);

        db.registerDocumentTrigger(DocumentEvents.class);
        db.registerCollectionTrigger(CollectionEvents.class);

        Map<String, Class<?>> map = (Map<String, Class<?>>) db.getConfiguration().getProperty(XQueryContext.PROPERTY_BUILT_IN_MODULES);
        map.put(
            NAMESPACE_URI,
            MetadataModule.class);
    }

    private void inject(Database db, MetaStorage md) {
        try {
            Field field = db.getClass().getDeclaredField("metaStorage");
            field.setAccessible(true);
            field.set(db, md);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    @Override
    public void start(DBBroker broker) throws EXistException {
    }

    @Override
    public void sync(DBBroker broker) throws EXistException {
        md.sync();
    }

    @Override
    public void stop(DBBroker broker) throws EXistException {
        md.close();
    }

    //backup methods
    private void backup(Metas ms, AttributesImpl attrs) {
        attrs.addAttribute( NAMESPACE_URI, UUID, PREFIX_UUID, "CDATA", ms.getUUID() );
    }

    private void backup(Metas ms, SAXSerializer serializer) throws SAXException {
        List<Meta> sub = ms.metas();
        for (Meta m : sub) {

            AttributesImpl attr = new AttributesImpl();
            attr.addAttribute(NAMESPACE_URI, UUID, PREFIX_UUID, "CDATA", m.getUUID());
            attr.addAttribute(NAMESPACE_URI, KEY, PREFIX_KEY, "CDATA", m.getKey());

            Object value = m.getValue();
            if (value instanceof DocumentImpl) {
                DocumentImpl doc = (DocumentImpl) value;

                attr.addAttribute(NAMESPACE_URI, VALUE, PREFIX_VALUE, "CDATA", doc.getURI().toString());
                attr.addAttribute(NAMESPACE_URI, VALUE_IS_DOCUMENT, PREFIX_VALUE_IS_DOCUMENT, "CDATA", "true");

            } else {

                attr.addAttribute(NAMESPACE_URI, VALUE, PREFIX_VALUE, "CDATA", value.toString());
            }

            serializer.startElement(NAMESPACE_URI, META, PREFIX_META, attr );
            serializer.endElement(NAMESPACE_URI, META, PREFIX_META);
        }
    }

    private void backup(Metas ms, XMLStreamWriter writer) throws IOException {
        try {
            List<Meta> sub = ms.metas();
            for (Meta m : sub) {

                writer.writeStartElement(PREFIX, META, MetaData.NAMESPACE_URI);

                writer.writeAttribute(PREFIX, NAMESPACE_URI, UUID, m.getUUID());
                writer.writeAttribute(PREFIX, NAMESPACE_URI, KEY, m.getKey());

                Object value = m.getValue();
                if (value instanceof DocumentImpl) {
                    DocumentImpl doc = (DocumentImpl) value;

                    writer.writeAttribute(PREFIX, NAMESPACE_URI, VALUE, doc.getURI().toString());
                    writer.writeAttribute(PREFIX, NAMESPACE_URI, VALUE_IS_DOCUMENT, "true");

                } else {

                    writer.writeAttribute(PREFIX, NAMESPACE_URI, VALUE, value.toString());
                }

                writer.writeEndElement();
            }
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void backup(Collection collection, AttributesImpl attrs) {
        if (collection == null) {
            return;
        }

        Metas ms = md.getMetas(collection.getURI());
        if (ms != null) {
            backup(ms, attrs);
        } else {
            LOG.error("Collection '" + collection.getURI() + "' have no metas");
        }
    }

    @Override
    public void backup(Collection collection, SAXSerializer serializer) throws SAXException {
        if (collection == null) {
            return;
        }

        Metas ms = md.getMetas(collection.getURI());
        if (ms != null) {
            backup(ms, serializer);
        }
//        else
//            LOG.error("Collection '"+collection.getURI()+"' have no metas");
    }

    @Override
    public void backup(DocumentImpl document, AttributesImpl attrs) {
        if (document == null) {
            return;
        }

        Metas ms = md.getMetas(document);
        if (ms != null) {
            backup(ms, attrs);
        } else {
            LOG.error("Document '" + document.getURI() + "' have no metas");
        }
    }

    @Override
    public void backup(DocumentImpl document, SAXSerializer serializer) throws SAXException {
        if (document == null) {
            return;
        }

        Metas ms = md.getMetas(document);
        if (ms != null) {
            backup(ms, serializer);
        }
//        else {
//            LOG.error("Document '" + document.getURI() + "' have no metas");
//        }
    }

    @Override
    public void backup(Resource resource, XMLStreamWriter writer) throws IOException {
        if (resource == null) {
            return;
        }

        Metas ms = md.getMetas(resource.getURI());
        if (ms != null) {
            backup(ms, writer);
        }
//        else
//            LOG.error("Document '"+document.getURI()+"' have no metas");
    }

    //restore methods
    private Metas collectionMetas = null;
    private Metas currentMetas = null;

    @Override
    public void setDocumentLocator(Locator locator) {}

    @Override
    public void startDocument() throws SAXException {}

    @Override
    public void endDocument() throws SAXException {}

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {}

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {}

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if ((qName != null && qName.equals(PREFIX_META)) || (META.equals(localName) && NAMESPACE_URI.equals(uri))) {
            String uuid = atts.getValue(NAMESPACE_URI, UUID);
            if (uuid == null) uuid = atts.getValue(PREFIX_UUID);
            if (uuid == null) return;

            String key = atts.getValue(NAMESPACE_URI, KEY);
            if (key == null) key = atts.getValue(PREFIX_KEY);
            if (key == null) return;

            String value = atts.getValue(NAMESPACE_URI, VALUE);
            if (value == null) value = atts.getValue(PREFIX_VALUE);
            if (value == null) return;

            if (currentMetas != null) {
                md._addMeta(currentMetas, uuid, key, value);
            } else if (collectionMetas != null) {
                md._addMeta(collectionMetas, uuid, key, value);
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (localName.equals("collection")) {
            collectionMetas = null;
            currentMetas = null;

        } else if (localName.equals("resource")) {
            currentMetas = null;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {}

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {}

    @Override
    public void processingInstruction(String target, String data) throws SAXException {}

    @Override
    public void skippedEntity(String name) throws SAXException {}

    @Override
    public void startRestore(Resource resource, Attributes atts) {

        String uuid = atts.getValue(NAMESPACE_URI, UUID);

        startRestore(resource, uuid);
    }

    @Override
    public void startRestore(Resource resource, String uuid) {

        if (resource == null) return;

        if (uuid != null) {
            if (resource.isFolder()) {
                collectionMetas = md.replaceMetas(resource.getURI(), uuid);
            } else {
                currentMetas = md.replaceMetas(resource.getURI(), uuid);
            }
        } else {
            if (resource.isFolder()) {
                collectionMetas = md.addMetas(resource.getURI());
            } else {
                currentMetas = md.addMetas(resource.getURI());
            }
        }
    }

    @Override
    public void endRestore(Resource resource) {
        String type;
        if (resource.isFolder()) type = "collection";
        else type = "resource";

        endElement(null, type, null);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public Configuration getConfiguration() {
        return null;
    }
}