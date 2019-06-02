/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2012 The eXist Project
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
package org.exist.util.io;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.*;

import javax.xml.transform.OutputKeys;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.Collection.CollectionEntry;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.*;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static org.exist.security.Permission.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * eXist's resource. It extend java.io.File
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class Resource extends File {

    private final static Logger LOG = LogManager.getLogger(Resource.class);

    private static final long serialVersionUID = -3450182389919974961L;

    public static final char separatorChar = '/';

    //  default output properties for the XML serialization
    public final static Properties XML_OUTPUT_PROPERTIES = new Properties();

    static {
        XML_OUTPUT_PROPERTIES.setProperty(OutputKeys.INDENT, "yes");
        XML_OUTPUT_PROPERTIES.setProperty(OutputKeys.ENCODING, "UTF-8");
        XML_OUTPUT_PROPERTIES.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        XML_OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
        XML_OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
    }


    private static final SecureRandom random = new SecureRandom();

    static File generateFile(String prefix, String suffix, File dir) {
        long n = random.nextLong();
        if (n == Long.MIN_VALUE) {
            n = 0;      // corner case
        } else {
            n = Math.abs(n);
        }
        return new Resource(dir, prefix + Long.toString(n) + suffix);
    }

    protected XmldbURI uri;

    protected boolean initialized = false;

    private Collection collection = null;
    private DocumentImpl resource = null;

    Path file = null;

    public Resource(XmldbURI uri) {
        super(uri.toString());

        this.uri = uri;
    }

    public Resource(String uri) {
        this(XmldbURI.create(uri));
    }

    public Resource(File file, String child) {
        this((Resource) file, child);
    }

    public Resource(Resource resource, String child) {
        this(resource.uri.append(child));
//		this(child.startsWith("/db") ? XmldbURI.create(child) : resource.uri.append(child));
    }

    public Resource(String parent, String child) {
        this(XmldbURI.create(parent).append(child));
//		this(child.startsWith("/db") ? XmldbURI.create(child) : XmldbURI.create(parent).append(child));
    }

    public Resource getParentFile() {
        final XmldbURI parentPath = uri.removeLastSegment();
        if (parentPath == XmldbURI.EMPTY_URI) {
            if (uri.startsWith(XmldbURI.DB))
                return null;

            return new Resource(XmldbURI.DB);
        }

        return new Resource(parentPath);
    }

    public Resource getAbsoluteFile() {
        return this; //UNDERSTAND: is it correct?
    }

    public File getCanonicalFile() throws IOException {
        return this;
    }

    public String getName() {
        return uri.lastSegment().toString();
    }

    private void closeFile(InputStream is) {
        if (is == null) {
            return;
        }
        try {
            is.close();
        } catch (final IOException e) {
            //
        }
    }

    public boolean mkdir() {

        final BrokerPool db;

        try {
            db = BrokerPool.getInstance();
        } catch (final EXistException e) {
            return false;
        }

        try (final DBBroker broker = db.getBroker()) {

            final Collection collection = broker.getCollection(uri.toCollectionPathURI());
            if (collection != null) {
                return true;
            }

            final Collection parent_collection = broker.getCollection(uri.toCollectionPathURI().removeLastSegment());
            if (parent_collection == null) {
                return false;
            }

            final TransactionManager tm = db.getTransactionManager();


            try (final Txn transaction = tm.beginTransaction()) {
                final Collection child = broker.getOrCreateCollection(transaction, uri.toCollectionPathURI());
                broker.saveCollection(transaction, child);
                tm.commit(transaction);
            } catch (final Exception e) {
                LOG.error(e);
                return false;
            }
        } catch (final Exception e) {
            LOG.error(e);
            return false;
        }

        return true;
    }

    public boolean mkdirs() {
        final BrokerPool db;

        try {
            db = BrokerPool.getInstance();

        } catch (final EXistException e) {
            return false;
        }

        try (final DBBroker broker = db.getBroker()) {

            final Collection collection = broker.getCollection(uri.toCollectionPathURI());
            if (collection != null) {
                return true;
            }

            final TransactionManager tm = db.getTransactionManager();

            try (final Txn transaction = tm.beginTransaction()) {
                final Collection child = broker.getOrCreateCollection(transaction, uri.toCollectionPathURI());
                broker.saveCollection(transaction, child);
                tm.commit(transaction);
            } catch (final Exception e) {
                LOG.error(e);
                return false;
            }

        } catch (final Exception e) {
            LOG.error(e);
            return false;
        }

        return true;
    }

    public boolean isDirectory() {
        try {
            init();
        } catch (final IOException e) {
            return false;
        }

        return (resource == null);
    }

    public boolean isFile() {
        try {
            init();
        } catch (final IOException e) {
            return false;
        }

        return (resource != null);
    }

    public boolean exists() {
        try {
            init();
        } catch (final IOException e) {
            return false;
        }

        return ((collection != null) || (resource != null));

    }

    public boolean _renameTo(File dest) {
        final XmldbURI destinationPath = ((Resource) dest).uri;

        BrokerPool db = null;
        TransactionManager tm;

        try {
            db = BrokerPool.getInstance();
            tm = db.getTransactionManager();
        } catch (final EXistException e) {
            return false;
        }

        try (final DBBroker broker = db.getBroker();
             final Collection source = broker.openCollection(uri.removeLastSegment(), LockMode.WRITE_LOCK)) {
            if (source == null) {
                return false;
            }
            final DocumentImpl doc = source.getDocument(broker, uri.lastSegment());
            if (doc == null) {
                return false;
            }
            try (final Collection destination = broker.openCollection(destinationPath.removeLastSegment(), LockMode.WRITE_LOCK)) {
                if (destination == null) {
                    return false;
                }

                final XmldbURI newName = destinationPath.lastSegment();

                try (final Txn transaction = tm.beginTransaction()) {
                    broker.moveResource(transaction, doc, destination, newName);
                    tm.commit(transaction);
                }
                return true;

            }
        } catch (final Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean renameTo(File dest) {

//    	System.out.println("rename from "+uri+" to "+dest.getPath());

        final XmldbURI destinationPath = ((Resource) dest).uri;

        final BrokerPool db;
        try {
            db = BrokerPool.getInstance();
        } catch (final EXistException e) {
            return false;
        }

        try (final DBBroker broker = db.getBroker();
                final Collection source = broker.openCollection(uri.removeLastSegment(), LockMode.WRITE_LOCK)) {

            if (source == null) {
                return false;
            }
            final DocumentImpl doc = source.getDocument(broker, uri.lastSegment());
            if (doc == null) {
                return false;
            }

            try(final Collection destination = broker.openCollection(destinationPath.removeLastSegment(), LockMode.WRITE_LOCK)) {
                if (destination == null) {
                    return false;
                }

                final XmldbURI newName = destinationPath.lastSegment();

                final TransactionManager tm = db.getTransactionManager();
                try (final Txn transaction = tm.beginTransaction()) {
                    moveResource(broker, transaction, doc, source, destination, newName);

//                resource = null;
//                collection = null;
//                initialized = false;
//                uri = ((Resource)dest).uri;

                    tm.commit(transaction);
                    return true;
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private synchronized Path serialize(final DBBroker broker, final DocumentImpl doc) throws IOException {
        if (file != null) {
            throw new IOException(doc.getFileURI().toString() + " locked.");
        }

        try {
            final Serializer serializer = broker.getSerializer();
            serializer.setUser(broker.getCurrentSubject());
            serializer.setProperties(XML_OUTPUT_PROPERTIES);

            final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
            file = temporaryFileManager.getTemporaryFile();

            try (final Writer w = Files.newBufferedWriter(file, UTF_8)) {
                serializer.serialize(doc, w);
            }

            return file;

        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    private synchronized Path copy(final DBBroker broker, final Txn transaction, final BinaryDocument binaryDocument) throws IOException {
        if (file != null) {
            throw new IOException(binaryDocument.getFileURI().toString() + " locked.");
        }

        try {
            final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
            file = temporaryFileManager.getTemporaryFile();

            try (final InputStream is = broker.getBinaryResource(transaction, binaryDocument)) {
                Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
            }

            return file;

        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    protected synchronized void freeFile() throws IOException {

        if (isXML()) {
            if (file == null) {
                //XXX: understand why can't throw exception
                //throw new IOException();
                return;
            }

            FileUtils.deleteQuietly(file);

            file = null;
        }
    }

    protected synchronized void uploadTmpFile() throws IOException {
        if (file == null) {
            throw new IOException();
        }

        final BrokerPool db;

        try {
            db = BrokerPool.getInstance();
        } catch (final EXistException e) {
            throw new IOException(e);
        }

        final TransactionManager tm = db.getTransactionManager();
        try (final DBBroker broker = db.getBroker();
             final Txn txn = tm.beginTransaction()) {

            FileInputSource is = new FileInputSource(file);

            final IndexInfo info = collection.validateXMLResource(txn, broker, uri.lastSegment(), is);
//	        info.getDocument().getMetadata().setMimeType(mimeType.getName());

            is = new FileInputSource(file);
            collection.store(txn, broker, info, is);

            tm.commit(txn);

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private void moveResource(DBBroker broker, Txn txn, DocumentImpl doc, Collection source, Collection destination, XmldbURI newName) throws PermissionDeniedException, LockException, IOException, SAXException, EXistException {

        final MimeTable mimeTable = MimeTable.getInstance();

        final boolean isXML = mimeTable.isXMLContent(newName.toString());

        final MimeType mimeType = mimeTable.getContentTypeFor(newName);

        if (mimeType != null && !mimeType.getName().equals(doc.getMetadata().getMimeType())) {
            doc.getMetadata().setMimeType(mimeType.getName());
            broker.storeXMLResource(txn, doc);

            doc = source.getDocument(broker, uri.lastSegment());
        }

        if (isXML) {
            if (doc.getResourceType() == DocumentImpl.XML_FILE) {
                //XML to XML
                //move to same type as it
                broker.moveResource(txn, doc, destination, newName);

            } else {
                //convert BINARY to XML
                try (final InputStream is1 = broker.getBinaryResource(txn, (BinaryDocument)doc)) {

                    final IndexInfo info = destination.validateXMLResource(txn, broker, newName, new InputSource(is1));
                    info.getDocument().getMetadata().setMimeType(mimeType.getName());

                    try (final InputStream is2 = broker.getBinaryResource(txn, (BinaryDocument)doc)) {
                        destination.store(txn, broker, info, new InputSource(is2));
                    }

                    source.removeBinaryResource(txn, broker, doc);
                }
            }
        } else {
            if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                //BINARY to BINARY

                //move to same type as it
                broker.moveResource(txn, doc, destination, newName);

            } else {
                //convert XML to BINARY
                // xml file
                final Serializer serializer = broker.getSerializer();
                serializer.setUser(broker.getCurrentSubject());
                serializer.setProperties(XML_OUTPUT_PROPERTIES);

                final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
                Path tempFile = null;
                try {
                    tempFile = temporaryFileManager.getTemporaryFile();

                    try(final Writer w = Files.newBufferedWriter(tempFile, UTF_8)) {
                        serializer.serialize(doc, w);
                    }

                    try (final InputStream is = Files.newInputStream(tempFile)) {
                        final DocumentMetadata meta = doc.getMetadata();

                        final Date created = new Date(meta.getCreated());
                        final Date lastModified = new Date(meta.getLastModified());

                        BinaryDocument binary = destination.validateBinaryResource(txn, broker, newName);

                        binary = destination.addBinaryResource(txn, broker, binary, is, mimeType.getName(), -1, created, lastModified);

                        source.removeXMLResource(txn, broker, doc.getFileURI());
                    }
                } finally {
                    if (tempFile != null) {
                        temporaryFileManager.returnTemporaryFile(tempFile);
                    }
                }
            }
        }
    }

    public boolean delete() {
        final BrokerPool db;
        final TransactionManager tm;

        try {
            db = BrokerPool.getInstance();
            tm = db.getTransactionManager();
        } catch (final EXistException e) {
            return false;
        }

        try (final DBBroker broker = db.getBroker();
                final Collection collection = broker.openCollection(uri.removeLastSegment(), LockMode.WRITE_LOCK)) {
            if (collection == null) {
                return false;
            }
            // keep the write lock in the transaction
            //transaction.registerLock(collection.getLock(), LockMode.WRITE_LOCK);

            final DocumentImpl doc = collection.getDocument(broker, uri.lastSegment());
            if (doc == null) {
                return true;
            }

            try (final Txn txn = tm.beginTransaction()) {
                if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                    collection.removeBinaryResource(txn, broker, doc);
                } else {
                    collection.removeXMLResource(txn, broker, uri.lastSegment());
                }

                tm.commit(txn);

                return true;
            }
        } catch (final EXistException | IOException | PermissionDeniedException | LockException | TriggerException e) {
            LOG.error(e);
            return false;
        }
    }

    public boolean createNewFile() throws IOException {
        final BrokerPool db;

        try {
            db = BrokerPool.getInstance();
        } catch (final EXistException e) {
            throw new IOException(e);
        }

        try (final DBBroker broker = db.getBroker()) {
            try {
                if (uri.endsWith("/")) {
                    throw new IOException("It collection, but should be resource: " + uri);
                }
            } catch (final Exception e) {
                throw new IOException(e);
            }

            final XmldbURI collectionURI = uri.removeLastSegment();
            collection = broker.getCollection(collectionURI);
            if (collection == null) {
                throw new IOException("Collection not found: " + collectionURI);
            }

            final XmldbURI fileName = uri.lastSegment();

            try {
                resource = broker.getResource(uri, Permission.READ);
            } catch (final PermissionDeniedException e1) {
            } finally {
                if (resource != null) {
                    collection = resource.getCollection();
                    initialized = true;

                    return false;
                }
            }

            MimeType mimeType = MimeTable.getInstance().getContentTypeFor(fileName);

            if (mimeType == null) {
                mimeType = MimeType.BINARY_TYPE;
            }


            final TransactionManager tm = db.getTransactionManager();
            try (final Txn transaction = tm.beginTransaction()) {
                if (mimeType.isXMLType()) {
                    // store as xml resource
                    final String str = "<empty/>";
                    final IndexInfo info = collection.validateXMLResource(transaction, broker, fileName, str);
                    info.getDocument().getMetadata().setMimeType(mimeType.getName());
                    collection.store(transaction, broker, info, str);

                } else {
                    // store as binary resource
                    try (final InputStream is = new FastByteArrayInputStream(new byte[0])) {
                        final int docId = broker.getNextResourceId(transaction);
                        final BinaryDocument blob = new BinaryDocument(db, collection, docId, fileName);
                        collection.addBinaryResource(transaction, broker, blob, is,
                                mimeType.getName(), 0L, new Date(), new Date());
                    }
                }

                tm.commit(transaction);
            } catch (final Exception e) {
                LOG.error(e);
                throw new IOException(e);
            }

        } catch (final Exception e) {
            LOG.error(e);
            return false;
        }

        return true;
    }


    private synchronized void init() throws IOException {
        if (initialized) {
            collection = null;
            resource = null;
            initialized = false;
        }

        try {
            final BrokerPool db = BrokerPool.getInstance();
            try (final DBBroker broker = db.getBroker()) {
                //collection
                if (uri.endsWith("/")) {
                    collection = broker.getCollection(uri);
                    if (collection == null) {
                        throw new IOException("Resource not found: " + uri);
                    }

                    //resource
                } else {
                    try(final LockedDocument lockedResource = broker.getXMLResource(uri, LockMode.READ_LOCK)) {
                        resource = lockedResource == null ? null : lockedResource.getDocument();
                        if (resource == null) {
                            //may be, it's collection ... checking ...
                            collection = broker.getCollection(uri);
                            if (collection == null) {
                                throw new IOException("Resource not found: " + uri);
                            }
                        } else {
                            collection = resource.getCollection();
                        }
                    }
                }
            }
        } catch (final IOException e) {
            throw e;
        } catch (final Exception e) {
            throw new IOException(e);
        }

        initialized = true;
    }

    private Permission getPermission() throws IOException {
        init();

        if (resource != null) {
            return resource.getPermissions();
        }

        if (collection != null) {
            return collection.getPermissionsNoLock();
        }

        throw new IOException("this never should happen");
    }

    private Subject getBrokerUser() throws IOException {
        try {
            final BrokerPool db = BrokerPool.getInstance();
            try (final DBBroker broker = db.getBroker()) {
                return broker.getCurrentSubject();
            }
        } catch (final EXistException e) {
            throw new IOException(e);
        }
    }

    public Reader getReader() throws IOException {
        final InputStream is = getConnection().getInputStream();
        final BufferedInputStream bis = new BufferedInputStream(is);
        return new InputStreamReader(bis);
    }

    public BufferedReader getBufferedReader() throws IOException {
        return new BufferedReader(getReader());
    }

    private URLConnection connection = null;

    private URLConnection getConnection() throws IOException {
        if (connection == null) {
            try {
                final BrokerPool db = BrokerPool.getInstance();
                try (final DBBroker broker = db.getBroker()) {
                    final Subject subject = broker.getCurrentSubject();

                    final URL url = new URL("xmldb:exist://jsessionid:" + subject.getSessionId() + "@" + uri.toString());
                    connection = url.openConnection();
                }
            } catch (final IllegalArgumentException e) {
                throw new IOException(e);
            } catch (final MalformedURLException e) {
                throw new IOException(e);
            } catch (final EXistException e) {
                throw new IOException(e);
            }
        }
        return connection;
    }

    public InputStream getInputStream() throws IOException {
        return getConnection().getInputStream();
    }

    public Writer getWriter() throws IOException {
        return new BufferedWriter(new OutputStreamWriter(getOutputStream(false)));
    }

    public OutputStream getOutputStream() throws IOException {
        return getOutputStream(false);
    }

    public OutputStream getOutputStream(boolean append) throws IOException {
        //XXX: code append
        if (append) {
            LOG.error("BUG: OutputStream in append mode!");
        }
        return getConnection().getOutputStream();
    }

    public DocumentImpl getDocument() throws IOException {
        init();

        return resource;
    }

    public Collection getCollection() throws IOException {
        if (!initialized) {
            try {
                final BrokerPool db = BrokerPool.getInstance();
                try (final DBBroker broker = db.getBroker()) {
                    if (uri.endsWith("/")) {
                        collection = broker.getCollection(uri);
                    } else {
                        collection = broker.getCollection(uri);
                        if (collection == null) {
                            collection = broker.getCollection(uri.removeLastSegment());
                        }
                    }
                    if (collection == null) {
                        throw new IOException("Collection not found: " + uri);
                    }

                    return collection;
                }
            } catch (final Exception e) {
                throw new IOException(e);
            }
        }

        if (resource == null) {
            return collection;
        } else {
            return resource.getCollection();
        }
    }

    public String[] list() {

        if (isDirectory()) {
            try {
                final BrokerPool db = BrokerPool.getInstance();
                try (final DBBroker broker = db.getBroker()) {

                    final List<String> list = new ArrayList<>();
                    for (final CollectionEntry entry : collection.getEntries(broker)) {
                        list.add(entry.getUri().lastSegment().toString());
                    }

                    return list.toArray(new String[list.size()]);
                }
            } catch (final LockException | PermissionDeniedException | IOException | EXistException e) {
                LOG.error(e);
                return new String[0];
            }
        }

        return new String[0];
    }

//    public String[] list(FilenameFilter filter) {
//    	throw new IllegalAccessError("not implemeted");
//    }

    public File[] listFiles() {
        if (!isDirectory()) {
            return null;
        }

        if (collection == null) {
            return null;
        }

        try {
            final BrokerPool db = BrokerPool.getInstance();
            try (final DBBroker broker = db.getBroker();
                    final ManagedCollectionLock collectionLock = db.getLockManager().acquireCollectionReadLock(collection.getURI())) {

                final File[] children = new File[collection.getChildCollectionCount(broker) +
                        collection.getDocumentCount(broker)];

                //collections
                int j = 0;
                for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext(); j++) {
                    children[j] = new Resource(collection.getURI().append(i.next()));
                }

                //collections
                final List<XmldbURI> allresources = new ArrayList<>();
                for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                    final DocumentImpl doc = i.next();

                    // Include only when (1) locktoken is present or (2)
                    // locktoken indicates that it is not a null resource
                    final LockToken lock = doc.getMetadata().getLockToken();
                    if (lock == null || (!lock.isNullResource())) {
                        allresources.add(doc.getURI());
                    }
                }

                // Copy content of list into String array.
                for (final Iterator<XmldbURI> i = allresources.iterator(); i.hasNext(); j++) {
                    children[j] = new Resource(i.next());
                }

                return children;
            } catch (final Exception e) {
                return null;
            }

        } catch (final Exception e) {
            return null;

        }
    }

    public File[] listFiles(FilenameFilter filter) {
        throw new IllegalAccessError("not implemeted");
    }

    public File[] listFiles(FileFilter filter) {
        throw new IllegalAccessError("not implemeted");
    }

    public synchronized long length() {
        try {
            init();
        } catch (final IOException e) {
            return 0L;
        }

        if (resource != null) {
            //report size for binary resource only
            if (resource instanceof BinaryDocument) {
                return resource.getContentLength();
            }
        }

        return 0L;
    }

    private static XmldbURI normalize(final XmldbURI uri) {
        return uri.startsWith(XmldbURI.ROOT_COLLECTION_URI) ?
                uri :
                uri.prepend(XmldbURI.ROOT_COLLECTION_URI);
    }

    public String getPath() {
        return normalize(uri).toString();// uri.toString();
    }

    public String getAbsolutePath() {
        return normalize(uri).toString();// uri.toString();
    }

    public boolean isXML() throws IOException {
        init();

        if (resource != null) {
            if (resource instanceof BinaryDocument) {
                return false;
            } else {
                return true;
            }
        }

        return false;
    }

    protected Path getFile() throws FileNotFoundException {
        if (isDirectory()) {
            throw new FileNotFoundException("unsupported operation for collection.");
        }

        DocumentImpl doc;
        try {
            if (!exists()) {
                createNewFile();
            }

            doc = getDocument();
        } catch (final IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }

        try {
            final BrokerPool db = BrokerPool.getInstance();
            try (final DBBroker broker = db.getBroker();
            final Txn transaction = db.getTransactionManager().beginTransaction()) {

                final Path result;
                if (doc instanceof BinaryDocument) {
                    result = copy(broker, transaction, (BinaryDocument)doc);
                } else {
                    result = serialize(broker, doc);
                }

                transaction.commit();

                return result;
            }
        } catch (final Exception e) {
            throw new FileNotFoundException(e.getMessage());
        }
//		throw new FileNotFoundException("unsupported operation for "+doc.getClass()+".");
    }

    public boolean setReadOnly() {
        try {
            modifyMetadata(new ModifyMetadata() {

                @Override
                public void modify(final DBBroker broker, final DocumentImpl resource) throws IOException {
                    final Permission perm = resource.getPermissions();
                    try {
                        PermissionFactory.chmod(broker, perm, Optional.of(perm.getMode() | (READ << 6) & ~(WRITE << 6)), Optional.empty());
                    } catch (PermissionDeniedException e) {
                        throw new IOException(e);
                    }
                }

                @Override
                public void modify(final DBBroker broker, final Collection collection) throws IOException {
                    final Permission perm = collection.getPermissionsNoLock();
                    try {
                        PermissionFactory.chmod(broker, perm, Optional.of(perm.getMode() | (READ << 6) & ~(WRITE << 6)), Optional.empty());
                    } catch (PermissionDeniedException e) {
                        throw new IOException(e);
                    }
                }

            });
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        try {
            modifyMetadata(new ModifyMetadata() {

                @Override
                public void modify(final DBBroker broker, final DocumentImpl resource) throws IOException {
                    final Permission perm = resource.getPermissions();
                    try {
                        PermissionFactory.chmod(broker, perm, Optional.of(perm.getMode() | (EXECUTE << 6)), Optional.empty());
                    } catch (PermissionDeniedException e) {
                        throw new IOException(e);
                    }
                }

                @Override
                public void modify(final DBBroker broker, final Collection collection) throws IOException {
                    final Permission perm = collection.getPermissionsNoLock();
                    try {
                        PermissionFactory.chmod(broker, perm, Optional.of(perm.getMode() | (EXECUTE << 6)), Optional.empty());
                    } catch (PermissionDeniedException e) {
                        throw new IOException(e);
                    }
                }

            });
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    public boolean canExecute() {
        try {
            return getPermission().validate(getBrokerUser(), EXECUTE);
        } catch (final IOException e) {
            return false;
        }
    }


    public boolean canRead() {
        try {
            return getPermission().validate(getBrokerUser(), READ);
        } catch (final IOException e) {
            return false;
        }
    }

    long lastModified = 0L;

    public boolean setLastModified(final long time) {
        lastModified = time;
        try {
            modifyMetadata(new ModifyMetadata() {

                @Override
                public void modify(final DBBroker broker, DocumentImpl resource) throws IOException {
                    resource.getMetadata().setLastModified(time);
                }

                @Override
                public void modify(final DBBroker broker, Collection collection) throws IOException {
                    throw new IOException("LastModified can't be set for collection.");
                }

            });
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    public long lastModified() {
        try {
            init();
        } catch (final IOException e) {
            return lastModified;
        }

        if (resource != null) {
            return resource.getMetadata().getLastModified();
        }

        if (collection != null) {
            //TODO: need lastModified for collection
            return collection.getCreationTime();
        }
        return lastModified;
    }

    interface ModifyMetadata {
        void modify(DBBroker broker, DocumentImpl resource) throws IOException;
        void modify(DBBroker broker, Collection collection) throws IOException;
    }

    private void modifyMetadata(ModifyMetadata method) throws IOException {
//    	if (initialized) {return;}

        final BrokerPool db;

        try {
            db = BrokerPool.getInstance();
        } catch (final EXistException e) {
            throw new IOException(e);
        }

        try (final DBBroker broker = db.getBroker()) {

            final TransactionManager tm = db.getTransactionManager();

            try {
                //collection
                if (uri.endsWith("/")) {
                    collection = broker.getCollection(uri);
                    if (collection == null) {
                        throw new IOException("Resource not found: " + uri);
                    }

                    //resource
                } else {
                    try(final LockedDocument lockedResource = broker.getXMLResource(uri, LockMode.READ_LOCK)) {
                        resource = lockedResource == null ? null : lockedResource.getDocument();
                        if (resource == null) {
                            //may be, it's collection ... checking ...
                            collection = broker.getCollection(uri);
                            if (collection == null) {
                                throw new IOException("Resource not found: " + uri);
                            }

                            try (final Txn txn = tm.beginTransaction()) {
                                method.modify(broker, collection);
                                broker.saveCollection(txn, collection);

                                tm.commit(txn);
                            }

                        } else {
                            collection = resource.getCollection();

                            try (final Txn txn = tm.beginTransaction()) {
                                method.modify(broker, resource);
                                broker.storeMetadata(txn, resource);

                                tm.commit(txn);
                            }
                        }
                    }
                }
            } catch (final Exception e) {
                LOG.error(e);
                throw new IOException(e);
            }
        } catch (final EXistException e) {
            LOG.error(e);
            throw new IOException(e);
        }

        initialized = true;
    }
}
