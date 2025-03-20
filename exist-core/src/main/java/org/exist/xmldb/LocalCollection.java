/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xmldb;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import javax.xml.transform.OutputKeys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockToken;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.function.FunctionE;
import org.exist.xmldb.function.LocalXmldbCollectionFunction;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xmldb.api.base.*;
import org.xmldb.api.base.ServiceProviderCache.ProviderRegistry;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

import static com.evolvedbinary.j8fu.Try.Try;
import static org.xmldb.api.base.ResourceType.BINARY_RESOURCE;
import static org.xmldb.api.base.ResourceType.XML_RESOURCE;

/**
 * A local implementation of the Collection interface. This
 * is used when the database is running in embedded mode.
 *
 * Extends Observable to allow status callbacks during indexing.
 * Methods storeResource notifies registered observers about the
 * progress of the indexer by passing an object of type ProgressIndicator
 * to the observer.
 *
 * @author     wolf
 */
public class LocalCollection extends AbstractLocal implements EXistCollection {

    private static final Logger LOG = LogManager.getLogger(LocalCollection.class);

    /**
     * Property to be passed to {@link #setProperty(String, String)}.
     * When storing documents, pass HTML files through an HTML parser
     * (NekoHTML) instead of the XML parser. The HTML parser will normalize
     * the HTML into well-formed XML.
     */
    public final static String NORMALIZE_HTML = "normalize-html";

    private final static Properties defaultProperties = new Properties();
    static {
        defaultProperties.setProperty(OutputKeys.INDENT, "yes");
        defaultProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
        defaultProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
        defaultProperties.setProperty(NORMALIZE_HTML, "no");
        defaultProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        defaultProperties.setProperty(EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION, "no");
        defaultProperties.setProperty(EXistOutputKeys.OUTPUT_DOCTYPE, "yes");
    }

    private final XmldbURI path;
    private final Random random = new Random();
    private final ServiceProviderCache serviceProviderCache = ServiceProviderCache.withRegistered(this::registerProvders);

    private Properties properties = new Properties(defaultProperties);
    private boolean needsSync = false;

    /**
     * Create a collection with no parent (root collection).
     *
     * @param user the user
     * @param brokerPool the broker pool
     * @param collection the collection
     * @throws XMLDBException if an error occurs opening the collection
     */
    public LocalCollection(final Subject user, final BrokerPool brokerPool, final XmldbURI collection) throws XMLDBException {
        this(user, brokerPool, null, collection);
    }

    /**
     * Create a collection identified by its name. Load the collection from the database.
     *
     * @param user the user
     * @param brokerPool the broker pool
     * @param parent the parent collection
     * @param name the name of this collection
     * @throws XMLDBException if an error occurs opening the collection
     */
    public LocalCollection(final Subject user, final BrokerPool brokerPool, final LocalCollection parent, final XmldbURI name) throws XMLDBException {
        super(user, brokerPool, parent);

        if(name == null) {
            this.path = XmldbURI.ROOT_COLLECTION_URI.toCollectionPathURI();
        } else {
            this.path = name.toCollectionPathURI();
        }

        /*
        no-op, used to make sure the current user can open the collection!
        will throw an XMLDBException if they cannot
        we are careful to throw the exception outside of the transaction operation
        so that it does not immediately close the current transaction and unwind the stack,
        this is because not being able to open a collection is a valid operation e.g. xmldb:collection-available
        */
        final Optional<XMLDBException> openException = withDb((broker, transaction) -> {
            try {
                return this.<Optional<XMLDBException>>read(broker, transaction, ErrorCodes.NO_SUCH_COLLECTION).apply((collection, broker1, transaction1) -> Optional.empty());
            } catch(final XMLDBException e) {
                return Optional.of(e);
            }
        });

        if(openException.isPresent()) {
            throw openException.get();
        }
    }

    protected boolean checkOwner(final Collection collection, final Account account) throws XMLDBException {
        return account.equals(collection.getPermissions().getOwner());
    }

    protected boolean checkPermissions(final Collection collection, final int perm) throws XMLDBException {
        return collection.getPermissions().validate(user, perm);
    }

