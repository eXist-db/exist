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
package org.exist.management.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the resolution paths that {@code system:get-jmx-token()} relies on but can't itself
 * exercise via XQSuite: a live test instance always has the {@code DiskUsage} MBean registered
 * (see {@code AgentFactory#initDBInstance}), so "JMX unavailable" can only be simulated by
 * substituting a {@link JMXtoXML} client directly, at this unit level.
 */
public class JMXTokenProviderTest {

    @TempDir
    public File temporaryFolder;

    private static JMXtoXML clientReturning(final String dataDir) {
        return new JMXtoXML() {
            @Override
            public String getDataDir() {
                return dataDir;
            }
        };
    }

    private static JMXtoXML clientThrowing(final RuntimeException e) {
        return new JMXtoXML() {
            @Override
            public String getDataDir() {
                throw e;
            }
        };
    }

    @Test
    void getDataDirUsesMBeanValueWhenAvailable() throws IOException {
        final Path dataDir = newFolder(temporaryFolder, "mbean-data-dir").toPath();
        final JMXTokenProvider provider = new JMXTokenProvider(clientReturning(dataDir.toString()));

        assertEquals(Optional.of(dataDir), provider.getDataDir());
    }

    @Test
    void getDataDirFallsBackWhenMBeanReturnsNull() throws IOException {
        final Path fallback = newFolder(temporaryFolder, "fallback-data-dir").toPath();
        final JMXTokenProvider provider = new JMXTokenProvider(clientReturning(null), fallback);

        assertEquals(Optional.of(fallback), provider.getDataDir());
    }

    @Test
    void getDataDirIsEmptyWhenMBeanReturnsNullAndNoFallback() {
        final JMXTokenProvider provider = new JMXTokenProvider(clientReturning(null));

        assertEquals(Optional.empty(), provider.getDataDir());
    }

    @Test
    void getDataDirFallsBackWhenMBeanLookupThrows() throws IOException {
        final Path fallback = newFolder(temporaryFolder, "fallback-data-dir").toPath();
        final JMXtoXML client = clientThrowing(new NullPointerException("no MBean connection"));
        final JMXTokenProvider provider = new JMXTokenProvider(client, fallback);

        assertEquals(Optional.of(fallback), provider.getDataDir());
    }

    @Test
    void getDataDirIsEmptyWhenMBeanLookupThrowsAndNoFallback() {
        final JMXtoXML client = clientThrowing(new NullPointerException("no MBean connection"));
        final JMXTokenProvider provider = new JMXTokenProvider(client);

        assertEquals(Optional.empty(), provider.getDataDir());
    }

    @Test
    void getTokenIsEmptyWhenDataDirCannotBeResolved() {
        // Mirrors system:get-jmx-token()'s construction: no fallback, MBean unavailable.
        final JMXTokenProvider provider = new JMXTokenProvider(clientReturning(null));

        assertEquals(Optional.empty(), provider.getToken());
    }

    @Test
    void getTokenCreatesAndPersistsNewTokenWhenFileAbsent() throws IOException {
        final Path dataDir = newFolder(temporaryFolder, "new-token-dir").toPath();
        final JMXTokenProvider provider = new JMXTokenProvider(clientReturning(dataDir.toString()));

        final Optional<String> token = provider.getToken();

        assertTrue(token.isPresent());
        assertFalse(token.get().isBlank());

        final Path tokenFile = dataDir.resolve("jmxservlet.token");
        assertTrue(Files.exists(tokenFile));

        final Properties persisted = new Properties();
        try (final var is = Files.newInputStream(tokenFile)) {
            persisted.load(is);
        }
        assertEquals(token.get(), persisted.getProperty("token"));

        final String raw = Files.readString(tokenFile);
        assertThat(raw, containsString("/exist/status?token=<token>"));
        assertThat(raw, containsString("system:get-jmx-token()"));
    }

    @Test
    void getTokenReadsExistingTokenRatherThanCreatingNew() throws IOException {
        final Path dataDir = newFolder(temporaryFolder, "existing-token-dir").toPath();
        final Path tokenFile = dataDir.resolve("jmxservlet.token");
        final Properties existing = new Properties();
        existing.setProperty("token", "existing-token-value");
        try (final var os = Files.newOutputStream(tokenFile)) {
            existing.store(os, null);
        }

        final JMXTokenProvider provider = new JMXTokenProvider(clientReturning(dataDir.toString()));

        assertThat(provider.getToken(), equalTo(Optional.of("existing-token-value")));
    }

    @Test
    void getTokenRegeneratesTokenWhenExistingFileHasNoTokenProperty() throws IOException {
        final Path dataDir = newFolder(temporaryFolder, "corrupt-token-dir").toPath();
        final Path tokenFile = dataDir.resolve("jmxservlet.token");
        final Properties withoutTokenKey = new Properties();
        withoutTokenKey.setProperty("not-the-token-key", "irrelevant");
        try (final var os = Files.newOutputStream(tokenFile)) {
            withoutTokenKey.store(os, null);
        }

        final JMXTokenProvider provider = new JMXTokenProvider(clientReturning(dataDir.toString()));
        final Optional<String> token = provider.getToken();

        assertTrue(token.isPresent());
        assertFalse(token.get().isBlank());

        final Properties persisted = new Properties();
        try (final var is = Files.newInputStream(tokenFile)) {
            persisted.load(is);
        }
        assertEquals(token.get(), persisted.getProperty("token"));
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            if (result.isDirectory()) {
                // repeated or parameterized calls can share a root and reuse subDirs, so fall back to a
                // uniquely-suffixed sibling instead of colliding with the earlier directory
                result = Files.createTempDirectory(root.toPath(), subFolder + "-").toFile();
            } else {
                throw new IOException("Couldn't create folders " + root);
            }
        }
        return result;
    }
}
