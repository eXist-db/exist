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
package org.exist.http.openapi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-memory registry of OpenAPI routes discovered from api.json and controller.json
 * files stored in the database. Routes are scoped by collection path.
 * <p>
 * Thread-safe: uses ConcurrentHashMap for route storage.
 * </p>
 */
public class OpenApiServiceRegistry {

    private static final Logger LOG = LogManager.getLogger(OpenApiServiceRegistry.class);

    private static volatile OpenApiServiceRegistry instance;

    /** All registered routes, keyed by collection path */
    private final ConcurrentHashMap<String, List<Route>> routesByCollection = new ConcurrentHashMap<>();

    public static OpenApiServiceRegistry getInstance() {
        if (instance == null) {
            synchronized (OpenApiServiceRegistry.class) {
                if (instance == null) {
                    instance = new OpenApiServiceRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * Register routes from an OpenAPI spec for a given collection.
     *
     * @param collectionPath the database collection path (e.g., "/db/apps/myapp")
     * @param routes         the routes parsed from the spec
     */
    public void registerRoutes(final String collectionPath, final List<Route> routes) {
        routesByCollection.put(collectionPath, new ArrayList<>(routes));
        LOG.info("Registered {} OpenAPI routes for collection {}", routes.size(), collectionPath);
    }

    /**
     * Deregister all routes for a collection.
     */
    public void deregisterRoutes(final String collectionPath) {
        final List<Route> removed = routesByCollection.remove(collectionPath);
        if (removed != null) {
            LOG.info("Deregistered {} OpenAPI routes for collection {}", removed.size(), collectionPath);
        }
    }

    /**
     * Find a matching route for the given HTTP method and path.
     * The path should be relative to /exist/apps/ (e.g., "myapp/api/users/123").
     *
     * @param method HTTP method (GET, POST, etc.)
     * @param path   request path relative to /exist/apps/
     * @return matched route with extracted path parameters, or null if no match
     */
    @Nullable
    public RouteMatch findRoute(final String method, final String path) {
        // Extract app name from path (first segment)
        final int slashIdx = path.indexOf('/');
        final String appName = slashIdx > 0 ? path.substring(0, slashIdx) : path;
        final String remainingPath = slashIdx > 0 ? path.substring(slashIdx) : "/";

        // Search for matching collection
        for (final Map.Entry<String, List<Route>> entry : routesByCollection.entrySet()) {
            final String collectionPath = entry.getKey();
            // Match by app name (last segment of collection path)
            final String collAppName = collectionPath.substring(collectionPath.lastIndexOf('/') + 1);
            if (!collAppName.equals(appName)) {
                continue;
            }

            // Search routes in this collection
            RouteMatch bestMatch = null;
            for (final Route route : entry.getValue()) {
                if (!route.method().equalsIgnoreCase(method)) {
                    continue;
                }
                final Map<String, String> params = matchPath(route.pathPattern(), route.pathRegex(), remainingPath);
                if (params != null) {
                    final RouteMatch match = new RouteMatch(route, collectionPath, params);
                    // Prefer more specific routes (longer pattern without variables)
                    if (bestMatch == null || match.specificity() > bestMatch.specificity()) {
                        bestMatch = match;
                    }
                }
            }
            if (bestMatch != null) {
                return bestMatch;
            }
        }
        return null;
    }

    /**
     * Check if any routes are registered.
     */
    public boolean hasRoutes() {
        return !routesByCollection.isEmpty();
    }

    /**
     * Get all registered collection paths.
     */
    public Set<String> getRegisteredCollections() {
        return routesByCollection.keySet();
    }

    // --- Path matching ---

    /**
     * Match a request path against a route's compiled regex.
     * Returns parameter map if matched, null otherwise.
     */
    @Nullable
    private Map<String, String> matchPath(final String pattern, final Pattern regex, final String path) {
        final Matcher matcher = regex.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        final Map<String, String> params = new LinkedHashMap<>();
        // Extract named groups from the pattern
        final List<String> paramNames = extractParamNames(pattern);
        for (int i = 0; i < paramNames.size(); i++) {
            if (i + 1 <= matcher.groupCount()) {
                params.put(paramNames.get(i), matcher.group(i + 1));
            }
        }
        return params;
    }

    // --- Static helpers for route compilation ---

    /**
     * Compile an OpenAPI path pattern (e.g., "/users/{id}") into a regex.
     */
    public static Pattern compilePathPattern(final String pattern) {
        final StringBuilder regex = new StringBuilder("^");
        int i = 0;
        while (i < pattern.length()) {
            final char c = pattern.charAt(i);
            if (c == '{') {
                final int end = pattern.indexOf('}', i);
                if (end > 0) {
                    regex.append("([^/]+)");
                    i = end + 1;
                    continue;
                }
            }
            // Escape regex special characters
            if (".+*?^$[]()\\|".indexOf(c) >= 0) {
                regex.append('\\');
            }
            regex.append(c);
            i++;
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    /**
     * Extract parameter names from an OpenAPI path pattern.
     */
    public static List<String> extractParamNames(final String pattern) {
        final List<String> names = new ArrayList<>();
        int i = 0;
        while (i < pattern.length()) {
            if (pattern.charAt(i) == '{') {
                final int end = pattern.indexOf('}', i);
                if (end > 0) {
                    names.add(pattern.substring(i + 1, end));
                    i = end + 1;
                    continue;
                }
            }
            i++;
        }
        return names;
    }

    // --- Inner types ---

    /**
     * A registered route from an OpenAPI spec.
     */
    public record Route(
            String method,        // HTTP method (GET, POST, etc.)
            String pathPattern,   // original pattern, e.g. "/api/users/{id}"
            Pattern pathRegex,    // compiled regex
            String operationId,   // e.g. "users:get"
            Map<String, Object> spec  // the full operation spec from api.json
    ) {
        /**
         * Specificity score: how specific is this route? Higher = more specific.
         * Literal segments score higher than parameterized ones.
         */
        public int specificity() {
            return pathPattern.replaceAll("\\{[^}]+}", "").length();
        }
    }

    /**
     * A matched route with extracted path parameters.
     */
    public record RouteMatch(
            Route route,
            String collectionPath,
            Map<String, String> pathParams
    ) {
        public int specificity() {
            return route.specificity();
        }
    }
}
