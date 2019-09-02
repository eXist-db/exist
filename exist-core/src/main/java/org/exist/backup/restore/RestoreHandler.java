/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.backup.restore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.backup.BackupDescriptor;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentTypeImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.ACLPermission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.util.EXistInputSource;
import org.exist.util.LockException;
import org.exist.util.XMLReaderPool;
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
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

// TODO(AR) consider merging with org.exist.backup.restore.SystemImportHandler

/**
 * SAX Content Handler that can act upon
 * and process Backup Descriptors to
 * restore the contents of the backup
 * into the database.
 */
public class RestoreHandler extends DefaultHandler {

    private final static Logger LOG = LogManager.getLogger(RestoreHandler.class);

    private static final int STRICT_URI_VERSION = 1;
    private static final int BLOB_STORE_VERSION = 2;

    private final DBBroker broker;
    @Nullable private final Txn transaction;
    private final BackupDescriptor descriptor;
    private final RestoreListener listener;

    //handler state
    private int version = 0;
    private boolean deduplicateBlobs = false;
    private XmldbURI currentCollectionUri = null;
    private final Deque<DeferredPermission> deferredPermissions = new ArrayDeque<>();
    private final Set<String> pathsToIgnore;

    /**
     * @param broker the database broker
     * @param transaction the transaction to use for the entire restore,
     *                    or null if restoring each collection/resource
     *                    should occur in its own transaction
     * @param descriptor the backup descriptor to start restoring from
     * @param listener the listener to report restore events to
     * @param pathsToIgnore database paths to ignore in the backup
     */
    public RestoreHandler(final DBBroker broker, @Nullable final Txn transaction, final BackupDescriptor descriptor,
            final RestoreListener listener, final Set<String> pathsToIgnore) {
        this.broker = broker;
        this.transaction = transaction;
        this.descriptor = descriptor;
        this.listener = listener;
        this.pathsToIgnore = pathsToIgnore;
    }

