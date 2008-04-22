/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *
 * $Id$
 */
package org.exist.backup;

import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.Value;
import org.exist.storage.index.CollectionStore;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.UTF8;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.TerminatedException;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.DateTimeValue;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.NamespaceSupport;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * Embedded database export tool class. Tries to export as much data as
 * possible, even if parts of the collection hierarchy are corrupted or
 * documents are no longer readable. Features:
 *
 * <ul>
 *  <li>Descendant collections will be exported properly even if their ancestor
 *  collection is corrupted.</li>
 *  <li>Documents which are intact but belong to a destroyed collection will be
 *  stored into a special collection /db/__lost_and_found__.
 *  <li>Damaged documents are detected by ConsistencyCheck and are removed from
 *  the backup.</li>
 *  <li>The format of the exported data is compatible with backups generated
 *  via the standard backup tool (Java admin client).</li>
 * </ul>
 *
 * The class should be used in combination with {@link ConsistencyCheck}.
 * The error lists returned by ConsistencyCheck can be passed to {@link #export(String, java.util.List)}.
 */
public class SystemExport {

    private final static Logger LOG = Logger.getLogger(SystemExport.class);

    private final static DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmm");

    public Properties defaultOutputProperties = new Properties();
	{
		defaultOutputProperties.setProperty(OutputKeys.INDENT, "no");
		defaultOutputProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
		defaultOutputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
		defaultOutputProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
		defaultOutputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
	}

    public Properties contentsOutputProps = new Properties();
	{
	    contentsOutputProps.setProperty(OutputKeys.INDENT, "yes");
    }

    private static final XmldbURI TEMP_COLLECTION = XmldbURI.createInternal(NativeBroker.TEMP_COLLECTION);
    private static final XmldbURI CONTENTS_URI = XmldbURI.createInternal("__contents__.xml");
    private static final XmldbURI LOST_URI = XmldbURI.createInternal("__lost_and_found__");

    private static final int currVersion = 1;
    
    private DBBroker broker;
    private StatusCallback callback = null;

    public SystemExport(DBBroker broker, StatusCallback callback) {
        this.broker = broker;
        this.callback = callback;
    }

    /**
     * Export the contents of the database, trying to preserve
     * as much data as possible. To be effective, this method
     * should be used in combination with class
     * {@link ConsistencyCheck}.
     *
     * @param target the output directory or file to which data will be written.
     *  Output will be written to a zip file if target ends with .zip.
     * @param errorList a list of {@link ErrorReport} objects as returned by
     *   methods in {@link ConsistencyCheck}.
     */
    public void export(String target, List errorList) {
        try {
            BackupWriter output;
            if (target.endsWith(".zip"))
                output = new ZipWriter(target, "/db");
            else
                output = new FileSystemWriter(target + "/db");
            CollectionCallback cb = new CollectionCallback(output, errorList);
            broker.getCollectionsFailsafe(cb);

            exportOrphans(output, cb.getDocs(), errorList);

            output.close();
        } catch (IOException e) {
            reportError("A write error occurred while exporting data: '" + e.getMessage() +
                    "'. Aborting export.", e);
        }
    }

    private void reportError(String message, Throwable e) {
        if (callback != null) {
            callback.error("EXPORT: " + message, e);
        }
    }
    
    private static boolean isDamaged(DocumentImpl doc, List errorList) {
        if (errorList == null)
            return false;
        org.exist.backup.ErrorReport report;
        for (int i = 0; i < errorList.size(); i++) {
            report = (org.exist.backup.ErrorReport) errorList.get(i);
            if (report.getErrcode() == org.exist.backup.ErrorReport.RESOURCE_ACCESS_FAILED &&
                    ((ErrorReport.ResourceError)report).getDocumentId() == doc.getDocId())
                return true;
        }
        return false;
    }

    private static boolean isDamaged(Collection collection, List errorList) {
        if (errorList == null)
            return false;
        ErrorReport report;
        for (int i = 0; i < errorList.size(); i++) {
            report = (ErrorReport) errorList.get(i);
            if (report.getErrcode() == org.exist.backup.ErrorReport.CHILD_COLLECTION &&
                    ((ErrorReport.CollectionError)report).getCollectionId() == collection.getId())
                return true;
        }
        return false;
    }

    private static boolean isDamagedChild(XmldbURI uri, List errorList) {
        if (errorList == null)
            return false;
        org.exist.backup.ErrorReport report;
        for (int i = 0; i < errorList.size(); i++) {
            report = (org.exist.backup.ErrorReport) errorList.get(i);
            if (report.getErrcode() == org.exist.backup.ErrorReport.CHILD_COLLECTION &&
                    ((org.exist.backup.ErrorReport.CollectionError)report).getCollectionURI().equalsInternal(uri))
                return true;
        }
        return false;
    }

    /**
     * Scan all document records in collections.dbx and try to find orphaned documents
     * whose parent collection got destroyed or is damaged.
     *
     * @param output the backup writer
     * @param docs a document set containing all the documents which were exported regularily.
     *  the method will ignore those.
     * @param errorList a list of {@link org.exist.backup.ErrorReport} objects as returned by
     *   methods in {@link ConsistencyCheck}
     */
    private void exportOrphans(BackupWriter output, DocumentSet docs, List errorList) {
        output.newCollection("/db/__lost_and_found__");
        try {
            Writer contents = output.newContents();
            // serializer writes to __contents__.xml
            SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            serializer.setOutput(contents, contentsOutputProps);

            serializer.startDocument();
            serializer.startPrefixMapping("", Namespaces.EXIST_NS);
            AttributesImpl attr = new AttributesImpl();
            attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", "/db/__lost_and_found__");
            attr.addAttribute(Namespaces.EXIST_NS, "version", "version", "CDATA", String.valueOf(currVersion));
            attr.addAttribute(Namespaces.EXIST_NS, "owner", "owner", "CDATA", org.exist.security.SecurityManager.DBA_USER);
            attr.addAttribute(Namespaces.EXIST_NS, "group", "group", "CDATA", org.exist.security.SecurityManager.DBA_GROUP);
            attr.addAttribute(Namespaces.EXIST_NS, "mode", "mode", "CDATA", "0771");
            serializer.startElement(Namespaces.EXIST_NS, "collection", "collection", attr);

            DocumentCallback docCb = new DocumentCallback(output, serializer, docs);
            broker.getResourcesFailsafe(docCb);

            serializer.endElement(Namespaces.EXIST_NS, "collection", "collection");
            serializer.endPrefixMapping("");
            serializer.endDocument();
            output.closeContents();
        } catch (Exception e) {
            e.printStackTrace();
            if (callback != null)
                callback.error(e.getMessage(), e);
        } finally {
            output.closeCollection();
        }
    }

    /**
     * Export a collection. Write out the collection metadata and save the resources stored in
     * the collection.
     *
     * @param current the collection
     * @param output the output writer
     * @param errorList a list of {@link ErrorReport} objects as returned by
     *   methods in {@link ConsistencyCheck}
     * @param docs a document set to keep track of all written documents.
     * @throws IOException
     * @throws SAXException
     */
    private void export(Collection current, BackupWriter output, List errorList, DocumentSet docs) throws IOException, SAXException {
        if (callback != null)
            callback.startCollection(current.getURI().toString());
        
        if (!current.getURI().equalsInternal(XmldbURI.ROOT_COLLECTION_URI)) {
            output.newCollection(Backup.encode(URIUtils.urlDecodeUtf8(current.getURI())));
        }
        try {
            Writer contents = output.newContents();
            // serializer writes to __contents__.xml
            SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            serializer.setOutput(contents, contentsOutputProps);

            serializer.startDocument();
            serializer.startPrefixMapping("", Namespaces.EXIST_NS);
            XmldbURI uri = current.getURI();
            AttributesImpl attr = new AttributesImpl();
            attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", uri.toString());
            attr.addAttribute(Namespaces.EXIST_NS, "version", "version", "CDATA", String.valueOf(currVersion));
            attr.addAttribute(Namespaces.EXIST_NS, "owner", "owner", "CDATA", current.getPermissions().getOwner());
            attr.addAttribute(Namespaces.EXIST_NS, "group", "group", "CDATA", current.getPermissions().getOwnerGroup());
            attr.addAttribute(Namespaces.EXIST_NS, "mode", "mode", "CDATA",
                    Integer.toOctalString(current.getPermissions().getPermissions()));
            try {
                attr.addAttribute(Namespaces.EXIST_NS, "created", "created", "CDATA",
                        new DateTimeValue(new Date(current.getCreationTime())).getStringValue());
            } catch (XPathException e) {
                e.printStackTrace();
            }
            serializer.startElement(Namespaces.EXIST_NS, "collection", "collection", attr);

            int docsCount = current.getDocumentCount();
            int count = 0;
            for (Iterator i = current.iterator(broker); i.hasNext(); count++) {
                DocumentImpl doc = (DocumentImpl) i.next();
                if (isDamaged(doc, errorList)) {
                    reportError("Skipping damaged document " + doc.getFileURI(), null);
                    continue;
                }
                if (doc.getFileURI().equalsInternal(CONTENTS_URI) || doc.getFileURI().equalsInternal(LOST_URI))
                    continue; // skip __contents__.xml documents
                exportDocument(output, serializer, docsCount, count, doc);
                docs.add(doc, false);
            }

            for (Iterator i = current.collectionIterator(); i.hasNext(); ) {
                XmldbURI childUri = (XmldbURI) i.next();
                if (childUri.equalsInternal(TEMP_COLLECTION))
                    continue;
                if (isDamagedChild(childUri, errorList)) {
                    reportError("Skipping damaged child collection " + childUri, null);
                    continue;
                }
                attr.clear();
                attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", childUri.toString());
                attr.addAttribute(Namespaces.EXIST_NS, "filename", "filename", "CDATA",
                        Backup.encode(URIUtils.urlDecodeUtf8(childUri.toString())));
                serializer.startElement(Namespaces.EXIST_NS, "subcollection", "subcollection", attr);
                serializer.endElement(Namespaces.EXIST_NS, "subcollection", "subcollection");
            }
            // close <collection>
            serializer.endElement(Namespaces.EXIST_NS, "collection", "collection");
            serializer.endPrefixMapping("");
            serializer.endDocument();
            output.closeContents();
        } finally {
            if (!current.getURI().equalsInternal(XmldbURI.ROOT_COLLECTION_URI)) {
                output.closeCollection();
            }
        }
    }

    private void exportDocument(BackupWriter output, SAXSerializer serializer, int docsCount, int count, DocumentImpl doc) throws IOException, SAXException {
        if (callback != null)
            callback.startDocument(doc.getFileURI().toString(), count, docsCount);
        OutputStream os = output.newEntry(Backup.encode(URIUtils.urlDecodeUtf8(doc.getFileURI())));
        try {
            if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                broker.readBinaryResource((BinaryDocument) doc, os);
            } else {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                // write resource to contentSerializer
                SAXSerializer contentSerializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
                contentSerializer.setOutput(writer, defaultOutputProperties);
                writeXML(doc, contentSerializer);
                SerializerPool.getInstance().returnObject(contentSerializer);
                writer.flush();
            }
        } catch (Exception e) {
            reportError("A write error occurred while exporting document: '" + doc.getFileURI() +
                        "'. Continuing with next document.", e);
            return;
        } finally {
            output.closeEntry();
        }

        //store permissions
        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(Namespaces.EXIST_NS, "type", "type", "CDATA",
                doc.getResourceType() == DocumentImpl.BINARY_FILE ? "BinaryResource" : "XMLResource");
        attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", doc.getFileURI().toString());
        attr.addAttribute(Namespaces.EXIST_NS, "owner", "owner", "CDATA", doc.getPermissions().getOwner());
        attr.addAttribute(Namespaces.EXIST_NS, "group", "group", "CDATA", doc.getPermissions().getOwnerGroup());
        attr.addAttribute(Namespaces.EXIST_NS, "mode", "mode", "CDATA",
                Integer.toOctalString(doc.getPermissions().getPermissions()));

        // be careful when accessing document metadata: it is stored in a different place than the
        // main document info and could thus be damaged
        DocumentMetadata metadata = null;
        try {
            metadata = doc.getMetadata();
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
        }

        try {
            String created;
            String modified;
            // metadata could be damaged
            if (metadata != null) {
                created = new DateTimeValue(new Date(metadata.getCreated())).getStringValue();
                modified = new DateTimeValue(new Date(metadata.getLastModified())).getStringValue();
            } else {
                created = new DateTimeValue().getStringValue();
                modified = created;
            }
            attr.addAttribute(Namespaces.EXIST_NS, "created", "created", "CDATA", created);
            attr.addAttribute(Namespaces.EXIST_NS, "modified", "modified", "CDATA", modified);
        } catch (XPathException e) {
            LOG.warn(e.getMessage(), e);
        }

        attr.addAttribute(Namespaces.EXIST_NS, "filename", "filename", "CDATA",
                Backup.encode(URIUtils.urlDecodeUtf8(doc.getFileURI())));
        String mimeType = "text/xml";
        if (metadata != null && metadata.getMimeType() != null)
            mimeType = Backup.encode(metadata.getMimeType());
        attr.addAttribute(Namespaces.EXIST_NS, "mimetype", "mimetype", "CDATA", mimeType);
        if (doc.getResourceType() == DocumentImpl.XML_FILE && metadata != null && doc.getDoctype() != null) {
            if (doc.getDoctype().getName() != null)
                attr.addAttribute(Namespaces.EXIST_NS, "namedoctype", "namedoctype", "CDATA", doc.getDoctype().getName());
            if (doc.getDoctype().getPublicId() != null)
                attr.addAttribute(Namespaces.EXIST_NS, "publicid", "publicid", "CDATA", doc.getDoctype().getPublicId());
            if (doc.getDoctype().getSystemId() != null)
                attr.addAttribute(Namespaces.EXIST_NS, "systemid", "systemid", "CDATA", doc.getDoctype().getSystemId());
        }
        serializer.startElement(Namespaces.EXIST_NS, "resource", "resource", attr);
        serializer.endElement(Namespaces.EXIST_NS, "resource", "resource");
    }

    /**
     * Serialize a document to XML, based on {@link XMLStreamReader}.
     *
     * @param doc the document to serialize
     * @param receiver the output handler
     */
    private void writeXML(DocumentImpl doc, Receiver receiver) {
        try {
            EmbeddedXMLStreamReader reader;
            char ch[];
            int nsdecls;
            NamespaceSupport nsSupport = new NamespaceSupport();
            NodeList children = doc.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                reader = doc.getBroker().getXMLStreamReader((StoredNode) children.item(i), false);
                while (reader.hasNext()) {
                    int status = reader.next();
                    switch (status) {
                        case XMLStreamReader.START_DOCUMENT:
                        case XMLStreamReader.END_DOCUMENT:
                            break;
                        case XMLStreamReader.START_ELEMENT :
                            nsdecls = reader.getNamespaceCount();
                            for (int ni = 0; ni < nsdecls; ni++) {
                                receiver.startPrefixMapping(reader.getNamespacePrefix(ni), reader.getNamespaceURI(ni));
                            }

                            AttrList attribs = new AttrList();
                            for (int j = 0; j < reader.getAttributeCount(); j++) {
                                final QName qn = new QName(reader.getAttributeLocalName(j), reader.getAttributeNamespace(j),
                                        reader.getAttributePrefix(j));
                                attribs.addAttribute(qn, reader.getAttributeValue(j));
                            }
                            receiver.startElement(new QName(reader.getLocalName(), reader.getNamespaceURI(), reader.getPrefix()),
                                    attribs);
                            break;
                        case XMLStreamReader.END_ELEMENT :
                            receiver.endElement(new QName(reader.getLocalName(), reader.getNamespaceURI(), reader.getPrefix()));
                            nsdecls = reader.getNamespaceCount();
                            for (int ni = 0; ni < nsdecls; ni++) {
                                receiver.endPrefixMapping(reader.getNamespacePrefix(ni));
                            }
                            break;
                        case XMLStreamReader.CHARACTERS :
                            receiver.characters(reader.getText());
                            break;
                        case XMLStreamReader.CDATA :
                            ch = reader.getTextCharacters();
                            receiver.cdataSection(ch, 0, ch.length);
                            break;
                        case XMLStreamReader.COMMENT :
                            ch = reader.getTextCharacters();
                            receiver.comment(ch, 0, ch.length);
                            break;
                        case XMLStreamReader.PROCESSING_INSTRUCTION :
                            receiver.processingInstruction(reader.getPITarget(), reader.getPIData());
                            break;
                    }
                }
                nsSupport.reset();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    public static File getUniqueFile(String base, String extension, String dir) {
        String filename = base + '-' + dateFormat.format(new Date());
        File file = new File(dir, filename + extension);
        int version = 0;
        while (file.exists()) {
            file = new File(dir, filename + '_' + version++ + extension);
        }
        return file;
    }

    public static interface StatusCallback {

        public void startCollection(String path);

        public void startDocument(String name, int current, int count);
        
        public void error(String message, Throwable exception);
    }

    private class CollectionCallback implements BTreeCallback {

        private BackupWriter writer;
        private List errors;
        private DocumentSet docs = new DocumentSet();
        
        private CollectionCallback(BackupWriter writer, List errorList) {
            this.writer = writer;
            this.errors = errorList;
        }

        public boolean indexInfo(Value value, long pointer) throws TerminatedException {
            String uri = null;
            try {
                CollectionStore store = (CollectionStore) ((NativeBroker)broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
                uri = UTF8.decode(value.data(), value.start() + CollectionStore.CollectionKey.OFFSET_VALUE,
                        value.getLength() - CollectionStore.CollectionKey.OFFSET_VALUE).toString();
                if (CollectionStore.NEXT_COLLECTION_ID_KEY.equals(uri) || CollectionStore.NEXT_DOC_ID_KEY.equals(uri))
                    return true;
                if (callback != null)
                    callback.startCollection(uri);
                Collection collection = new Collection(XmldbURI.createInternal(uri));
                VariableByteInput istream = store.getAsStream(pointer);
                collection.read(broker, istream);
                export(collection, writer, errors, docs);
            } catch (Exception e) {
                reportError("Caught exception while scanning collections: " + uri, e);
            }
            return true;
        }

        public DocumentSet getDocs() {
            return docs;
        }
    }

    private class DocumentCallback implements BTreeCallback {

        private DocumentSet exportedDocs;
        private SAXSerializer serializer;
        private BackupWriter output;

        private DocumentCallback(BackupWriter output, SAXSerializer serializer, DocumentSet exportedDocs) {
            this.exportedDocs = exportedDocs;
            this.serializer = serializer;
            this.output = output;
        }

        public boolean indexInfo(Value key, long pointer) throws TerminatedException {
            CollectionStore store = (CollectionStore) ((NativeBroker)broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            int collectionId = CollectionStore.DocumentKey.getCollectionId(key);
            int docId = CollectionStore.DocumentKey.getDocumentId(key);

            if (!exportedDocs.contains(docId)) {
                try {
                    byte type = key.data()[key.start() + Collection.LENGTH_COLLECTION_ID + DocumentImpl.LENGTH_DOCUMENT_TYPE];
                    VariableByteInput istream = store.getAsStream(pointer);
                    DocumentImpl doc = null;
                    if (type == DocumentImpl.BINARY_FILE)
                        doc = new BinaryDocument(broker);
                    else
                        doc = new DocumentImpl(broker);
                    doc.read(istream);
                    reportError("Found an orphaned document: " + doc.getFileURI().toString(), null);
                    exportDocument(output, serializer, 0, 0, doc);
                } catch (Exception e) {
                    reportError("Caught an exception while scanning documents: " + e.getMessage(), e);
                }
            }
            return true;
        }
    }
}