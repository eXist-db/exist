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
package org.exist.source;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A source implementation reading from an URL.
 *
 * @author wolf
 */
public class URLSource extends AbstractSource {

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bcharset=\\s*\"?([^\\s;\"]*)");

    private static final Logger LOG = LogManager.getLogger(URLSource.class);

    private static final String ACCEPT_HEADER = "application/xml,application/xhtml+xml;q=0.9,*/*;q=0.2";

    protected final URL url;
    private URLConnection connection = null;
    private long lastModified = 0;
    private int responseCode = HttpURLConnection.HTTP_OK;

    public URLSource(final URL url) {
        super(hashKey(url.toString()));
        this.url = url;
    }

    @Override
    public String path() {
        final String protocol = url.getProtocol();
        final String host = url.getHost();
        if ("file".equals(protocol) && (host == null || host.isEmpty() || "localhost".equals(host) || "127.0.0.1".equals(host))) {
            return url.getFile();
        }
        return url.toExternalForm();
    }

    @Override
    public String type() {
        final String protocol = url.getProtocol();
        final String host = url.getHost();
        if ("file".equals(protocol) && (host == null || host.isEmpty() || "localhost".equals(host) || "127.0.0.1".equals(host))) {
            return "File";
        }
        return "URL";
    }

    public URL getURL() {
        return url;
    }

    private long getLastModification() {
        try {
            if (connection == null) {
                connection = url.openConnection();
            }
            return connection.getLastModified();
        } catch (final IOException e) {
            LOG.warn("URL '{}' could not be opened: {}", url, e.getMessage());
            return 0;
        }
    }

    @Override
    public Validity isValid() {
        final long modified = getLastModification();
        final Validity validity;
        if (modified == 0 || modified > lastModified) {
            validity = Validity.INVALID;
        } else {
            validity = Validity.VALID;
        }
        lastModified = modified;
        return validity;
    }

    @Override
    public Charset getEncoding() throws IOException {
        if (connection == null) {
            connection = url.openConnection();
            lastModified = connection.getLastModified();
        }
        final String contentType = connection.getContentType();
        if (contentType != null) {
            final Matcher matcher = URL_PATTERN.matcher(contentType);
            if (matcher.find()) {
                try {
                    return Charset.forName(matcher.group(1).trim().toUpperCase());
                } catch (final IllegalArgumentException e) {
                    // unknown or illegal charset
                    return null;
                }
            } else if (contentType.startsWith("text/")) {
                return StandardCharsets.ISO_8859_1;
            }
        }
        return null;
    }

    @Override
    public Reader getReader() throws IOException {
        try {
            if (connection == null) {
                connection = url.openConnection();
                if (connection instanceof HttpURLConnection) {
                    ((HttpURLConnection) connection).setRequestProperty("Accept", ACCEPT_HEADER);
                }

                lastModified = connection.getLastModified();

                if (connection instanceof HttpURLConnection) {
                    responseCode = ((HttpURLConnection) connection).getResponseCode();
                }
            }
            Reader reader = null;
            if (responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
                reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
            }
            connection = null;
            return reader;
        } catch (final IOException e) {
            LOG.warn("URL '{}' could not be opened: {}", url, e.getMessage());
            throw e;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            if (connection == null) {
                connection = url.openConnection();
                if (connection instanceof HttpURLConnection) {
                    ((HttpURLConnection) connection).setRequestProperty("Accept", ACCEPT_HEADER);
                }

                lastModified = connection.getLastModified();

                if (connection instanceof HttpURLConnection) {
                    responseCode = ((HttpURLConnection) connection).getResponseCode();
                }
            }
            InputStream is = null;
            if (responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
                is = connection.getInputStream();
            }
            connection = null;
            return is;

        } catch (final ConnectException e) {
            LOG.warn("Unable to connect to URL '{}': {}", url, e.getMessage());
            throw e;

        } catch (final IOException e) {
            LOG.warn("URL '{}' could not be opened: {}", url, e.getMessage());
            throw e;
        }
    }

    @Override
    public String getContent() {
        try {
            if (connection == null) {
                connection = url.openConnection();
                if (connection instanceof HttpURLConnection) {
                    ((HttpURLConnection) connection).setRequestProperty("Accept", ACCEPT_HEADER);
                }

                lastModified = connection.getLastModified();

                if (connection instanceof HttpURLConnection) {
                    responseCode = ((HttpURLConnection) connection).getResponseCode();
                }
            }
            final String content = connection.getContent().toString();
            connection = null;
            return content;
        } catch (final IOException e) {
            LOG.warn("URL '{}' could not be opened: {}", url, e.getMessage());
            return null;
        }
    }

    public int getResponseCode() {
        return responseCode;
    }

    @Override
    public String toString() {
        if (url == null) {
            return "[not set]";
        }
        return url.toString();
    }

    @Override
    public QName isModule() throws IOException {
        try (final InputStream is = getInputStream()) {
            return getModuleDecl(is);
        }
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }
}
