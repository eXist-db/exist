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

import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertySet;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory store for WebDAV dead properties (custom user-defined properties).
 * Dead properties are stored per resource path and are lost on server restart.
 *
 * <p>This is a PoC implementation. A production implementation should persist
 * dead properties in the database (e.g., as document metadata or in a system
 * collection like /db/system/webdav-properties/).</p>
 *
 * @author Joe Wicentowski
 */
class DeadPropertyStore {

    private final ConcurrentMap<String, DavPropertySet> store = new ConcurrentHashMap<>();

    /**
     * Get all dead properties for a resource.
     *
     * @param resourcePath the resource path (e.g., "/db/myfile.xml")
     * @return the property set (never null, may be empty)
     */
    DavPropertySet getProperties(final String resourcePath) {
        return store.getOrDefault(resourcePath, new DavPropertySet());
    }

    /**
     * Get a specific dead property for a resource.
     *
     * @param resourcePath the resource path
     * @param name         the property name
     * @return the property, or null if not set
     */
    DavProperty<?> getProperty(final String resourcePath, final DavPropertyName name) {
        final DavPropertySet props = store.get(resourcePath);
        if (props == null) {
            return null;
        }
        return props.get(name);
    }

    /**
     * Set a dead property on a resource.
     *
     * @param resourcePath the resource path
     * @param property     the property to set
     */
    void setProperty(final String resourcePath, final DavProperty<?> property) {
        store.computeIfAbsent(resourcePath, k -> new DavPropertySet()).add(property);
    }

    /**
     * Remove a dead property from a resource.
     *
     * @param resourcePath the resource path
     * @param name         the property name to remove
     */
    void removeProperty(final String resourcePath, final DavPropertyName name) {
        final DavPropertySet props = store.get(resourcePath);
        if (props != null) {
            props.remove(name);
            if (props.isEmpty()) {
                store.remove(resourcePath);
            }
        }
    }

    /**
     * Remove all dead properties for a resource (e.g., on DELETE).
     *
     * @param resourcePath the resource path
     */
    void removeAll(final String resourcePath) {
        store.remove(resourcePath);
    }

    /**
     * Copy dead properties from one resource to another.
     *
     * @param sourcePath the source resource path
     * @param destPath   the destination resource path
     */
    void copyProperties(final String sourcePath, final String destPath) {
        final DavPropertySet sourceProps = store.get(sourcePath);
        if (sourceProps != null && !sourceProps.isEmpty()) {
            final DavPropertySet destProps = new DavPropertySet();
            for (final DavPropertyName name : sourceProps.getPropertyNames()) {
                destProps.add(sourceProps.get(name));
            }
            store.put(destPath, destProps);
        }
    }
}