    /**
     * Close the current collection. Calling this method will flush all
     * open buffers to disk.
     */
    @Override
    public void close() throws XMLDBException {
        if (needsSync) {
            withDb((broker, transaction) -> {
                broker.sync(Sync.MAJOR);
                return null;
            });
        }
    }

    /**
     * Creates a unique name for a database resource
     * Uniqueness is only guaranteed within the eXist instance
     * 
     * The name is based on a hex encoded string of a random integer
     * and will have the format xxxxxxxx.xml where x is in the range
     * 0 to 9 and a to f 
     * 
     * @return the unique resource name 
     */
    @Override
    public String createId() throws XMLDBException {
        return this.<String>read().apply((collection, broker, transaction) -> {
            XmldbURI id;
            boolean ok;
            do {
                ok = true;
                id = XmldbURI.create(Integer.toHexString(random.nextInt()) + ".xml");
                // check if this ID does already exist
                if (collection.hasDocument(broker, id)) {
                    ok = false;
                }

                if (collection.hasChildCollection(broker, id)) {
                    ok = false;
                }

            } while (!ok);
            return id.toString();
        });
    }

    @Override
    public <R extends Resource> R createResource(String id, Class<R> type) throws XMLDBException {
        if (id == null) {
            id = createId();
        }

        final XmldbURI idURI;
        try {
            idURI = XmldbURI.xmldbUriFor(id);
        } catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        final R r;
        if (XMLResource.class.isAssignableFrom(type)) {
            r = (R)new LocalXMLResource(user, brokerPool, this, idURI);
        } else if (BinaryResource.class.isAssignableFrom(type)) {
            r = (R)new LocalBinaryResource(user, brokerPool, this, idURI);
        } else {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Unknown resource type: " + type);
        }

        ((AbstractEXistResource)r).isNewResource = true;
        return r;
    }

    @Override
    public org.xmldb.api.base.Collection getChildCollection(final String name) throws XMLDBException {

        final XmldbURI childURI;
        try {
            childURI = XmldbURI.xmldbUriFor(name);
        } catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }

        final XmldbURI nameUri = this.<XmldbURI>read().apply((collection, broker, transaction) -> {
            XmldbURI childName = null;
            if (collection.hasChildCollection(broker, childURI)) {
                childName = getPathURI().append(childURI);
            }
            return childName;
        });

