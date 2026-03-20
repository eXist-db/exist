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
package org.exist.http.restxq;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.source.DBSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory route table that discovers RESTXQ-annotated functions from
 * XQuery modules stored in the database. Uses timestamp-based caching
 * to re-parse only when modules change.
 *
 * <p>This replaces the trigger-based {@code ExistXqueryRegistry} and
 * {@code RestXqServiceRegistryPersistence} from the old EXQuery-based
 * implementation.</p>
 *
 * <p>Thread safety is provided by a read/write lock: multiple HTTP
 * request threads can read the route table concurrently, while scans
 * acquire a write lock.</p>
 */
public class RouteRegistry {

    private static final Logger LOG = LogManager.getLogger(RouteRegistry.class);

    private static final Set<String> XQUERY_MIME_TYPES = Set.of(
            "application/xquery"
    );

    private static final Set<String> XQUERY_EXTENSIONS = Set.of(
            ".xq", ".xqm", ".xql", ".xquery"
    );

    private final BrokerPool brokerPool;
    private final XmldbURI scanRoot;

    /** Module URI → last-modified timestamp at time of last parse. */
    private final Map<String, Long> moduleTimestamps = new ConcurrentHashMap<>();

    /** All currently registered routes, sorted by specificity. */
    private volatile List<Route> routes = Collections.emptyList();

    /** All currently registered error handlers. */
    private volatile List<ErrorRoute> errorRoutes = Collections.emptyList();

    /** Protects route table mutations. */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** Per-module route list, for efficient partial updates. */
    private final Map<String, List<Route>> routesByModule = new ConcurrentHashMap<>();

    /** Per-module error route list. */
    private final Map<String, List<ErrorRoute>> errorRoutesByModule = new ConcurrentHashMap<>();

    /** Modules that failed compilation. */
    private final Map<String, String> failedModules = new ConcurrentHashMap<>();

    /** When we last completed a full scan (epoch millis). */
    private volatile long lastScanTime = 0;

    /** How long the scan took (ms). */
    private volatile long lastScanDurationMs = 0;

    /** Whether at least one scan has completed. */
    private volatile boolean initialized = false;

    public RouteRegistry(final BrokerPool brokerPool, final String scanRoot) {
        this.brokerPool = brokerPool;
        this.scanRoot = XmldbURI.create(scanRoot);
    }

    /**
     * Finds the best matching route for the given HTTP method and path.
     *
     * <p>Routes are pre-sorted by specificity; the first match wins.
     * Content negotiation (consumes/produces) is also checked.</p>
     *
     * @return the matching Route, or null if no route matches
     */
    public Route findRoute(final String method, final String path,
                           final String contentType, final String acceptHeader) {
        final List<Route> snapshot = routes;
        for (final Route route : snapshot) {
            if (route.matches(method, path)
                    && route.matchesConsumes(contentType)
                    && route.matchesProduces(acceptHeader)) {
                return route;
            }
        }
        return null;
    }

    /**
     * Returns all routes that match the given path (regardless of method),
     * useful for generating Allow headers on 405 responses.
     */
    public Set<String> allowedMethods(final String path) {
        return allowedMethods(path, null, null);
    }

    /**
     * Returns all HTTP methods that have a matching route for the given path
     * and content negotiation constraints. Used for 405 Allow headers.
     */
    public Set<String> allowedMethods(final String path, final String contentType,
                                      final String acceptHeader) {
        final Set<String> methods = new LinkedHashSet<>();
        for (final Route route : routes) {
            if (route.getPathMatcher().matches(path)
                    && route.matchesConsumes(contentType)
                    && route.matchesProduces(acceptHeader)) {
                methods.addAll(route.getMethods());
            }
        }
        return methods;
    }

