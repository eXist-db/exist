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

import jakarta.servlet.ServletException;
import org.apache.jackrabbit.webdav.*;
import org.apache.jackrabbit.webdav.lock.ActiveLock;
import org.apache.jackrabbit.webdav.lock.LockInfo;
import org.apache.jackrabbit.webdav.server.AbstractWebdavServlet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;

/**
 * WebDAV servlet based on Apache Jackrabbit WebDAV library.
 * Extends {@link AbstractWebdavServlet} which handles all HTTP method
 * dispatching (PROPFIND, COPY, MOVE, LOCK, etc.) and delegates to
 * the resource factory for resource creation.
 *
 * <p>Authentication is handled by the servlet container (Jetty JAAS).
 * The servlet maps request URIs under {@code /webdav} to eXist-db
 * database paths starting at {@code /db}.</p>
 *
 * @author Joe Wicentowski
 */
public class ExistWebdavServlet extends AbstractWebdavServlet {

    private static final Logger LOG = LogManager.getLogger(ExistWebdavServlet.class);

    private DavSessionProvider davSessionProvider;
    private DavLocatorFactory locatorFactory;
    private DavResourceFactory resourceFactory;

    @Override
    public void init() throws ServletException {
        super.init();

        try {
            final BrokerPool pool = BrokerPool.getInstance();

            // Create the locator factory - maps request URIs to resource paths
            // The prefix is the servlet context path + servlet path (e.g. "/webdav")
            locatorFactory = new ExistDavLocatorFactory("/webdav");

            // Create the resource factory - creates DavResource objects from locators
            resourceFactory = new ExistDavResourceFactory(pool);

            // Create the session provider - attaches DavSession to requests
            davSessionProvider = new ExistDavSessionProvider(pool);

            LOG.info("eXist-db Jackrabbit WebDAV servlet initialized successfully");

        } catch (final EXistException e) {
            LOG.error("Unable to initialize WebDAV servlet", e);
            throw new ServletException("Unable to initialize WebDAV servlet", e);
        }
    }

