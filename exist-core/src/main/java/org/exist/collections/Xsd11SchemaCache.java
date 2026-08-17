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
package org.exist.collections;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import javax.annotation.Nullable;
import javax.xml.validation.Schema;
import java.util.Optional;

/**
 * Per-namespace cache of whether the system catalog's grammar for that namespace needs an
 * XSD 1.1-capable loader -- see {@link Xsd11ValidationHelper#resolveXsd11SchemaForNamespace}.
 * A cache miss ({@link #get(String)} returning {@code null}) means "not yet resolved"; a hit of
 * {@code Optional.empty()} means "resolved: the standard XSD 1.0 pipeline handles this namespace
 * fine".
 * <p>
 * Namespaces reaching this cache are catalog-registered (a finite, admin-controlled set, never
 * attacker-influenced), so unlike {@link org.exist.validation.Xsd11SchemaDetection}'s
 * location-driven cache, no eviction/bounding is needed here -- entries live for the life of the
 * JVM, cleared only via {@link #clear()}.
 */
final class Xsd11SchemaCache {

    private static final Cache<String, Optional<Schema>> CACHE = Caffeine.newBuilder().build();

    private Xsd11SchemaCache() {
    }

    /**
     * @return the cached resolution for {@code namespace}, or {@code null} if nothing has been
     * cached for it yet.
     */
    @Nullable
    static Optional<Schema> get(final String namespace) {
        return CACHE.getIfPresent(namespace);
    }

    static void put(final String namespace, final Optional<Schema> schema) {
        CACHE.put(namespace, schema);
    }

    static void clear() {
        CACHE.invalidateAll();
    }
}
