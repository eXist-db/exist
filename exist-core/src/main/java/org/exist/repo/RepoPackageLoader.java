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
import java.util.ArrayList;
import java.util.List;

/**
 * Loads packages from a remote public repository (e.g. https://exist-db.org/exist/apps/public-repo).
 * Used by {@link Deployment} to resolve dependencies during installation, by repo:install-and-deploy,
 * and by the package management API.
 *
 * <p>Each package is downloaded to a temporary file, which must stay available while the
 * installation reads it. Closing the loader returns every file it downloaded, including those of
 * dependencies, to the {@link TemporaryFileManager}; so use it in a try-with-resources that spans
 * the installation.</p>
 */
public final class RepoPackageLoader implements PackageLoader, AutoCloseable {
    private static final Logger LOG = LogManager.getLogger(RepoPackageLoader.class);

    private static final int CONNECT_TIMEOUT = 15_000;
    private static final int READ_TIMEOUT = 15_000;

    private final String repoURL;
    private final List<Path> downloaded = new ArrayList<>();

    /**
     * @param repoURL the base URL of the public package repository
     */
    public RepoPackageLoader(final String repoURL) {
        this.repoURL = repoURL;
    }

    @Override
    public XarSource load(final String name, final Version version) throws IOException {
        final String pkgURL = packageUrl(name, version);
        LOG.info("Retrieving package from {}", pkgURL);
        final HttpURLConnection connection = (HttpURLConnection) URI.create(pkgURL).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "eXist-db Package Manager");
        connection.connect();

        try (final InputStream is = connection.getInputStream()) {
            final Path outFile = TemporaryFileManager.getInstance().getTemporaryFile();
            downloaded.add(outFile);
            Files.copy(is, outFile, StandardCopyOption.REPLACE_EXISTING);
            return new XarFileSource(outFile);
        } catch (final IOException e) {
            throw new IOException("Failed to download package from " + pkgURL + ": " + e.getMessage(), e);
        }
    }

    private String packageUrl(final String name, final Version version) {
        final StringBuilder pkgURL = new StringBuilder(repoURL)
                .append("?name=").append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                .append("&processor=").append(SystemProperties.getInstance().getSystemProperty("product-version", "2.2.0"));
        if (version != null) {
            if (version.getMin() != null) {
                pkgURL.append("&semver-min=").append(version.getMin());
            }
            if (version.getMax() != null) {
                pkgURL.append("&semver-max=").append(version.getMax());
            }
            if (version.getSemVer() != null) {
                pkgURL.append("&semver=").append(version.getSemVer());
            }
            if (version.getVersion() != null) {
                pkgURL.append("&version=").append(URLEncoder.encode(version.getVersion(), StandardCharsets.UTF_8));
            }
        }
        return pkgURL.toString();
    }

    /** Returns every downloaded package file to the {@link TemporaryFileManager}. */
    @Override
    public void close() {
        final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
        for (final Path file : downloaded) {
            temporaryFileManager.returnTemporaryFile(file);
        }
        downloaded.clear();
    }
}