        if(nameUri != null) {
            return new LocalCollection(user, brokerPool, this, nameUri);
        } else {
            return null;
        }
    }

    @Override
    public int getChildCollectionCount() throws XMLDBException {
        return this.<Integer>read().apply((collection, broker, transaction) -> {
            if(checkPermissions(collection, Permission.READ)) {
                return collection.getChildCollectionCount(broker);
            } else {
                return 0;
            }
        });
    }

    @Override
    public String getName() throws XMLDBException {
        return withDb(this::getName);
    }

    /**
     * Similar to {@link org.exist.xmldb.LocalCollection#getName()}
     * but useful for operations within the XML:DB Local API
     * that are already working within a transaction
     */
    String getName(final DBBroker broker, final Txn transaction) throws XMLDBException {
        return this.<String>read(broker, transaction).apply((collection, broker1, transaction1) -> collection.getURI().toString());
    }

    @Override
    public org.xmldb.api.base.Collection getParentCollection() throws XMLDBException {
        return withDb((broker, transaction) -> {
            if (getName(broker, transaction).equals(XmldbURI.ROOT_COLLECTION)) {
                return null;
            }

            if (collection == null) {
                final XmldbURI parentUri = this.<XmldbURI>read(broker, transaction).apply((collection, broker1, transaction1) -> collection.getParentURI());
                this.collection = new LocalCollection(user, brokerPool, null, parentUri);
            }
            return collection;
        });
    }

    public String getPath() throws XMLDBException {
        return path.toString();
    }

    @Override
    public XmldbURI getPathURI() {
        return path;
    }

    @Override
    public Resource getResource(final String id) throws XMLDBException {
        final XmldbURI idURI;
        try {
            idURI = XmldbURI.xmldbUriFor(id);
        } catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }

        return withDb((broker, transaction) -> getResource(broker, transaction, idURI));
    }

    /**
     * Similar to {@link org.exist.xmldb.LocalCollection#getResource(String)}
     * but useful for operations within the XML:DB Local API
     * that are already working within a transaction
     */
    Resource getResource(final DBBroker broker, final Txn transaction, final String id) throws XMLDBException {
        final XmldbURI idURI;
        try {
            idURI = XmldbURI.xmldbUriFor(id);
        } catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI, e);
        }

        return getResource(broker, transaction, idURI);
    }

    Resource getResource(final DBBroker broker, final Txn transaction, final XmldbURI idURI) throws XMLDBException {
        return this.<Resource>read(broker, transaction).apply((collection, broker1, transaction1) -> {
            try(final LockedDocument lockedDocument = collection.getDocumentWithLock(broker1, idURI, LockMode.READ_LOCK)) {

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collection.close();

                final DocumentImpl document = lockedDocument == null ? null : lockedDocument.getDocument();
                if (document == null) {
                    LOG.warn("Resource {} not found", idURI);
                    return null;
                }

                final Resource r = switch (document.getResourceType()) {
                    case DocumentImpl.XML_FILE -> new LocalXMLResource(user, brokerPool, this, idURI);
                    case DocumentImpl.BINARY_FILE -> new LocalBinaryResource(user, brokerPool, this, idURI);
                    default -> throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Unknown resource type");
                };
                ((AbstractEXistResource) r).setMimeType(document.getMimeType());
                return r;
            }
        });
    }

    @Override
    public int getResourceCount() throws XMLDBException {
        return this.<Integer>read().apply((collection, broker, transaction) -> {
            if(checkPermissions(collection, Permission.READ)) {
                return collection.getDocumentCount(broker);
            } else {
                return 0;
            }
        });
    }

    @Override
    public <S extends Service> boolean hasService(Class<S> serviceType) {
        return serviceProviderCache.hasService(serviceType);
    }

    @Override
    public <S extends Service> Optional<S> findService(Class<S> serviceType) {
        return serviceProviderCache.findService(serviceType);
    }

    @Override
    public <S extends Service> S getService(Class<S> serviceType) throws XMLDBException {
        return serviceProviderCache.getService(serviceType);
    }

    final void registerProvders(ProviderRegistry registry) {
        final Supplier<LocalXPathQueryService> queryServiceSupplier =
                () -> new LocalXPathQueryService(user, brokerPool, this);
        registry.add(XPathQueryService.class, queryServiceSupplier);
        registry.add(XQueryService.class, queryServiceSupplier);
        final Supplier<LocalCollectionManagementService> collectionServiceSupplier =
                () -> new LocalCollectionManagementService(user, brokerPool, this);
        registry.add(CollectionManagementService.class, collectionServiceSupplier);
        registry.add(EXistCollectionManagementService.class, collectionServiceSupplier);
        final Supplier<LocalUserManagementService> userManagementServiceSupplier =
                () -> new LocalUserManagementService(user, brokerPool, this);
        registry.add(UserManagementService.class, userManagementServiceSupplier);
        registry.add(EXistUserManagementService.class, userManagementServiceSupplier);
        registry.add(DatabaseInstanceManager.class, () -> new LocalDatabaseInstanceManager(user, brokerPool));
        registry.add(XUpdateQueryService.class, () -> new LocalXUpdateQueryService(user, brokerPool, this));
        registry.add(IndexQueryService.class, () -> new LocalIndexQueryService(user, brokerPool, this));
        registry.add(EXistRestoreService.class, () -> new LocalRestoreService(user, brokerPool, this));
    }

    @Override
    public boolean isOpen() throws XMLDBException {
        return true;
    }

    @Override
    public List<String> listChildCollections() throws XMLDBException {
        return this.<List<String>>read().apply((collection, broker, transaction) -> {
            final List<String> collections = new ArrayList<>(collection.getChildCollectionCount(broker));
            for(final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext(); ) {
                collections.add(i.next().toString());
            }
            return collections;
        });
    }

    @Override
    public String[] getChildCollections() throws XMLDBException {
        return  listChildCollections().toArray(new String[0]);
    }

    /**
     * Retrieve the list of resources in the collection.
     *
     * @return the list of resources.
     * 
     * @throws XMLDBException if and invalid collection was specified, or if permission is denied
     */
    @Override
    public List<String> listResources() throws XMLDBException {
    	return this.<List<String>>read().apply((collection, broker, transaction) -> {
            final List<String> resources = new ArrayList<>();
            for(final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                final DocumentImpl doc = i.next();

                try(final ManagedDocumentLock documentLock = broker.getBrokerPool().getLockManager().acquireDocumentReadLock(doc.getURI())) {

                    // Include only when (1) lockToken is present or (2)
                    // lockToken indicates that it is not a null resource
                    final LockToken lock = doc.getLockToken();
                    if (lock == null || (!lock.isNullResource())) {
                        resources.add(doc.getFileURI().toString());
                    }
                }
            }
            return resources;
        });
    }

    @Override
    public String[] getResources() throws XMLDBException {
        return listResources().toArray(new String[0]);
    }

    @Override
    public void removeResource(final Resource res) throws XMLDBException {
        if(res == null) {
            return;
        }
        
        final XmldbURI resURI;
        try {
            resURI = XmldbURI.xmldbUriFor(res.getId());
        } catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }

        modify().apply((collection, broker, transaction) -> {
            //Check that the document exists
            try(final LockedDocument lockedDocument = collection.getDocumentWithLock(broker, resURI, LockMode.WRITE_LOCK)) {
                if (lockedDocument == null) {

                    // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                    collection.close();

                    throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource " + resURI + " not found");
                }

                if (XML_RESOURCE.equals(res.getResourceType())) {
                    collection.removeXMLResource(transaction, broker, resURI);
                } else {
                    collection.removeBinaryResource(transaction, broker, resURI);
                }

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collection.close();
            }

            return null;
        });

        this.needsSync = true;
    }

    public Properties getProperties() {
        return properties;
    }

    @Override
    public String getProperty(final String property) throws XMLDBException {
        return properties.getProperty(property);
    }

    @Override
    public String getProperty(final String property, final String defaultValue) throws XMLDBException {
        return properties.getProperty(property, defaultValue);
    }

    public void setProperties(final Properties properties) {
        if (properties == null) {
            return;
        }
        this.properties = properties;
    }

    @Override
    public void setProperty(final String property, final String value) throws XMLDBException {
        properties.setProperty(property, value);
    }

    @Override
    public void storeResource(final Resource resource) throws XMLDBException {
        storeResource(resource, null, null);
    }

    @Override
    public void storeResource(final Resource resource, final Instant a, final Instant b) throws XMLDBException {
        if (XML_RESOURCE.equals(resource.getResourceType())) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("storing document {}", resource.getId());
            }
            ((LocalXMLResource)resource).datecreated = toDate(a);
            ((LocalXMLResource)resource).datemodified = toDate(b);
            storeXMLResource((LocalXMLResource) resource);

        } else if (BINARY_RESOURCE.equals(resource.getResourceType())) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("storing binary resource {}", resource.getId());
            }
            ((LocalBinaryResource)resource).datecreated = toDate(a);
            ((LocalBinaryResource)resource).datemodified = toDate(b);
            storeBinaryResource((LocalBinaryResource) resource);   
        } else {
            throw new XMLDBException(ErrorCodes.UNKNOWN_RESOURCE_TYPE, "unknown resource type: " + resource.getResourceType());
        }
        
        ((AbstractEXistResource)resource).isNewResource = false;
        this.needsSync = true;
    }

    private Date toDate(Instant instant) {
        if (instant!=null) {
            return new Date(instant.toEpochMilli());
        }
        return null;
    }

    private void storeBinaryResource(final LocalBinaryResource res) throws XMLDBException {
        final XmldbURI resURI;
        try {
            resURI = XmldbURI.xmldbUriFor(res.getId());
        } catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }

        modify().apply((collection, broker, transaction) -> {
            try {
                final String strMimeType = res.getMimeType(broker, transaction);
                final MimeType mimeType = strMimeType != null ? MimeTable.getInstance().getContentType(strMimeType) : null;
                final long conLength = res.getStreamLength();
                if (conLength != -1) {
                    broker.storeDocument(transaction, resURI, new InputStreamSupplierInputSource(() -> Try(() -> res.getStreamContent(broker, transaction)).getOrElse((InputStream) null)), mimeType, res.datecreated, res.datemodified, null, null, null, collection);
                } else {
                    broker.storeDocument(transaction, resURI, new StringInputSource((byte[]) res.getContent(broker, transaction)), mimeType, res.datecreated, res.datemodified, null, null, null, collection);
                }
            } catch(final EXistException | SAXException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            }
            return null;
        });
    }

    private void storeXMLResource(final LocalXMLResource res) throws XMLDBException {
        final XmldbURI resURI;
        try {
            resURI = XmldbURI.xmldbUriFor(res.getId());
        } catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }

        modify().apply((collection, broker, transaction) -> {
            String uri = null;
            if(res.file != null) {
                uri = res.file.toUri().toASCIIString();
            }

//          for(final Observer observer : observers) {
//              collection.addObserver(observer);
//          }

            try(final ManagedDocumentLock documentLock = broker.getBrokerPool().getLockManager().acquireDocumentWriteLock(collection.getURI().append(resURI))) {

                final String strMimeType = res.getMimeType(broker, transaction);
                final MimeType mimeType = strMimeType != null ? MimeTable.getInstance().getContentType(strMimeType) : null;

                if (res.root != null) {
                    collection.storeDocument(transaction, broker, resURI, res.root, mimeType, res.datecreated, res.datemodified, null, null, null);

                } else {
                    final InputSource source;
                    if (uri != null) {
                        source = new InputSource(uri);
                    } else
                        source = Objects.requireNonNullElseGet(res.inputSource, () -> new StringInputSource(res.content));

                    final XMLReader reader;
                    if (useHtmlReader(broker, transaction, res)) {
                        reader = getHtmlReader();
                    } else {
                        reader = null;
                    }

                    broker.storeDocument(transaction, resURI, source, mimeType, res.datecreated, res.datemodified, null, null, reader, collection);
                }

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collection.close();

                return null;

            } catch(final EXistException | SAXException e) {

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collection.close();

                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            }
        });
    }

    /**
     * Determines if a HTML reader should be used for the resource.
     *
     * @param broker the database broker
     * @param transaction the current transaction
     * @param res the html resource
     *
     * @return true if a HTML reader should be used.
     *
     * @throws XMLDBException if the HTML Reader cannot be configured.
     */
    private boolean useHtmlReader(final DBBroker broker, final Txn transaction, final LocalXMLResource res) throws XMLDBException {
        final String normalize = properties.getProperty(NORMALIZE_HTML, "no");
        return (("yes".equalsIgnoreCase(normalize) || "true".equalsIgnoreCase(normalize)) &&
                ("text/html".equals(res.getMimeType(broker, transaction)) || res.getId().endsWith(".htm") ||
                        res.getId().endsWith(".html")));
    }

    /**
     * Get's the HTML Reader
     *
     * @return the HTML reader configured in conf.xml
     *
     * @throws XMLDBException if the HTML reader cannot be retrieved.
     */
    private XMLReader getHtmlReader() throws XMLDBException {
        final Optional<Either<Throwable, XMLReader>> maybeReaderInst = HtmlToXmlParser.getHtmlToXmlParser(brokerPool.getConfiguration());
        if (maybeReaderInst.isPresent()) {
            final Either<Throwable, XMLReader> readerInst = maybeReaderInst.get();
            if (readerInst.isLeft()) {
                final String msg = "Unable to parse HTML to XML please ensure the parser is configured in conf.xml and is present on the classpath";
                final Throwable t = readerInst.left().get();
                LOG.error(msg, t);
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, msg, t);
            } else {
                final XMLReader htmlReader = readerInst.right().get();
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Converting HTML to XML using: {}", htmlReader.getClass().getName());
                }
                return htmlReader;
            }
        } else {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "There is no HTML to XML parser configured in conf.xml");
        }
    }

    @Override
    public Instant getCreationTime() throws XMLDBException {
        return this.<Instant>read().apply((collection, broker, transaction) -> Instant.ofEpochMilli(collection.getCreated()));
    }

    @Override
    public boolean isRemoteCollection() throws XMLDBException {
        return false;
    }

    public XmldbURI getURI() {
        final StringBuilder accessor = new StringBuilder(XmldbURI.XMLDB_URI_PREFIX);
        //TODO : get the name from client
        accessor.append("exist");
        accessor.append("://");
        //No host ;-)
        accessor.append("");
        //No port ;-)
        //No context ;-)
        //accessor.append(getContext());
        try {
            //TODO : cache it when constructed
            return XmldbURI.create(accessor.toString(), getPath());
        } catch(final XMLDBException e) {
            //TODO : should never happen
            return null;
        }
    }

    /**
     * Higher-order-function for performing read-only operations against this collection
     *
     * NOTE this read will occur using the database user set on the collection
     *
     * @param <R> the return type.
     *
     * @return A function to receive a read-only operation to perform against the collection
     *
     * @throws XMLDBException if the collection could not be read
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> read() throws XMLDBException {
        return readOp -> this.<R>read(path).apply(readOp::apply);
    }

    /**
     * Higher-order-function for performing read-only operations against this collection
     *
     * NOTE this read will occur using the database user set on the collection
     *
     * @param <R> the return type.
     * @param errorCode The error code to use in the XMLDBException if the collection does not exist, see {@link ErrorCodes}
     * @return A function to receive a read-only operation to perform against the collection
     *
     * @throws XMLDBException if the collection could not be read
     */
    private <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> read(final int errorCode) throws XMLDBException {
        return readOp -> this.<R>read(path, errorCode).apply(readOp::apply);
    }

    /**
     * Higher-order-function for performing read-only operations against this collection
     *
     * NOTE this read will occur using the database user set on the collection
     *
     * @param <R> the return type.
     * @param broker The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @return A function to receive a read-only operation to perform against the collection
     */
    private <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> read(final DBBroker broker, final Txn transaction) throws XMLDBException {
        return readOp -> this.<R>read(broker, transaction, path).apply(readOp::apply);
    }

    /**
     * Higher-order-function for performing read-only operations against this collection
     *
     * NOTE this read will occur using the database user set on the collection
     *
     * @param <R> the return type.
     * @param broker The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @param errorCode The error code to use in the XMLDBException if the collection does not exist, see {@link ErrorCodes}
     * @return A function to receive a read-only operation to perform against the collection
     *
     * @throws XMLDBException if the collection could not be read
     */
    private <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> read(final DBBroker broker, final Txn transaction, final int errorCode) throws XMLDBException {
        return readOp -> this.<R>read(broker, transaction, path, errorCode).apply(readOp::apply);
    }

    /**
     * Higher-order-function for performing read/write operations against this collection
     *
     * NOTE this read/write will occur using the database user set on the collection
     *
     * @param <R> the return type.
     *
     * @return A function to receive a read/write operation to perform against the collection
     *
     * @throws XMLDBException if the collection could not be modified
     */
    private <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> modify() throws XMLDBException {
        return modifyOp -> this.<R>modify(path).apply(modifyOp::apply);
    }

    /**
     * Higher-order-function for performing read/write operations against this collection
     *
     * NOTE this read/write will occur using the database user set on the collection
     *
     * @param <R> the return type.
     * @param broker The database broker to use when accessing the collection
     * @param transaction The transaction to use when accessing the collection
     *
     * @return A function to receive a read/write operation to perform against the collection
     *
     * @throws XMLDBException if the collection could not be modified
     */
    private <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> modify(final DBBroker broker, final Txn transaction) throws XMLDBException {
        return modifyOp -> this.<R>modify(broker, transaction, path).apply(modifyOp::apply);
    }

    /**
     * Higher-order function for performing lockable operations on this collection
     *
     * @param <R> the return type.
     * @param lockMode the lock mode
     * @param broker The broker to use for the operation
     * @param transaction The transaction to use for the operation
     *
     * @return A function to receive an operation to perform on the locked database collection
     *
     * @throws XMLDBException if the the operation raises an error
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> with(final LockMode lockMode, final DBBroker broker, final Txn transaction) throws XMLDBException {
        return op -> this.<R>with(lockMode, broker, transaction, path).apply(op::apply);
    }
}
