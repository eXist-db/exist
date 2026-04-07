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
package org.exist.webdav;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent lock storage using /db/system/webdav-locks/.
 *
 * <p>Locks are stored as XML documents keyed by the URI path of the locked
 * resource. This decouples lock state from document instances, so locks
 * survive document replacement (PUT), server restarts, and collection moves.</p>
 *
 * <p>Lock document format:</p>
 * <pre>
 * &lt;lock xmlns="http://exist-db.org/webdav/locks"&gt;
 *     &lt;token&gt;opaquelocktoken:uuid&lt;/token&gt;
 *     &lt;scope&gt;exclusive&lt;/scope&gt;
 *     &lt;type&gt;write&lt;/type&gt;
 *     &lt;owner&gt;admin&lt;/owner&gt;
 *     &lt;depth&gt;0&lt;/depth&gt;
 *     &lt;timeout&gt;Second-3600&lt;/timeout&gt;
 *     &lt;created&gt;2026-03-29T12:00:00Z&lt;/created&gt;
 *     &lt;resource&gt;/db/test/document.xml&lt;/resource&gt;
 * &lt;/lock&gt;
 * </pre>
 */
public class WebDavLockStore {

    private static final Logger LOG = LogManager.getLogger(WebDavLockStore.class);

    private static final String LOCKS_COLLECTION = "/db/system/webdav-locks";
    private static final String LOCK_NS = "http://exist-db.org/webdav/locks";

    private final BrokerPool brokerPool;

    public WebDavLockStore(final BrokerPool brokerPool) {
        this.brokerPool = brokerPool;
    }

    /**
     * Store a lock for a resource URI.
     *
     * @return the generated opaque lock token
     */
    public String storeLock(final String resourceUri, final String owner,
                            final String scope, final String type,
                            final boolean deep, final long timeout) throws EXistException {

        final String token = UUID.randomUUID().toString();
        final String lockXml = buildLockXml(token, resourceUri, owner, scope, type, deep, timeout);
        final String lockDocName = uriToLockDocName(resourceUri);

        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final TransactionManager txnManager = brokerPool.getTransactionManager();
            try (final Txn txn = txnManager.beginTransaction()) {
                final Collection locksCollection = getOrCreateLocksCollection(broker, txn);
                broker.storeDocument(txn, XmldbURI.create(lockDocName),
                        new StringInputSource(lockXml), MimeType.XML_TYPE, locksCollection);
                txnManager.commit(txn);
            }
        } catch (final PermissionDeniedException e) {
            throw new EXistException("Permission denied storing WebDAV lock: " + e.getMessage(), e);
        } catch (final Exception e) {
            throw new EXistException("Failed to store WebDAV lock: " + e.getMessage(), e);
        }

        LOG.debug("Stored lock for {} with token {}", resourceUri, token);
        return token;
    }

    /**
     * Get the lock for a resource URI, or null if not locked.
     */
    public LockInfo getLock(final String resourceUri) {
        final String lockDocName = uriToLockDocName(resourceUri);
        final XmldbURI lockDocUri = XmldbURI.create(LOCKS_COLLECTION + "/" + lockDocName);

        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final LockedDocument lockedDoc = broker.getXMLResource(lockDocUri, LockMode.READ_LOCK)) {

            if (lockedDoc == null || lockedDoc.getDocument() == null) {
                return null;
            }

            return parseLockDocument(lockedDoc.getDocument());

        } catch (final EXistException | PermissionDeniedException e) {
            LOG.error("Error reading lock for {}: {}", resourceUri, e.getMessage());
            return null;
        }
    }

    /**
     * Remove the lock for a resource URI.
     */
    public void removeLock(final String resourceUri) throws EXistException {
        final String lockDocName = uriToLockDocName(resourceUri);
        final XmldbURI lockDocUri = XmldbURI.create(lockDocName);

        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()))) {
            final TransactionManager txnManager = brokerPool.getTransactionManager();
            try (final Txn txn = txnManager.beginTransaction()) {
                final Collection locksCollection = broker.getCollection(XmldbURI.create(LOCKS_COLLECTION));
                if (locksCollection != null) {
                    locksCollection.removeXMLResource(txn, broker, lockDocUri);
                }
                txnManager.commit(txn);
            }
        } catch (final Exception e) {
            throw new EXistException("Failed to remove WebDAV lock: " + e.getMessage(), e);
        }

        LOG.debug("Removed lock for {}", resourceUri);
    }

    /**
     * Check if a resource has an active lock with the given token.
     */
    public boolean hasLock(final String resourceUri, final String token) {
        final LockInfo lock = getLock(resourceUri);
        if (lock == null) {
            return false;
        }
        return lock.token.equals(token);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private Collection getOrCreateLocksCollection(final DBBroker broker, final Txn txn)
            throws PermissionDeniedException, EXistException, IOException,
            org.exist.collections.triggers.TriggerException {
        Collection collection = broker.getCollection(XmldbURI.create(LOCKS_COLLECTION));
        if (collection == null) {
            collection = broker.getOrCreateCollection(txn, XmldbURI.create(LOCKS_COLLECTION));
            broker.saveCollection(txn, collection);
            LOG.info("Created WebDAV locks collection: {}", LOCKS_COLLECTION);
        }
        return collection;
    }

    /**
     * Convert a resource URI to a lock document name.
     * /db/test/doc.xml → db-test-doc.xml.lock.xml
     */
    static String uriToLockDocName(final String resourceUri) {
        String name = resourceUri;
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        return name.replace('/', '-') + ".lock.xml";
    }

    private String buildLockXml(final String token, final String resourceUri,
                                 final String owner, final String scope,
                                 final String type, final boolean deep,
                                 final long timeout) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<lock xmlns=\"" + LOCK_NS + "\">\n" +
                "    <token>" + esc(token) + "</token>\n" +
                "    <scope>" + esc(scope) + "</scope>\n" +
                "    <type>" + esc(type) + "</type>\n" +
                "    <owner>" + esc(owner) + "</owner>\n" +
                "    <depth>" + (deep ? "infinity" : "0") + "</depth>\n" +
                "    <timeout>" + (timeout > 0 ? "Second-" + timeout : "Infinite") + "</timeout>\n" +
                "    <created>" + Instant.now().toString() + "</created>\n" +
                "    <resource>" + esc(resourceUri) + "</resource>\n" +
                "</lock>\n";
    }

    private LockInfo parseLockDocument(final Document doc) {
        final Element root = doc.getDocumentElement();
        if (root == null) {
            return null;
        }

        final LockInfo info = new LockInfo();
        info.token = getElementText(root, "token");
        info.scope = getElementText(root, "scope");
        info.type = getElementText(root, "type");
        info.owner = getElementText(root, "owner");
        info.resource = getElementText(root, "resource");
        final String depth = getElementText(root, "depth");
        info.deep = "infinity".equals(depth);
        return info;
    }

    private static String getElementText(final Element parent, final String localName) {
        // Try with namespace first, then without (for robustness)
        NodeList nodes = parent.getElementsByTagNameNS(LOCK_NS, localName);
        if (nodes.getLength() == 0) {
            nodes = parent.getElementsByTagName(localName);
        }
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    private static String esc(final String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Lock information record.
     */
    public static class LockInfo {
        public String token;
        public String scope;
        public String type;
        public String owner;
        public String resource;
        public boolean deep;
    }
}