    /**
     * Either reuses the provided transaction
     * in a safe manner or starts a new transaction.
     */
    private Txn beginTransaction() {
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

        //only process entries in the exist namespace
        if (namespaceURI != null && !namespaceURI.equals(Namespaces.EXIST_NS)) {
            return;
        }

        if ("collection".equals(localName) || "resource".equals(localName)) {

            final DeferredPermission df;
            if ("collection".equals(localName)) {
                df = restoreCollectionEntry(atts);
            } else {
                df = restoreResourceEntry(atts);
            }
            deferredPermissions.push(df);

        } else if ("subcollection".equals(localName)) {
            restoreSubCollectionEntry(atts);

        } else if ("deleted".equals(localName)) {
            restoreDeletedEntry(atts);

        } else if ("ace".equals(localName)) {
            addACEToDeferredPermissions(atts);
        }
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {
        if (namespaceURI.equals(Namespaces.EXIST_NS) && ("collection".equals(localName) || "resource".equals(localName))) {
            setDeferredPermissions();
        }
        super.endElement(namespaceURI, localName, qName);
    }

    private DeferredPermission restoreCollectionEntry(final Attributes atts) throws SAXException {
        final String name = atts.getValue("name");

        if(name == null) {
            throw new SAXException("Collection requires a name attribute");
        }

        final String owner = getAttr(atts, "owner", SecurityManager.SYSTEM);
        final String group = getAttr(atts, "group", SecurityManager.DBA_GROUP);
        final String mode = getAttr(atts, "mode", "644");
        final String created = atts.getValue("created");
        final String strVersion = atts.getValue("version");

        if(strVersion != null) {
            try {
                this.version = Integer.parseInt(strVersion);
            } catch(final NumberFormatException nfe) {
                final String msg = "Could not parse version number for Collection '" + name + "', defaulting to version 0";
                listener.warn(msg);
                LOG.warn(msg);

                this.version = 0;
            }
        }

        try {
            listener.createdCollection(name);
            final XmldbURI collUri;

            if(version >= STRICT_URI_VERSION) {
                collUri = XmldbURI.create(name);
            } else {
                try {
                    collUri = URIUtils.encodeXmldbUriFor(name);
                } catch(final URISyntaxException e) {
                    listener.warn("Could not parse document name into a URI: " + e.getMessage());
                    return new SkippedEntryDeferredPermission();
                }
            }

            if (version >= BLOB_STORE_VERSION) {
                this.deduplicateBlobs = Boolean.valueOf(atts.getValue("deduplicate-blobs"));
            } else {
                this.deduplicateBlobs = false;
            }

            final LockManager lockManager = broker.getBrokerPool().getLockManager();
            try (final Txn transaction = beginTransaction();
                    final ManagedCollectionLock colLock = lockManager.acquireCollectionWriteLock(collUri)) {
                Collection collection = broker.getCollection(collUri);
                if (collection == null) {
                    collection = broker.getOrCreateCollection(transaction, collUri);
                    collection.setCreationTime(getDateFromXSDateTimeStringForItem(created, name).getTime());
                    broker.saveCollection(transaction, collection);
                }

                transaction.commit();

                this.currentCollectionUri = collection.getURI();
            }

            final DeferredPermission deferredPermission;
            if(name.startsWith(XmldbURI.SYSTEM_COLLECTION)) {
                //prevents restore of a backup from changing System collection ownership
                deferredPermission = new CollectionDeferredPermission(listener, currentCollectionUri, SecurityManager.SYSTEM, SecurityManager.DBA_GROUP, Integer.parseInt(mode, 8));
            } else {
                deferredPermission = new CollectionDeferredPermission(listener, currentCollectionUri, owner, group, Integer.parseInt(mode, 8));
            }
            return deferredPermission;

        } catch(final IOException | LockException | TransactionException | PermissionDeniedException e) {
            final String msg = "An unrecoverable error occurred while restoring collection '" + name + "': "  + e.getMessage() + ". Aborting restore!";
            LOG.error(msg, e);
            listener.warn(msg);
            throw new SAXException(msg, e);
        }
    }

    private DeferredPermission restoreResourceEntry(final Attributes atts) throws SAXException {
        final String skip = atts.getValue( "skip" );

        // Don't process entries which should be skipped
        if(skip != null && !"no".equals(skip)) {
            return new SkippedEntryDeferredPermission();
        }

        final String name = atts.getValue("name");
        if(name == null) {
            throw new SAXException("Resource requires a name attribute");
        }

        final boolean xmlType = Optional.ofNullable(atts.getValue("type")).filter(s -> s.equals("XMLResource")).isPresent();

        final String owner = getAttr(atts, "owner", SecurityManager.SYSTEM);
        final String group = getAttr(atts, "group", SecurityManager.DBA_GROUP);
        final String perms = getAttr(atts, "mode", "644");

        final String filename = getAttr(atts, "filename", name);

        final String mimeType = atts.getValue("mimetype");
        final String created = atts.getValue("created");
        final String modified = atts.getValue("modified");

        final String publicId = atts.getValue("publicid");
        final String systemId = atts.getValue("systemid");
        final String nameDocType = atts.getValue("namedoctype");

        final XmldbURI docName;
        if (version >= STRICT_URI_VERSION) {
            docName = XmldbURI.create(name);
        } else {
            try {
                docName = URIUtils.encodeXmldbUriFor(name);
            } catch(final URISyntaxException e) {
                final String msg = "Could not parse document name into a URI: " + e.getMessage();
                listener.error(msg);
                LOG.error(msg, e);
                return new SkippedEntryDeferredPermission();
            }
        }

        final EXistInputSource is;
        if (deduplicateBlobs && !xmlType) {
            final String blobId = atts.getValue("blob-id");
            is = descriptor.getBlobInputSource(blobId);

            if (is == null) {
                final String msg = "Failed to restore resource '" + name + "'\nfrom BLOB '" + blobId + "'.\nReason: Unable to obtain its EXistInputSource";
                listener.warn(msg);
                return new SkippedEntryDeferredPermission();
            }
        } else {
            is = descriptor.getInputSource(filename);

            if (is == null) {
                final String msg = "Failed to restore resource '" + name + "'\nfrom file '" + descriptor.getSymbolicPath( name, false ) + "'.\nReason: Unable to obtain its EXistInputSource";
                listener.warn(msg);
                return new SkippedEntryDeferredPermission();
            }
        }

        Date dateCreated = null;
        Date dateModified = null;
        if (created != null) {
            try {
                dateCreated = (new DateTimeValue(created)).getDate();
            } catch(final XPathException xpe) {
                listener.warn("Illegal creation date. Ignoring date...");
            }
        }
        if (modified != null) {
            try {
                dateModified = (new DateTimeValue(modified)).getDate();
            } catch(final XPathException xpe) {
                listener.warn("Illegal modification date. Ignoring date...");
            }
        }

        final XmldbURI docUri = currentCollectionUri.append(docName);
        try {
            try (final Txn transaction = beginTransaction()) {

                boolean validated = false;
                try {
                    try (final Collection collection = broker.openCollection(currentCollectionUri, Lock.LockMode.WRITE_LOCK);
                         final ManagedDocumentLock docLock = broker.getBrokerPool().getLockManager().acquireDocumentWriteLock(docUri)) {

                        if (xmlType) {
                            final IndexInfo info = collection.validateXMLResource(transaction, broker, docName, is);
                            validated = true;

                            info.getDocument().getMetadata().setMimeType(mimeType);
                            if (dateCreated != null) {
                                info.getDocument().getMetadata().setCreated(dateCreated.getTime());
                            }
                            if (dateModified != null) {
                                info.getDocument().getMetadata().setLastModified(dateModified.getTime());
                            }
                            if (publicId != null || systemId != null) {
                                final DocumentType docType = new DocumentTypeImpl(nameDocType, publicId, systemId);
                                info.getDocument().getMetadata().setDocType(docType);
                            }
                            collection.store(transaction, broker, info, is);

                        } else {
                            try (final InputStream stream = is.getByteStream()) {
                                collection.addBinaryResource(transaction, broker, docName, stream, mimeType, -1, dateCreated, dateModified);
                            }
                        }

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
            if(name.startsWith(XmldbURI.SYSTEM_COLLECTION)) {
                //prevents restore of a backup from changing system collection resource ownership
                deferredPermission = new ResourceDeferredPermission(listener, docUri, SecurityManager.SYSTEM, SecurityManager.DBA_GROUP, Integer.parseInt(perms, 8));
            } else {
                deferredPermission = new ResourceDeferredPermission(listener, docUri, owner, group, Integer.parseInt(perms, 8));
            }

            listener.restoredResource(name);

            return deferredPermission;

        } catch(final Exception e) {
            listener.warn(String.format("Failed to restore resource '%s'\nfrom file '%s'.\nReason: %s", name, descriptor.getSymbolicPath(name, false), e.getMessage()));
            LOG.error(e.getMessage(), e);
            return new SkippedEntryDeferredPermission();
        } finally {
            is.close();
        }
    }

    private void restoreSubCollectionEntry(final Attributes atts) throws SAXException {
        final String name;
        if(atts.getValue("filename") != null) {
            name = atts.getValue("filename");
        } else {
            name = atts.getValue("name");
        }

        // exclude /db/system collection and sub-collections, as these have already been restored
        if ((XmldbURI.DB.equals(currentCollectionUri) && "system".equals(name))
                || (XmldbURI.SYSTEM.equals(currentCollectionUri) && "security".equals(name))) {
            return;
        }

        //parse the sub-collection descriptor and restore
        final BackupDescriptor subDescriptor = descriptor.getChildBackupDescriptor(name);
        if(subDescriptor != null) {
            if (pathsToIgnore.contains(subDescriptor.getSymbolicPath())) {
                listener.info("Skipping app path " + subDescriptor.getSymbolicPath() + ". Newer version " +
                        "is already installed.");
                return;
            }
            final XMLReaderPool parserPool = broker.getBrokerPool().getParserPool();
            XMLReader reader = null;
            try {
                reader = parserPool.borrowXMLReader();

                final EXistInputSource is = subDescriptor.getInputSource();
                is.setEncoding(UTF_8.displayName());

                final RestoreHandler handler = new RestoreHandler(broker, transaction, subDescriptor, listener, pathsToIgnore);

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

    private void restoreDeletedEntry(final Attributes atts) {
        final String name = atts.getValue("name");
        final String type = atts.getValue("type");

        if("collection".equals(type)) {
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
            } catch(final PermissionDeniedException | IOException | TriggerException | TransactionException e) {
                listener.warn("Failed to remove deleted collection: " + name + ": " + e.getMessage());
            }

        } else if("resource".equals(type)) {
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

            } catch(final PermissionDeniedException | TransactionException | TriggerException | LockException | IOException e) {
                listener.warn("Failed to remove deleted resource: " + name + ": " + e.getMessage());
            }
        }
    }

    private void addACEToDeferredPermissions(final Attributes atts) {
        final int index = Integer.parseInt(atts.getValue("index"));
        final ACLPermission.ACE_TARGET target = ACLPermission.ACE_TARGET.valueOf(atts.getValue("target"));
        final String who = atts.getValue("who");
        final ACLPermission.ACE_ACCESS_TYPE access_type = ACLPermission.ACE_ACCESS_TYPE.valueOf(atts.getValue("access_type"));
        final int mode = Integer.parseInt(atts.getValue("mode"), 8);
        deferredPermissions.peek().addACE(index, target, who, access_type, mode);
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
        Date date_created = null;

        if(strXSDateTime != null) {
            try {
                date_created = new DateTimeValue(strXSDateTime).getDate();
            } catch(final XPathException e2) {
            }
        }

        if(date_created == null) {
            final String msg = "Could not parse created date '" + strXSDateTime + "' from backup for: '" + itemName + "', using current time!";
            listener.error(msg);
            LOG.error(msg);

            date_created = Calendar.getInstance().getTime();
        }

        return date_created;
    }

    private String getAttr(final Attributes atts, final String name, final String fallback) {
        final String value = atts.getValue(name);
        if(value == null) {
            return fallback;
        }
        return value;
    }
}
