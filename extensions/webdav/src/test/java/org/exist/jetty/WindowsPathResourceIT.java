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

import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.exist.util.OSUtil;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Windows integration regression test for Jetty 12.1 {@link PathResource#resolve(String)} on
 * drive URIs ({@code /D:/...}). Lives in {@code exist-webdav} so it runs on Windows CI
 * ({@code verify -DskipUnitTests=true}).
 */
@SuppressWarnings("PMD.ClassNamingConventions") // Failsafe *IT suffix; not a JUnit *Test class
public class WindowsPathResourceIT {

    @Test
    public void resolveWebInfOnWindowsDriveUri() throws Exception {
        assumeTrue("Windows-only PathResource URI regression", OSUtil.isWindows());

        final ResourceFactory resourceFactory = ResourceFactory.root();
        final Path webapp = Files.createTempDirectory("webapp");
        Files.createDirectory(webapp.resolve("WEB-INF"));
        try {
            final Resource resource = resourceFactory.newResource(webapp);
            assumeTrue("Expected PathResource for local webapp directory", resource instanceof PathResource);
            final String uriPath = resource.getURI().getPath();
            assumeTrue("Expected absolute Windows drive URI path, got: " + uriPath,
                    uriPath != null && uriPath.matches("/[A-Za-z]:/.*"));

            final PathResource pathResource = (PathResource) resource;
            try {
                pathResource.resolve("WEB-INF/");
                // Jetty version may already fix resolve; wrapped path must still work.
            } catch (final InvalidPathException e) {
                // Expected on Jetty 12.1.x with /X:/... URI paths.
            }

            final Resource wrapped = WindowsPathResource.wrapIfNeeded(pathResource, resourceFactory);
            assertNotSame(pathResource, wrapped);

            final Resource webInf = wrapped.resolve("WEB-INF/");
            assertTrue("WEB-INF should resolve to a directory", webInf.isDirectory());
        } finally {
            Files.deleteIfExists(webapp.resolve("WEB-INF"));
            Files.deleteIfExists(webapp);
        }
    }
}