    /**
     * Invalidates the entire route cache, forcing a full rescan
     * on the next call to {@link #ensureInitialized(DBBroker)}.
     */
    public void invalidate() {
        lock.writeLock().lock();
        try {
            routes = Collections.emptyList();
            errorRoutes = Collections.emptyList();
            routesByModule.clear();
            errorRoutesByModule.clear();
            moduleTimestamps.clear();
            failedModules.clear();
            initialized = false;
            LOG.info("RESTXQ route registry invalidated; will rescan on next request");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Ensures the registry has been initialized (at least one scan completed).
     * If not yet initialized, performs a full scan.
     */
    public void ensureInitialized(final DBBroker broker) {
        if (!initialized) {
            fullScan(broker);
        }
    }

    /**
     * Performs a full scan of all XQuery modules under the scan root.
     * Modules whose timestamp hasn't changed since last scan are skipped.
     */
    public void fullScan(final DBBroker broker) {
        lock.writeLock().lock();
        try {
            final long start = System.currentTimeMillis();
            LOG.info("Starting RESTXQ module scan of {}", scanRoot);

            // Track which modules exist during this scan
            final Set<String> scannedModules = new HashSet<>();
            scanCollection(broker, scanRoot, scannedModules);

            // Remove routes/failures for modules that no longer exist
            routesByModule.keySet().retainAll(scannedModules);
            errorRoutesByModule.keySet().retainAll(scannedModules);
            moduleTimestamps.keySet().retainAll(scannedModules);
            failedModules.keySet().retainAll(scannedModules);

            rebuildRouteList();

            lastScanTime = System.currentTimeMillis();
            lastScanDurationMs = lastScanTime - start;
            initialized = true;

            LOG.info("RESTXQ scan complete: {} routes from {} modules in {}ms",
                    routes.size(), routesByModule.size(), lastScanDurationMs);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void scanCollection(final DBBroker broker, final XmldbURI collectionUri,
                               final Set<String> scannedModules) {
        try (final Collection collection = broker.openCollection(collectionUri, LockMode.READ_LOCK)) {
            if (collection == null) {
                return;
            }

            // Scan documents in this collection
            final Iterator<DocumentImpl> docs = collection.iterator(broker);
            while (docs.hasNext()) {
                final DocumentImpl doc = docs.next();
                if (isXQueryDocument(doc)) {
                    scannedModules.add(doc.getURI().toString());
                    scanDocument(broker, doc);
                }
            }

            // Recurse into child collections
            // We need to collect child URIs while holding the lock, then recurse
            final List<XmldbURI> childUris = new ArrayList<>();
            for (final Iterator<XmldbURI> it = collection.collectionIterator(broker); it.hasNext(); ) {
                childUris.add(collectionUri.append(it.next()));
            }

            // Release parent lock before recursing (we re-acquire per child)
            collection.close();
            for (final XmldbURI childUri : childUris) {
                scanCollection(broker, childUri, scannedModules);
            }

        } catch (final PermissionDeniedException e) {
            LOG.debug("Permission denied scanning collection {}: {}", collectionUri, e.getMessage());
        } catch (final Exception e) {
            LOG.warn("Error scanning collection {}: {}", collectionUri, e.getMessage());
        }
    }

    private void scanDocument(final DBBroker broker, final DocumentImpl doc) {
        final String moduleUri = doc.getURI().toString();
        final long lastModified = doc.getLastModified();

        // Check if we already have an up-to-date parse
        final Long cached = moduleTimestamps.get(moduleUri);
        if (cached != null && cached == lastModified) {
            return;
        }

        // Need to (re-)parse this module
        try {
            if (!(doc instanceof BinaryDocument binDoc)) {
                return;
            }

            {
                final DBSource source = new DBSource(brokerPool, binDoc, true);
                final XQuery xqueryService = brokerPool.getXQueryService();
                final XQueryContext context = new XQueryContext(brokerPool);

                // Set module load path so relative imports resolve correctly
                context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI_PREFIX + doc.getURI().removeLastSegment().toString());

                final CompiledXQuery compiled = xqueryService.compile(context, source);
                final AnnotationParser.ParseResult result =
                        AnnotationParser.parseModuleFull(compiled, moduleUri);

                if (!result.routes.isEmpty()) {
                    routesByModule.put(moduleUri, result.routes);
                    LOG.debug("Found {} RESTXQ routes in {}", result.routes.size(), moduleUri);
                } else {
                    routesByModule.remove(moduleUri);
                }

                if (!result.errorRoutes.isEmpty()) {
                    errorRoutesByModule.put(moduleUri, result.errorRoutes);
                    LOG.debug("Found {} RESTXQ error handlers in {}", result.errorRoutes.size(), moduleUri);
                } else {
                    errorRoutesByModule.remove(moduleUri);
                }

                moduleTimestamps.put(moduleUri, lastModified);
                failedModules.remove(moduleUri);
            }
        } catch (final RestXqAnnotationException e) {
            LOG.warn("RESTXQ annotation error in {}: {}", moduleUri, e.getMessage());
            failedModules.put(moduleUri, e.getMessage());
            routesByModule.remove(moduleUri);
            errorRoutesByModule.remove(moduleUri);
            moduleTimestamps.remove(moduleUri);
        } catch (final XPathException e) {
            LOG.warn("Failed to compile RESTXQ module {}: {}", moduleUri, e.getMessage());
            failedModules.put(moduleUri, e.getMessage());
            routesByModule.remove(moduleUri);
            errorRoutesByModule.remove(moduleUri);
            moduleTimestamps.remove(moduleUri);
        } catch (final PermissionDeniedException | IOException e) {
            LOG.warn("Error reading RESTXQ module {}: {}", moduleUri, e.getMessage());
            failedModules.put(moduleUri, e.getMessage());
        } catch (final Exception e) {
            LOG.warn("Unexpected error reading RESTXQ module {}: {}", moduleUri, e.getMessage());
            failedModules.put(moduleUri, e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Rebuilds the flat, sorted route list from per-module route maps.
     * Must be called under write lock.
     */
    private void rebuildRouteList() {
        final List<Route> allRoutes = new ArrayList<>();
        for (final List<Route> moduleRoutes : routesByModule.values()) {
            allRoutes.addAll(moduleRoutes);
        }
        Collections.sort(allRoutes);
        this.routes = Collections.unmodifiableList(allRoutes);

        final List<ErrorRoute> allErrorRoutes = new ArrayList<>();
        for (final List<ErrorRoute> moduleErrorRoutes : errorRoutesByModule.values()) {
            allErrorRoutes.addAll(moduleErrorRoutes);
        }
        this.errorRoutes = Collections.unmodifiableList(allErrorRoutes);
    }

    private boolean isXQueryDocument(final DocumentImpl doc) {
        if (doc instanceof BinaryDocument) {
            final String mimeType = doc.getMimeType();
            if (mimeType != null && XQUERY_MIME_TYPES.contains(mimeType)) {
                return true;
            }
            // Fallback: check file extension
            final String name = doc.getFileURI().toString();
            for (final String ext : XQUERY_EXTENSIONS) {
                if (name.endsWith(ext)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds the best matching error handler for the given error QName.
     * Returns null if no handler matches.
     */
    public ErrorRoute findErrorHandler(final org.exist.dom.QName errorQName) {
        ErrorRoute bestHandler = null;
        ErrorRoute.ErrorCode bestCode = null;

        for (final ErrorRoute handler : errorRoutes) {
            final ErrorRoute.ErrorCode match = handler.bestMatch(errorQName);
            if (match != null) {
                if (bestCode == null || match.getMatchType().priority < bestCode.getMatchType().priority) {
                    bestHandler = handler;
                    bestCode = match;
                }
            }
        }
        return bestHandler;
    }

    // --- Status accessors ---

    public int getRouteCount() {
        return routes.size();
    }

    public int getModuleCount() {
        return routesByModule.size();
    }

    public long getLastScanTime() {
        return lastScanTime;
    }

    public long getLastScanDurationMs() {
        return lastScanDurationMs;
    }

    public Map<String, String> getFailedModules() {
        return Collections.unmodifiableMap(failedModules);
    }

    public List<Route> getAllRoutes() {
        return routes;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
