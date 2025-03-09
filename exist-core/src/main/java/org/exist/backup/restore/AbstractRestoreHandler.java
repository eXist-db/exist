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
package org.exist.backup.restore;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.backup.BackupDescriptor;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentTypeImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.ACLPermission;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.internal.RealmImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.DateTimeValue;
import org.w3c.dom.DocumentType;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * SAX Content Handler that can act upon
 * and process Backup Descriptors to
 * restore the contents of the backup
 * into the database.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class AbstractRestoreHandler extends DefaultHandler {

    private static final Logger LOG = LogManager.getLogger(AbstractRestoreHandler.class);

    private static final int STRICT_URI_VERSION = 1;
    private static final int BLOB_STORE_VERSION = 2;

    protected static final String COLLECTION_ELEMENT_NAME = "collection";
    protected static final String RESOURCE_ELEMENT_NAME = "resource";
    protected static final String SUBCOLLECTION_ELEMENT_NAME = "subcollection";
    protected static final String DELETED_ELEMENT_NAME = "deleted";
    protected static final String ACE_ELEMENT_NAME = "ace";

    protected final DBBroker broker;
    @Nullable private final Txn transaction;
    private final BackupDescriptor descriptor;
    private final RestoreListener listener;

    @Nullable private final Set<String> pathsToIgnore;

    //handler state
    private int version = 0;
    private boolean deduplicateBlobs = false;
    @Nullable private XmldbURI currentCollectionUri = null;
    private final Deque<DeferredPermission> deferredPermissions = new ArrayDeque<>();

    /**
     * @param broker the database broker
     * @param transaction the transaction to use for the entire restore,
     *                    or null if restoring each collection/resource
     *                    should occur in its own transaction
     * @param descriptor the backup descriptor to start restoring from
     * @param listener the listener to report restore events to
     * @param pathsToIgnore database paths to ignore in the backup
     */
    protected AbstractRestoreHandler(final DBBroker broker, @Nullable final Txn transaction,
            final BackupDescriptor descriptor, final RestoreListener listener,
            @Nullable final Set<String> pathsToIgnore) {
        this.broker = broker;
        this.transaction = transaction;
        this.listener = listener;
        this.descriptor = descriptor;
        this.pathsToIgnore = pathsToIgnore;
    }

    /**
     * Either reuses the provided transaction
     * in a safe manner or starts a new transaction.
     *
     * @return the provided transaction or a new transaction
     */
    protected Txn beginTransaction() {
        if (transaction == null) {
            return broker.continueOrBeginTransaction();
        }
        return new Txn.ReusableTxn(transaction);
    }

    @Override
    public void startDocument() throws SAXException {
        listener.processingDescriptor(descriptor.getSymbolicPath());
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qName, final Attributes atts) throws SAXException {

        // only process entries in the eXist-db namespace
        if (namespaceURI != null && !namespaceURI.equals(Namespaces.EXIST_NS)) {
            return;
        }

        if (COLLECTION_ELEMENT_NAME.equals(localName) || RESOURCE_ELEMENT_NAME.equals(localName)) {

            final DeferredPermission df;
            if (COLLECTION_ELEMENT_NAME.equals(localName)) {
                df = restoreCollectionEntry(atts);
            } else {
                df = restoreResourceEntry(atts);
            }
            deferredPermissions.push(df);

        } else if (SUBCOLLECTION_ELEMENT_NAME.equals(localName)) {
            restoreSubCollectionEntry(atts);

        } else if (DELETED_ELEMENT_NAME.equals(localName)) {
            restoreDeletedEntry(atts);

        } else if (ACE_ELEMENT_NAME.equals(localName)) {
            addACEToDeferredPermissions(atts);
        }
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {
        if (Namespaces.EXIST_NS.equals(namespaceURI) &&
                (COLLECTION_ELEMENT_NAME.equals(localName)
                        || RESOURCE_ELEMENT_NAME.equals(localName))) {
            setDeferredPermissions();
        }
    }

    private DeferredPermission restoreCollectionEntry(final Attributes attributes) throws SAXException {
        final EntryCommonMetadataAttributes commonAttributes = EntryCommonMetadataAttributes.fromAttributes(attributes);

        // Don't process entries which should be skipped
        if (commonAttributes.skip) {
            return new SkippedEntryDeferredPermission();
        }

        if (commonAttributes.name == null) {
            throw new SAXException("Collection requires a name attribute");
        }

        final String strVersion = attributes.getValue("version");

        if (strVersion != null) {
            try {
                this.version = Integer.parseInt(strVersion);
            } catch(final NumberFormatException nfe) {
                final String msg = "Could not parse version number for Collection '" + commonAttributes.name + "', defaulting to version 0";
                listener.warn(msg);
                LOG.warn(msg);

                this.version = 0;
            }
        }

        try {
            listener.createdCollection(commonAttributes.name);
            @Nullable final XmldbURI collUri = uriFromPath(commonAttributes.name);
            if (collUri == null) {
                return new SkippedEntryDeferredPermission();
            }

            if (version >= BLOB_STORE_VERSION) {
                this.deduplicateBlobs = Boolean.parseBoolean(attributes.getValue("deduplicate-blobs"));
            } else {
                this.deduplicateBlobs = false;
            }

            final LockManager lockManager = broker.getBrokerPool().getLockManager();
            try (final Txn transaction = beginTransaction();
                 final ManagedCollectionLock colLock = lockManager.acquireCollectionWriteLock(collUri)) {
                Collection collection = broker.getCollection(collUri);
                if (collection == null) {
                    final Tuple2<Permission, Long> creationAttributes = Tuple(null, getDateFromXSDateTimeStringForItem(commonAttributes.created, commonAttributes.name).getTime());
                    collection = broker.getOrCreateCollection(transaction, collUri, Optional.of(creationAttributes));
                    broker.saveCollection(transaction, collection);
                }

                transaction.commit();

                this.currentCollectionUri = collection.getURI();
            }

            notifyStartCollectionRestore(currentCollectionUri, attributes);

            final DeferredPermission deferredPermission;
            if (commonAttributes.name.startsWith(XmldbURI.SYSTEM_COLLECTION)) {
                //prevents restore of a backup from changing System collection ownership
                deferredPermission = new CollectionDeferredPermission(listener, currentCollectionUri, SecurityManager.SYSTEM, SecurityManager.DBA_GROUP, Integer.parseInt(commonAttributes.mode, 8));
            } else {
                deferredPermission = new CollectionDeferredPermission(listener, currentCollectionUri, commonAttributes.owner, commonAttributes.group, Integer.parseInt(commonAttributes.mode, 8));
            }

            notifyEndCollectionRestore(currentCollectionUri);

            return deferredPermission;

        } catch (final IOException | LockException | TransactionException | PermissionDeniedException e) {
            final String msg = "An unrecoverable error occurred while restoring collection '" + commonAttributes.name + "': "  + e.getMessage() + ". Aborting restore!";
            LOG.error(msg, e);
            listener.warn(msg);
            throw new SAXException(msg, e);
        }
    }

    private void restoreSubCollectionEntry(final Attributes atts) throws SAXException {
        final String name;
        if (atts.getValue("filename") != null) {
            name = atts.getValue("filename");
        } else {
            name = atts.getValue("name");
        }

        // exclude the /db/system, /db/system/security, /db/system/security/exist/groups collections and their sub-collections, as these have already been restored
        if ((XmldbURI.DB.equals(currentCollectionUri) && "system".equals(name))
                || (XmldbURI.SYSTEM.equals(currentCollectionUri) && "security".equals(name))
                || (XmldbURI.SYSTEM.append("security").append(RealmImpl.ID).equals(currentCollectionUri) && "groups".equals(name))) {
            return;
        }

        //parse the sub-collection descriptor and restore
        final BackupDescriptor subDescriptor = descriptor.getChildBackupDescriptor(name);
        if (subDescriptor != null) {
            if (pathsToIgnore != null && pathsToIgnore.contains(subDescriptor.getSymbolicPath())) {
                listener.skipResources("Skipping app path " + subDescriptor.getSymbolicPath() + ". Newer version " +
                        "is already installed.", subDescriptor.getNumberOfFiles());
                return;
            }
            final XMLReaderPool parserPool = broker.getBrokerPool().getXmlReaderPool();
            XMLReader reader = null;
            try {
                reader = parserPool.borrowXMLReader();

                final EXistInputSource is = subDescriptor.getInputSource();
                is.setEncoding(UTF_8.displayName());

                final AbstractRestoreHandler handler = newSelf(broker, transaction, subDescriptor, listener, pathsToIgnore);

                reader.setContentHandler(handler);
                reader.parse(is);
            } catch(final SAXParseException se) {
                listener.error("SAX exception while reading sub-collection " + subDescriptor.getSymbolicPath() + " for processing: " + se.getMessage());
            } catch(final IOException ioe) {
                listener.error("Could not read sub-collection for processing: " + ioe.getMessage());
            } finally {
                if (reader != null) {
                    parserPool.returnXMLReader(reader);
                }
            }
        } else {
            listener.error("Collection " + descriptor.getSymbolicPath(name, false) + " does not exist or is not readable.");
        }
    }

    private DeferredPermission restoreResourceEntry(final Attributes attributes) throws SAXException {
        final EntryCommonMetadataAttributes commonAttributes = EntryCommonMetadataAttributes.fromAttributes(attributes);

        // Don't process entries which should be skipped
        if (commonAttributes.skip) {
            return new SkippedEntryDeferredPermission();
        }

        if (commonAttributes.name == null) {
            throw new SAXException("Resource requires a name attribute");
        }

        final boolean xmlType = Optional.ofNullable(attributes.getValue("type")).filter(s -> "XMLResource".equals(s)).isPresent();
        final String filename = getAttr(attributes, "filename", commonAttributes.name);
        @Nullable final String mimeTypeStr = attributes.getValue("mimetype");
        @Nullable final String dateModifiedStr = attributes.getValue("modified");
        @Nullable final String publicId = attributes.getValue("publicid");
        @Nullable final String systemId = attributes.getValue("systemid");
        @Nullable final String nameDocType = attributes.getValue("namedoctype");

        @Nullable final XmldbURI docName = uriFromPath(commonAttributes.name);
        if (docName == null) {
            return new SkippedEntryDeferredPermission();
        }

        final EXistInputSource is;
        if (deduplicateBlobs && !xmlType) {
            final String blobId = attributes.getValue("blob-id");
            is = descriptor.getBlobInputSource(blobId);

            if (is == null) {
                final String msg = String.format("Failed to restore resource '%s'%nfrom BLOB '%s'.%nReason: Unable to obtain its EXistInputSource", commonAttributes.name, blobId);
                listener.warn(msg);
                return new SkippedEntryDeferredPermission();
            }
        } else {
            is = descriptor.getInputSource(filename);

            if (is == null) {
                final String msg = String.format("Failed to restore resource '%s'%nfrom file '%s'.%nReason: Unable to obtain its EXistInputSource", commonAttributes.name, descriptor.getSymbolicPath(commonAttributes.name, false));
                listener.warn(msg);
                return new SkippedEntryDeferredPermission();
            }
        }

        final MimeType mimeType;
        if (mimeTypeStr == null || mimeTypeStr.trim().isEmpty()) {
            mimeType = xmlType ? MimeType.XML_TYPE : MimeType.BINARY_TYPE;
            listener.warn("Missing mimetype attribute in the backup __contents__.xml file for: " + commonAttributes.name + ", assuming: " + mimeType);
        } else {
            mimeType = new MimeType(mimeTypeStr.trim(), xmlType ? MimeType.XML : MimeType.BINARY);
        }

        Date dateCreated = null;
        if (commonAttributes.created != null) {
            try {
                dateCreated = new DateTimeValue(commonAttributes.created).getDate();
            } catch (final XPathException xpe) {
                listener.warn("Illegal creation date. Ignoring date...");
            }
        }

        Date dateModified = null;
        if (dateModifiedStr != null) {
            try {
                dateModified = new DateTimeValue(dateModifiedStr).getDate();
            } catch (final XPathException xpe) {
                listener.warn("Illegal modification date. Ignoring date...");
            }
        }

        final DocumentType docType;
        if (publicId != null || systemId != null) {
            docType = new DocumentTypeImpl(null, nameDocType, publicId, systemId);
        } else {
            docType = null;
        }

        final XmldbURI docUri = currentCollectionUri.append(docName);
        try {
            try (final Txn transaction = beginTransaction()) {

                boolean validated = false;
                try {
                    try (final Collection collection = broker.openCollection(currentCollectionUri, Lock.LockMode.WRITE_LOCK);
                         final ManagedDocumentLock docLock = broker.getBrokerPool().getLockManager().acquireDocumentWriteLock(docUri)) {

                        broker.storeDocument(transaction, docName, is, mimeType, dateCreated, dateModified, null, docType, null, collection);
                        validated = true;

                        notifyStartDocumentRestore(docUri, attributes);

                        transaction.commit();

                        // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                        collection.close();
                    }
                } finally {
                    /*
                        This allows us to commit the transaction (so the restore doesn't stop)
                        and still throw an exception to skip over resources that didn't
                        validate. This preserves eXist-db's previous behaviour
                        of "best effort attempt" when restoring a backup,
                        rather than an ACID "all or nothing" approach.
                     */
                    if (!validated) {
                        // because `validated == false` we know that there have only been reads on the transaction/sub-transaction!
                        transaction.commit();
                    }
                }
            }

            final DeferredPermission deferredPermission;
            if(commonAttributes.name.startsWith(XmldbURI.SYSTEM_COLLECTION)) {
                //prevents restore of a backup from changing system collection resource ownership
                deferredPermission = new ResourceDeferredPermission(listener, docUri, SecurityManager.SYSTEM, SecurityManager.DBA_GROUP, Integer.parseInt(commonAttributes.mode, 8));
            } else {
                deferredPermission = new ResourceDeferredPermission(listener, docUri, commonAttributes.owner, commonAttributes.group, Integer.parseInt(commonAttributes.mode, 8));
            }

            notifyEndDocumentRestore(docUri);

            listener.restoredResource(commonAttributes.name);

            return deferredPermission;

        } catch(final Exception e) {
            final String message = String.format("Failed to restore resource '%s'%nfrom file '%s'.%nReason: %s", commonAttributes.name, descriptor.getSymbolicPath(commonAttributes.name, false), e.getMessage());
            listener.warn(message);
            LOG.error(message, e);
            return new SkippedEntryDeferredPermission();
        } finally {
            is.close();
        }
    }

    private void restoreDeletedEntry(final Attributes atts) {
        final String name = atts.getValue("name");
        final String type = atts.getValue("type");

        if (COLLECTION_ELEMENT_NAME.equals(type)) {
            try {
                try (final Txn transaction = beginTransaction();
                     final Collection collection = broker.openCollection(currentCollectionUri.append(name), Lock.LockMode.WRITE_LOCK)) {
                    if (collection != null) {
                        final boolean triggersEnabled = broker.isTriggersEnabled();
                        try {
                            broker.setTriggersEnabled(false);
                            broker.removeCollection(transaction, collection);
                        } finally {
                            // restore triggers enabled setting
                            broker.setTriggersEnabled(triggersEnabled);
                        }
                    }

                    transaction.commit();
                }
            } catch (final PermissionDeniedException | IOException | TriggerException | TransactionException e) {
                listener.warn("Failed to remove deleted collection: " + name + ": " + e.getMessage());
            }

        } else if (RESOURCE_ELEMENT_NAME.equals(type)) {
            final XmldbURI docName = XmldbURI.create(name);
            try (final Txn transaction = beginTransaction();
                 final Collection collection = broker.openCollection(currentCollectionUri.append(name), Lock.LockMode.WRITE_LOCK);
                 final LockedDocument lockedDocument = collection.getDocumentWithLock(broker, docName, Lock.LockMode.WRITE_LOCK)) {

                //Check that the document exists
                if (lockedDocument != null) {
                    final boolean triggersEnabled = broker.isTriggersEnabled();
                    try {
                        broker.setTriggersEnabled(false);
                        final boolean xmlType = !(lockedDocument.getDocument() instanceof BinaryDocument);
                        if (xmlType) {
                            collection.removeXMLResource(transaction, broker, docName);
                        } else {
                            collection.removeBinaryResource(transaction, broker, docName);
                        }
                    } finally {
                        // restore triggers enabled setting
                        broker.setTriggersEnabled(triggersEnabled);
                    }
                }

                transaction.commit();

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                collection.close();

            } catch (final PermissionDeniedException | TransactionException | TriggerException | LockException | IOException e) {
                listener.warn("Failed to remove deleted resource: " + name + ": " + e.getMessage());
            }
        }
    }

    private @Nullable XmldbURI uriFromPath(final String path) {
        final XmldbURI uri;
        if (version >= STRICT_URI_VERSION) {
            uri = XmldbURI.create(path);
        } else {
            try {
                uri = URIUtils.encodeXmldbUriFor(path);
            } catch(final URISyntaxException e) {
                final String msg = "Could not parse name into a URI: " + e.getMessage();
                listener.warn(msg);
                LOG.warn(msg, e);
                return null;
            }
        }
        return uri;
    }

    private void addACEToDeferredPermissions(final Attributes atts) {
        final int index = Integer.parseInt(atts.getValue("index"));
        final ACLPermission.ACE_TARGET target = ACLPermission.ACE_TARGET.valueOf(atts.getValue("target"));
        final String who = atts.getValue("who");
        final ACLPermission.ACE_ACCESS_TYPE accessType = ACLPermission.ACE_ACCESS_TYPE.valueOf(atts.getValue("access_type"));
        final int mode = Integer.parseInt(atts.getValue("mode"), 8);
        deferredPermissions.peek().addACE(index, target, who, accessType, mode);
    }

    private void setDeferredPermissions() {
        final DeferredPermission deferredPermission = deferredPermissions.pop();
        try (final Txn transaction = beginTransaction()) {
            deferredPermission.apply(broker, transaction);

            transaction.commit();
        } catch (final TransactionException e) {
            final String msg = "ERROR: Failed to set permissions on: '" + deferredPermission.getTarget() + "'.";
            LOG.error(msg, e);
            listener.warn(msg);
        }
    }

    private Date getDateFromXSDateTimeStringForItem(final String strXSDateTime, final String itemName) {
        Date dateCreated = null;

        if (strXSDateTime != null) {
            try {
                dateCreated = new DateTimeValue(strXSDateTime).getDate();
            } catch(final XPathException e2) {
                // no-op, ignore and move on
            }
        }

        if (dateCreated == null) {
            final String msg = "Could not parse created date '" + strXSDateTime + "' from backup for: '" + itemName + "', using current time!";
            listener.error(msg);
            LOG.error(msg);

            dateCreated = Calendar.getInstance().getTime();
        }

        return dateCreated;
    }

    private static String getAttr(final Attributes atts, final String name, final String fallback) {
        final String value = atts.getValue(name);
        if (value == null) {
            return fallback;
        }
        return value;
    }

    /**
     * Returns a new restore handler.
     *
     * @param broker the database broker
     * @param transaction the transaction to use for the entire restore,
     *                    or null if restoring each collection/resource
     *                    should occur in its own transaction
     * @param descriptor the backup descriptor to start restoring from
     * @param listener the listener to report restore events to
     * @param pathsToIgnore database paths to ignore in the backup
     * @return a new restore handler
     */
    protected abstract AbstractRestoreHandler newSelf(final DBBroker broker, @Nullable final Txn transaction,
            final BackupDescriptor descriptor, final RestoreListener listener,
            @Nullable final Set<String> pathsToIgnore);

    /**
     * Receives notification at the start of a Collection being restored.
     *
     * @param collectionUri The URI of the collection being restored.
     * @param attributes the attributes of the collection element from the backup descriptor.
     * @throws PermissionDeniedException if permission is denied
     */
    protected void notifyStartCollectionRestore(final XmldbURI collectionUri, final Attributes attributes) throws PermissionDeniedException {
        // no-op by default, may be overridden by subclass
    }

    /**
     * Receives notification at the end of a Collection being restored.
     *
     * @param collectionUri The URI of the collection being restored.
     * @throws PermissionDeniedException if permission is denied
     */
    protected void notifyEndCollectionRestore(final XmldbURI collectionUri) throws PermissionDeniedException {
        // no-op by default, may be overridden by subclass
    }

    /**
     * Receives notification at the start of a Document being restored.
     *
     * @param documentUri The URI of the document being restored.
     * @param attributes the attributes of the collection element from the backup descriptor.
     * @throws PermissionDeniedException if permission is denied
     */
    protected void notifyStartDocumentRestore(final XmldbURI documentUri, final Attributes attributes) throws PermissionDeniedException {
        // no-op by default, may be overridden by subclass
    }

    /**
     * Receives notification at the end of a Document being restored.
     *
     * @param documentUri The URI of the document being restored.
     * @throws PermissionDeniedException if permission is denied
     */
    protected void notifyEndDocumentRestore(final XmldbURI documentUri) throws PermissionDeniedException {
        // no-op by default, may be overridden by subclass
    }

    private static class EntryCommonMetadataAttributes {
        final boolean skip;
        @Nullable final String name;
        final String owner;
        final String group;
        final String mode;
        @Nullable final String created;

        private EntryCommonMetadataAttributes(final boolean skip, @Nullable final String name, final String owner, final String group, final String mode, @Nullable final String created) {
            this.skip = skip;
            this.name = name;
            this.owner = owner;
            this.group = group;
            this.mode = mode;
            this.created = created;
        }

        public static EntryCommonMetadataAttributes fromAttributes(final Attributes attributes) {
            @Nullable final String skipStr = attributes.getValue("skip");
            final boolean skip = skipStr != null && ("true".equalsIgnoreCase(skipStr) || "yes".equalsIgnoreCase(skipStr));
            @Nullable final String name = attributes.getValue("name");
            final String owner = getAttr(attributes, "owner", SecurityManager.SYSTEM);
            final String group = getAttr(attributes, "group", SecurityManager.DBA_GROUP);
            final String mode = getAttr(attributes, "mode", "644");
            @Nullable final String created = attributes.getValue("created");

            return new EntryCommonMetadataAttributes(skip, name, owner, group, mode, created);
        }
    }
}
