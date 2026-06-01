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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Loads packages from a remote public repository (e.g. https://exist-db.org/exist/apps/public-repo).
 * Used by {@link Deployment} to resolve package dependencies during installation,
 * and by {@link PackageService} for direct package installation from a registry.
 */
public record RepoPackageLoader(String repoURL) implements PackageLoader {

    private static final Logger LOG = LogManager.getLogger(RepoPackageLoader.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

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

        // Use the JDK 11+ HttpClient: it honours the standard system-property
        // proxy hooks (http.proxyHost / https.proxyHost / java.net.useSystemProxies)
        // and the platform ProxySelector.getDefault(), so corporate-proxy
        // deployments work out of the box.
        final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(java.net.ProxySelector.getDefault())
                .build();
        final HttpRequest request = HttpRequest.newBuilder(URI.create(pkgURL))
                .GET()
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "eXist-db Package Manager")
                .build();

        final HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading package from " + pkgURL, e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // Drain the body so the underlying connection can be reused / closed.
            try (final InputStream ignored = response.body()) {
                // empty
            }
            throw new IOException("Failed to download package from " + pkgURL
                    + ": HTTP " + response.statusCode());
        }

        try (final InputStream is = response.body()) {
            final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
            final Path outFile = temporaryFileManager.getTemporaryFile();
            Files.copy(is, outFile, StandardCopyOption.REPLACE_EXISTING);
            return new XarFileSource(outFile);
        } catch (final IOException e) {
            throw new IOException("Failed to download package from " + pkgURL + ": " + e.getMessage(), e);
        }
    }
}
