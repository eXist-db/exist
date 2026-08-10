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
package org.exist.jetty;

import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.exist.util.OSUtil;

import java.net.URI;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;

/**
 * Workaround for Jetty 12.1 {@link PathResource#resolve(String)} on Windows.
 * <p>
 * Jetty 12.0 resolves sub-paths with {@code Paths.get(resolvedUri)}. Jetty 12.1 uses
 * {@code path.resolve(uri.getPath())}, which throws {@link java.nio.file.InvalidPathException}
 * when the URI path is {@code /D:/...}. Exploded test webapps on Windows CI hit this in
 * {@code WebAppContext.getWebInf()}. Remove when upstream Jetty restores safe Windows resolve.
 * <p>
 * This class extends {@link Resource}, not {@link PathResource}. Jetty internals that use
 * {@code instanceof PathResource} will not treat wrapped resources as path resources. eXist
 * production code has no {@code instanceof PathResource} checks on resources that may be wrapped;
 * CI and integration tests validate that the exploded-webapp startup path does not depend on
 * Jetty-internal type checks either.
 * <p>
 * Only {@link #resolve(String)} differs from Jetty 12.1 {@link PathResource} behaviour. Other
 * {@link Resource} methods delegate to the wrapped resource or rely on inherited defaults that
 * route through {@link #getPath()}, {@link #getURI()}, {@link #iterator()}, and {@link #resolve(String)}.
 */
public final class WindowsPathResource extends Resource {

    private final Resource delegate;
    private final ResourceFactory resourceFactory;

    private WindowsPathResource(final Resource delegate, final ResourceFactory resourceFactory) {
        this.delegate = Objects.requireNonNull(delegate);
        this.resourceFactory = Objects.requireNonNull(resourceFactory);
    }

    public static Resource wrapIfNeeded(final Resource resource, final ResourceFactory resourceFactory) {
        if (resource == null || !OSUtil.isWindows()) {
            return resource;
        }
        if (resource instanceof WindowsPathResource) {
            return resource;
        }
        if (!(resource instanceof PathResource)) {
            return resource;
        }
        return new WindowsPathResource(resource, resourceFactory);
    }

    @Override
    public Path getPath() {
        return delegate.getPath();
    }

    @Override
    public boolean isDirectory() {
        return delegate.isDirectory();
    }

    @Override
    public boolean isReadable() {
        return delegate.isReadable();
    }

    @Override
    public URI getURI() {
        return delegate.getURI();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getFileName() {
        return delegate.getFileName();
    }

    @Override
    public Resource resolve(final String subUriPath) {
        if (URIUtil.isNotNormalWithinSelf(subUriPath)) {
            throw new IllegalArgumentException(subUriPath);
        }
        if ("/".equals(subUriPath)) {
            return this;
        }
        final URI resolvedUri = URIUtil.addPath(getURI(), subUriPath);
        final Path path = Path.of(resolvedUri);
        return wrapIfNeeded(resourceFactory.newResource(path), resourceFactory);
    }

    @Override
    public Iterator<Resource> iterator() {
        return delegate.iterator();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Resource other)) {
            return false;
        }
        if (other instanceof WindowsPathResource wrapped) {
            other = wrapped.delegate;
        }
        return delegate.equals(other);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
