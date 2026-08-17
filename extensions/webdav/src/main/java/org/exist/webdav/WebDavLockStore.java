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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent lock storage using /db/system/webdav-locks/.
 *
 * <p>Locks are stored as XML documents keyed by the URI path of the locked
 * resource. Each document can contain multiple lock entries to support
 * shared locks. Format:</p>
 * <pre>
 * &lt;locks xmlns="http://exist-db.org/webdav/locks"&gt;
 *     &lt;lock&gt;
 *         &lt;token&gt;uuid&lt;/token&gt;
 *         &lt;scope&gt;shared&lt;/scope&gt;
 *         &lt;type&gt;write&lt;/type&gt;
 *         &lt;owner&gt;alice&lt;/owner&gt;
 *         &lt;depth&gt;0&lt;/depth&gt;
 *         &lt;timeout&gt;Second-3600&lt;/timeout&gt;
 *         &lt;created&gt;2026-03-29T12:00:00Z&lt;/created&gt;
 *     &lt;/lock&gt;
 * &lt;/locks&gt;
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
     * Add a lock with a specific token (used for refresh).
     */
    public void storeLockWithToken(final String token, final String resourceUri,
                                    final String owner, final String scope,
                                    final String type, final boolean deep,
                                    final long timeout) throws EXistException {
        addLockEntry(token, resourceUri, owner, scope, type, deep, timeout);
    }

    /**
     * Add a lock for a resource URI.
     *
     * @return the generated opaque lock token
     */
    public String storeLock(final String resourceUri, final String owner,
                            final String scope, final String type,
                            final boolean deep, final long timeout) throws EXistException {
        final String token = UUID.randomUUID().toString();
        addLockEntry(token, resourceUri, owner, scope, type, deep, timeout);
        return token;
    }

    /**
     * Get the first lock for a resource URI, or null if not locked.
     * For backward compatibility with code that expects a single lock.
     */
    public LockInfo getLock(final String resourceUri) {
        final List<LockInfo> locks = getLocks(resourceUri);
        return locks.isEmpty() ? null : locks.getFirst();
    }

    /**
     * Get all locks for a resource URI.
     */
    public List<LockInfo> getLocks(final String resourceUri) {
        final String lockDocName = uriToLockDocName(resourceUri);
        final XmldbURI lockDocUri = XmldbURI.create(LOCKS_COLLECTION + "/" + lockDocName);

        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final LockedDocument lockedDoc = broker.getXMLResource(lockDocUri, LockMode.READ_LOCK)) {

            if (lockedDoc == null || lockedDoc.getDocument() == null) {
                return List.of();
            }

            return parseLockDocument(lockedDoc.getDocument());

        } catch (final EXistException | PermissionDeniedException e) {
            LOG.error("Error reading locks for {}: {}", resourceUri, e.getMessage());
            return List.of();
        }
    }

    /**
     * Remove all locks for a resource URI.
     */
    public void removeLock(final String resourceUri) throws EXistException {
        removeLockDocument(resourceUri);
    }

    /**
     * Remove a specific lock by token. If no locks remain, the document is deleted.
     */
    public void removeLockByToken(final String resourceUri, final String token) throws EXistException {
        final List<LockInfo> locks = getLocks(resourceUri);
        final List<LockInfo> remaining = new ArrayList<>();
        for (final LockInfo lock : locks) {
            if (!token.equals(lock.token)) {
                remaining.add(lock);
            }
        }

        if (remaining.isEmpty()) {
            removeLockDocument(resourceUri);
        } else {
            // Rewrite the document with remaining locks
            storeLocksDocument(resourceUri, remaining);
        }
    }

    /**
     * Check if a resource has an active lock with the given token.
     */
    public boolean hasLock(final String resourceUri, final String token) {
        for (final LockInfo lock : getLocks(resourceUri)) {
            if (token.equals(lock.token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find a specific lock by token across all locks on a resource.
     */
    public LockInfo getLockByToken(final String resourceUri, final String token) {
        for (final LockInfo lock : getLocks(resourceUri)) {
            if (token.equals(lock.token)) {
                return lock;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void addLockEntry(final String token, final String resourceUri,
                               final String owner, final String scope,
                               final String type, final boolean deep,
                               final long timeout) throws EXistException {
        final List<LockInfo> existing = getLocks(resourceUri);
        final List<LockInfo> all = new ArrayList<>(existing);

        final LockInfo newLock = new LockInfo();
        newLock.token = token;
        newLock.scope = scope;
        newLock.type = type;
        newLock.owner = owner != null ? owner : "unknown";
        newLock.deep = deep;
        newLock.timeout = timeout;
        all.add(newLock);

        storeLocksDocument(resourceUri, all);
        LOG.debug("Stored lock for {} with token {} (total: {})", resourceUri, token, all.size());
    }

    private void storeLocksDocument(final String resourceUri, final List<LockInfo> locks)
            throws EXistException {
        final String lockXml = buildLocksXml(locks);
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
    }

    private void removeLockDocument(final String resourceUri) throws EXistException {
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

        LOG.debug("Removed lock document for {}", resourceUri);
    }

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
     * /db/test/doc.xml -> db-test-doc.xml.lock.xml
     */
    static String uriToLockDocName(final String resourceUri) {
        String name = resourceUri;
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        return name.replace('/', '-') + ".lock.xml";
    }

    private String buildLocksXml(final List<LockInfo> locks) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<locks xmlns=\"").append(LOCK_NS).append("\">\n");
        for (final LockInfo lock : locks) {
            sb.append("    <lock>\n");
            sb.append("        <token>").append(esc(lock.token)).append("</token>\n");
            sb.append("        <scope>").append(esc(lock.scope)).append("</scope>\n");
            sb.append("        <type>").append(esc(lock.type)).append("</type>\n");
            sb.append("        <owner>").append(esc(lock.owner)).append("</owner>\n");
            sb.append("        <depth>").append(lock.deep ? "infinity" : "0").append("</depth>\n");
            sb.append("        <timeout>").append(lock.timeout > 0 ? "Second-" + lock.timeout : "Infinite").append("</timeout>\n");
            sb.append("        <created>").append(Instant.now().toString()).append("</created>\n");
            sb.append("    </lock>\n");
        }
        sb.append("</locks>\n");
        return sb.toString();
    }

    private List<LockInfo> parseLockDocument(final Document doc) {
        final Element root = doc.getDocumentElement();
        if (root == null) {
            return List.of();
        }

        final List<LockInfo> locks = new ArrayList<>();

        // New multi-lock format: <locks><lock>...</lock>...</locks>
        if ("locks".equals(root.getLocalName())) {
            final NodeList lockElements = root.getElementsByTagNameNS(LOCK_NS, "lock");
            if (lockElements.getLength() == 0) {
                // Try without namespace
                final NodeList lockElementsNoNs = root.getElementsByTagName("lock");
                for (int i = 0; i < lockElementsNoNs.getLength(); i++) {
                    locks.add(parseLockElement((Element) lockElementsNoNs.item(i)));
                }
            } else {
                for (int i = 0; i < lockElements.getLength(); i++) {
                    locks.add(parseLockElement((Element) lockElements.item(i)));
                }
            }
        } else if ("lock".equals(root.getLocalName())) {
            // Old single-lock format: <lock>...</lock>
            locks.add(parseLockElement(root));
        }

        return locks;
    }

    private LockInfo parseLockElement(final Element lockEl) {
        final LockInfo info = new LockInfo();
        info.token = getElementText(lockEl, "token");
        info.scope = getElementText(lockEl, "scope");
        info.type = getElementText(lockEl, "type");
        info.owner = getElementText(lockEl, "owner");
        info.resource = getElementText(lockEl, "resource");
        final String depth = getElementText(lockEl, "depth");
        info.deep = "infinity".equals(depth);
        return info;
    }

    private static String getElementText(final Element parent, final String localName) {
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
        public long timeout;
    }
}
