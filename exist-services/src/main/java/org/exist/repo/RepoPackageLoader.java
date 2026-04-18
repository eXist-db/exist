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
package org.exist.repo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.SystemProperties;
import org.exist.util.io.TemporaryFileManager;
import org.expath.pkg.repo.XarFileSource;
import org.expath.pkg.repo.XarSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads packages from a remote public repository (e.g. https://exist-db.org/exist/apps/public-repo).
 * Used by {@link Deployment} to resolve package dependencies during installation,
 * and by {@link PackageService} for direct package installation from a registry.
 */
public record RepoPackageLoader(String repoURL) implements PackageLoader {

    private static final Logger LOG = LogManager.getLogger(RepoPackageLoader.class);

    private static final int CONNECT_TIMEOUT = 15_000;
    private static final int READ_TIMEOUT = 15_000;

    @Override
    public XarSource load(final String name, final Version version) throws IOException {
        String pkgURL = repoURL + "?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) +
                "&processor=" + SystemProperties.getInstance().getSystemProperty("product-version", "2.2.0");
        if (version != null) {
            if (version.getMin() != null) {
                pkgURL += "&semver-min=" + version.getMin();
            }
            if (version.getMax() != null) {
                pkgURL += "&semver-max=" + version.getMax();
            }
            if (version.getSemVer() != null) {
                pkgURL += "&semver=" + version.getSemVer();
            }
            if (version.getVersion() != null) {
                pkgURL += "&version=" + URLEncoder.encode(version.getVersion(), StandardCharsets.UTF_8);
            }
        }
        LOG.info("Retrieving package from {}", pkgURL);
        final HttpURLConnection connection = (HttpURLConnection) URI.create(pkgURL).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "eXist-db Package Manager");
        connection.connect();

        try (final InputStream is = connection.getInputStream()) {
            final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
            final Path outFile = temporaryFileManager.getTemporaryFile();
            Files.copy(is, outFile, StandardCopyOption.REPLACE_EXISTING);
            return new XarFileSource(outFile);
        } catch (final IOException e) {
            throw new IOException("Failed to download package from " + pkgURL + ": " + e.getMessage(), e);
        }
    }
}
