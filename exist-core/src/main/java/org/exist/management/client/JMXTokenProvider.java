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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.UUIDGenerator;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Canonical resolution of {@link JMXServlet}'s shared secret token, i.e. the value the servlet
 * accepts via its {@code token} request parameter as an alternative to a request originating
 * from localhost (see {@code JMXServlet#hasSecretToken}).
 * <p>
 * The token is persisted in a {@code jmxservlet.token} properties file inside the data
 * directory reported by the {@code org.exist.management.exist:type=DiskUsage} MBean's
 * {@code DataDirectory} attribute, falling back to a caller-supplied directory (e.g.
 * {@code JMXServlet}'s {@code WEB-INF/data}) if that attribute is unavailable.
 * <p>
 * Both {@code JMXServlet} and the {@code system:get-jmx-token()} XQuery function delegate to
 * this class rather than each re-deriving the token/data-dir resolution independently.
 *
 * @author eXist-db authors
 */
public class JMXTokenProvider {

    private static final Logger LOG = LogManager.getLogger(JMXTokenProvider.class);

    private static final String TOKEN_KEY = "token";
    private static final String TOKEN_FILE = "jmxservlet.token";
    private static final String TOKEN_FILE_COMMENT = """
            JMXservlet token
            Use: /exist/status?token=<token>
            Obtain: system:get-jmx-token()""";

    private final JMXtoXML client;

    @Nullable
    private final Path fallbackDataDir;

    /**
     * @param client a connected {@link JMXtoXML} client used to resolve the data directory
     */
    public JMXTokenProvider(final JMXtoXML client) {
        this(client, null);
    }

    /**
     * @param client          a connected {@link JMXtoXML} client used to resolve the data directory
     * @param fallbackDataDir the data directory to use if the client cannot report one, or null
     *                        if there is no fallback available
     */
    public JMXTokenProvider(final JMXtoXML client, @Nullable final Path fallbackDataDir) {
        this.client = client;
        this.fallbackDataDir = fallbackDataDir;
    }

    /**
     * Resolve the directory the token file lives in, the same way {@code JMXServlet} does: the
     * {@code DiskUsage} MBean's {@code DataDirectory} attribute, or the caller-supplied fallback
     * if that attribute is unavailable.
     *
     * @return the resolved data directory, or empty if neither source yielded one
     */
    public Optional<Path> getDataDir() {
        final String jmxDataDir;
        try {
            jmxDataDir = client.getDataDir();
        } catch (final RuntimeException e) {
            LOG.error("Unable to determine data directory from JMX: {}", e.getMessage(), e);
            return Optional.ofNullable(fallbackDataDir).map(Path::normalize);
        }

        final Path dataDir;
        if (jmxDataDir != null) {
            dataDir = Path.of(jmxDataDir).normalize();
        } else if (fallbackDataDir != null) {
            dataDir = fallbackDataDir.normalize();
        } else {
            return Optional.empty();
        }

        if (!Files.isDirectory(dataDir) || !Files.isWritable(dataDir)) {
            LOG.error("Cannot access data directory {}", dataDir);
        }

        return Optional.of(dataDir);
    }

    /**
     * Get the token, reading it from (or creating and persisting it in, if not yet present) the
     * {@code jmxservlet.token} file in the resolved data directory. Data is read for each call so
     * the file can be updated at run-time.
     *
     * @return the token, or empty if the data directory could not be resolved
     */
    public Optional<String> getToken() {
        return getDataDir().map(this::readOrCreateToken);
    }

    private String readOrCreateToken(final Path dataDir) {
        final Path tokenFile = dataDir.resolve(TOKEN_FILE);

        final Properties props = new Properties();
        String token = null;

        if (Files.exists(tokenFile)) {
            try (final InputStream is = Files.newInputStream(tokenFile)) {
                props.load(is);
                token = props.getProperty(TOKEN_KEY);
            } catch (final IOException e) {
                LOG.error(e.getMessage());
            }
        }

        if (token == null) {
            // Create random token
            token = UUIDGenerator.getUUIDversion4();

            // Set value to properties
            props.setProperty(TOKEN_KEY, token);

            // Write data to file
            try (final OutputStream os = Files.newOutputStream(tokenFile)) {
                props.store(os, TOKEN_FILE_COMMENT);
            } catch (final IOException e) {
                LOG.error(e.getMessage());
            }

            LOG.debug("Token written to file {}", tokenFile.toAbsolutePath());
        }

        return token;
    }
}