    @Override
    protected void service(final jakarta.servlet.http.HttpServletRequest request,
            final jakarta.servlet.http.HttpServletResponse response)
            throws jakarta.servlet.ServletException, java.io.IOException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("WebDAV {} {} (servletPath={}, pathInfo={})",
                    request.getMethod(), request.getRequestURI(),
                    request.getServletPath(), request.getPathInfo());
        }
        try {
            super.service(request, response);
        } catch (final Throwable t) {
            LOG.error("WebDAV request failed: {} {}", request.getMethod(), request.getRequestURI(), t);
            throw t;
        }
    }

    @Override
    protected boolean isPreconditionValid(final WebdavRequest request, final DavResource resource) {
        if (!resource.exists()) {
            return true;
        }
        // Check the resource itself first
        if (request.matchesIfHeader(resource)) {
            return true;
        }
        // If the resource match failed, check if a parent collection has a
        // deep lock whose token appears in the If header.
        DavResource parent = resource.getCollection();
        while (parent != null) {
            final ActiveLock parentLock = parent.getLock(
                    org.apache.jackrabbit.webdav.lock.Type.WRITE,
                    org.apache.jackrabbit.webdav.lock.Scope.EXCLUSIVE);
            if (parentLock != null && parentLock.isDeep()) {
                return request.matchesIfHeader(parent);
            }
            parent = parent.getCollection();
        }
        // No matching lock found — if no If header was sent, this is OK
        final String ifHeader = request.getHeader("If");
        return ifHeader == null || ifHeader.isEmpty();
    }

    /**
     * Check if a destructive operation on a locked resource has a valid lock
     * token in the If header. Returns 423 (Locked) if the resource is locked
     * and no matching token is provided (RFC 4918 §9.4).
     *
     * This check uses simple string matching on the If header. Jackrabbit's
     * matchesIfHeader handles the full If-header grammar; this is a guard
     * for the case where no If header is present at all.
     */
    private void requireLockTokenForWrite(final WebdavRequest request, final DavResource resource)
            throws DavException {
        if (!resource.exists()) {
            return;
        }
        // Check both exclusive and shared locks
        ActiveLock lock = resource.getLock(
                org.apache.jackrabbit.webdav.lock.Type.WRITE,
                org.apache.jackrabbit.webdav.lock.Scope.EXCLUSIVE);
        if (lock == null) {
            lock = resource.getLock(
                    org.apache.jackrabbit.webdav.lock.Type.WRITE,
                    org.apache.jackrabbit.webdav.lock.Scope.SHARED);
        }
        if (lock == null || lock.getToken() == null) {
            return;
        }
        // Only block when NO If header is present at all.
        // When an If header IS present, let Jackrabbit's matchesIfHeader
        // handle the full evaluation (including Not conditions, ETags, etc.)
        final String ifHeader = request.getHeader("If");
        if (ifHeader == null || ifHeader.isEmpty()) {
            throw new DavException(DavServletResponse.SC_LOCKED,
                    "Resource is locked; provide lock token in If header");
        }
    }

    /**
     * Check if any ancestor collection is locked (deep lock).
     * If so, the If header must contain the ancestor's lock token.
     */
    private void requireParentLockToken(final WebdavRequest request, final DavResource resource)
            throws DavException {
        DavResource parent = resource.getCollection();
        while (parent != null) {
            ActiveLock parentLock = parent.getLock(
                    org.apache.jackrabbit.webdav.lock.Type.WRITE,
                    org.apache.jackrabbit.webdav.lock.Scope.EXCLUSIVE);
            if (parentLock == null) {
                parentLock = parent.getLock(
                        org.apache.jackrabbit.webdav.lock.Type.WRITE,
                        org.apache.jackrabbit.webdav.lock.Scope.SHARED);
            }
            if (parentLock != null && parentLock.getToken() != null && parentLock.isDeep()) {
                final String ifHeader = request.getHeader("If");
                if (ifHeader == null || !ifHeader.contains(parentLock.getToken())) {
                    throw new DavException(DavServletResponse.SC_LOCKED,
                            "Parent collection is locked; provide lock token in If header");
                }
                return;
            }
            parent = parent.getCollection();
        }
    }

    @Override
    protected void doPropPatch(final WebdavRequest request, final WebdavResponse response,
            final DavResource resource) throws java.io.IOException, DavException {
        requireLockTokenForWrite(request, resource);
        requireParentLockToken(request, resource);
        super.doPropPatch(request, response, resource);
    }

    @Override
    protected void doPut(final WebdavRequest request, final WebdavResponse response,
            final DavResource resource) throws java.io.IOException, DavException {
        requireLockTokenForWrite(request, resource);
        requireParentLockToken(request, resource);
        super.doPut(request, response, resource);
    }

    @Override
    protected void doDelete(final WebdavRequest request, final WebdavResponse response,
            final DavResource resource) throws java.io.IOException, DavException {
        requireLockTokenForWrite(request, resource);
        requireParentLockToken(request, resource);
        super.doDelete(request, response, resource);
    }

    @Override
    protected void doCopy(final WebdavRequest request, final WebdavResponse response,
            final DavResource resource) throws java.io.IOException, DavException {
        // Check destination lock — COPY onto a locked resource requires the token
        final DavResource dest = getResourceFactory().createResource(
                request.getDestinationLocator(), request, response);
        requireLockTokenForWrite(request, dest);
        requireParentLockToken(request, dest);
        super.doCopy(request, response, resource);
    }

    @Override
    protected void doMove(final WebdavRequest request, final WebdavResponse response,
            final DavResource resource) throws java.io.IOException, DavException {
        requireLockTokenForWrite(request, resource);
        requireParentLockToken(request, resource);
        super.doMove(request, response, resource);
    }

    @Override
    public DavSessionProvider getDavSessionProvider() {
        return davSessionProvider;
    }

    @Override
    public void setDavSessionProvider(final DavSessionProvider davSessionProvider) {
        this.davSessionProvider = davSessionProvider;
    }

    @Override
    public DavLocatorFactory getLocatorFactory() {
        return locatorFactory;
    }

    @Override
    public void setLocatorFactory(final DavLocatorFactory locatorFactory) {
        this.locatorFactory = locatorFactory;
    }

    @Override
    public DavResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    @Override
    public void setResourceFactory(final DavResourceFactory resourceFactory) {
        this.resourceFactory = resourceFactory;
    }

    // -----------------------------------------------------------------------
    // Inner classes
    // -----------------------------------------------------------------------

    /**
     * DavLocatorFactory implementation that maps WebDAV request URIs
     * to eXist-db database paths. Strips the "/webdav" prefix from
     * incoming paths and maps them to "/db/...".
     *
     * The locator uses a two-part prefix model:
     * - hrefPrefix: scheme://host:port/contextPath (from WebdavRequestImpl)
     * - webdavPath: "/webdav" (the servlet mapping path)
     *
     * getPrefix() returns hrefPrefix + webdavPath for href generation.
     * getHref() returns the db-relative path (e.g., "/system/" for /db/system).
     */
    static class ExistDavLocatorFactory implements DavLocatorFactory {

        private final String webdavPath;

        ExistDavLocatorFactory(final String webdavPath) {
            this.webdavPath = webdavPath;
        }

        @Override
        public DavResourceLocator createResourceLocator(final String hrefPrefix, final String href) {
            // href comes from the request URI minus contextPath
            // e.g., "/webdav/db/system" or "/webdav/db/"
            // Strip the webdav path to get the database path
            String dbPath = href;
            if (dbPath != null && dbPath.startsWith(webdavPath)) {
                dbPath = dbPath.substring(webdavPath.length());
            }
            return createLocator(hrefPrefix, dbPath);
        }

        @Override
        public DavResourceLocator createResourceLocator(final String hrefPrefix,
                final String workspacePath, final String resourcePath) {
            return createResourceLocator(hrefPrefix, workspacePath, resourcePath, true);
        }

        @Override
        public DavResourceLocator createResourceLocator(final String hrefPrefix,
                final String workspacePath, final String path, final boolean isResourcePath) {
            // For child resource creation, path is already a db path (e.g., "/db/system")
            return createLocator(hrefPrefix, path);
        }

        private DavResourceLocator createLocator(final String hrefPrefix, final String dbPath) {
            String normalized = (dbPath == null || dbPath.isEmpty()) ? "/db" : dbPath;
            if (!normalized.startsWith("/db")) {
                normalized = "/db" + normalized;
            }
            if (normalized.length() > 1 && normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return new ExistDavLocator(hrefPrefix, webdavPath, normalized, this);
        }
    }

    /**
     * DavResourceLocator implementation for eXist-db resources.
     *
     * Stores the href prefix (scheme://host:port/contextPath) separately
     * from the webdav path ("/webdav") so that getPrefix() always returns
     * the correct base for href generation.
     */
    static class ExistDavLocator implements DavResourceLocator {

        private final String hrefPrefix;
        private final String webdavPath;
        private final String resourcePath;
        private final DavLocatorFactory factory;

        ExistDavLocator(final String hrefPrefix, final String webdavPath,
                final String resourcePath, final DavLocatorFactory factory) {
            this.hrefPrefix = hrefPrefix;
            this.webdavPath = webdavPath;
            this.resourcePath = resourcePath;
            this.factory = factory;
        }

        @Override
        public String getPrefix() {
            // hrefPrefix is scheme://host:port/contextPath
            // Append the webdav path to form the full prefix
            if (hrefPrefix.endsWith(webdavPath)) {
                return hrefPrefix;
            }
            return hrefPrefix + webdavPath;
        }

        @Override
        public String getResourcePath() {
            return resourcePath;
        }

        @Override
        public String getWorkspacePath() {
            return "/db";
        }

        @Override
        public String getWorkspaceName() {
            return "db";
        }

        @Override
        public boolean isSameWorkspace(final DavResourceLocator locator) {
            return locator != null && isSameWorkspace(locator.getWorkspacePath());
        }

        @Override
        public boolean isSameWorkspace(final String workspaceName) {
            return "/db".equals(workspaceName) || "db".equals(workspaceName);
        }

        @Override
        public String getHref(final boolean isCollection) {
            // Jackrabbit uses getHref() directly for <D:href> in responses.
            // Must return the full path relative to the server root,
            // including the context path (e.g., /exist/webdav/db/).
            final String href;
            if (hrefPrefix.endsWith(webdavPath)) {
                href = hrefPrefix + resourcePath;
            } else {
                href = hrefPrefix + webdavPath + resourcePath;
            }
            if (isCollection && !href.endsWith("/")) {
                return href + "/";
            }
            return href;
        }

        @Override
        public boolean isRootLocation() {
            return "/db".equals(resourcePath);
        }

        @Override
        public DavLocatorFactory getFactory() {
            return factory;
        }

        @Override
        public String getRepositoryPath() {
            return resourcePath;
        }

        @Override
        public int hashCode() {
            return resourcePath.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            return this == obj
                    || (obj instanceof ExistDavLocator other
                            && resourcePath.equals(other.resourcePath));
        }
    }

    /**
     * DavSessionProvider that creates ExistDavSession objects.
     * Authentication is handled by the servlet container; this provider
     * extracts the authenticated user principal and maps it to an
     * eXist-db Subject.
     */
    static class ExistDavSessionProvider implements DavSessionProvider {

        private final BrokerPool pool;

        ExistDavSessionProvider(final BrokerPool pool) {
            this.pool = pool;
        }

        @Override
        public boolean attachSession(final WebdavRequest request) throws DavException {
            final SecurityManager securityManager = pool.getSecurityManager();
            Subject subject = null;

            // Try Basic Auth from the Authorization header
            final String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Basic ")) {
                final String encoded = authHeader.substring(6);
                final String decoded = new String(java.util.Base64.getDecoder().decode(encoded));
                final int colon = decoded.indexOf(':');
                if (colon >= 0) {
                    final String username = decoded.substring(0, colon);
                    final String password = decoded.substring(colon + 1);
                    try {
                        subject = securityManager.authenticate(username, password);
                    } catch (final org.exist.security.AuthenticationException e) {
                        LOG.debug("Authentication failed for user '{}': {}", username, e.getMessage());
                    }
                }
            }

            // Fall back to container principal
            if (subject == null) {
                final java.security.Principal principal = request.getUserPrincipal();
                if (principal != null && securityManager.hasAccount(principal.getName())) {
                    subject = securityManager.getSystemSubject();
                }
            }

            // Fall back to guest
            if (subject == null) {
                subject = securityManager.getGuestSubject();
            }

            // Reject guest access for write operations
            if (subject.equals(securityManager.getGuestSubject())) {
                // Allow read operations (PROPFIND, GET, OPTIONS) for guest
                final String method = request.getMethod();
                if ("PUT".equals(method) || "DELETE".equals(method) || "MKCOL".equals(method)
                        || "MOVE".equals(method) || "COPY".equals(method) || "LOCK".equals(method)) {
                    // Return 401 to trigger auth challenge
                    throw new DavException(DavServletResponse.SC_UNAUTHORIZED, "Authentication required");
                }
            }

            final ExistDavSession session = new ExistDavSession(subject);
            request.setDavSession(session);
            return true;
        }

        @Override
        public void releaseSession(final WebdavRequest request) {
            request.setDavSession(null);
        }
    }
}
