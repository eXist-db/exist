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

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;

/**
 * A source implementation reading from the path system.
 * 
 * @author wolf
 */
public class FileSource extends AbstractSource {

    private static final Logger LOG = LogManager.getLogger(FileSource.class);

    private final Path path;
    private Charset encoding;
    private final boolean checkEncoding;
    private final long lastModified;

    /**
     * Defaults to UTF-8 encoding for the path path
     * @param path to file source
     * @param checkXQEncoding enable / disable XQEncoding
     */
    public FileSource(final Path path, final boolean checkXQEncoding) {
        this(path, StandardCharsets.UTF_8, checkXQEncoding);
    }

    public FileSource(final Path path, final Charset encoding, final boolean checkXQEncoding) {
        super(hashKey(path.toString()));
        this.path = path;
        this.encoding = encoding;
        this.checkEncoding = checkXQEncoding;
        this.lastModified = lastModifiedSafe(path);
    }

    @Override
    public String path() {
        return path.toAbsolutePath().toString();
    }

    @Override
    public String type() {
        return "File";
    }

    public Path getPath() {
    	return path;
    }

    @Override
    public Validity isValid() {
        final long currentLastModified = lastModifiedSafe(path);
        if (currentLastModified == -1 || currentLastModified > lastModified) {
            return Validity.INVALID;
        } else {
            return Validity.VALID;
        }
    }

    @Override
    public Reader getReader() throws IOException {
        checkEncoding();
        return Files.newBufferedReader(path, encoding);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new BufferedInputStream(Files.newInputStream(path));
    }

    @Override
    public String getContent() throws IOException {
        checkEncoding();
        return Files.readString(path, encoding);
    }

    @Override
    public Charset getEncoding() throws IOException {
        checkEncoding();
        return encoding;
    }

    private void checkEncoding() throws IOException {
        if (checkEncoding) {
            try (final InputStream is = new BufferedInputStream(Files.newInputStream(path))) {
                final String checkedEnc = guessXQueryEncoding(is);
                if (checkedEnc != null) {
                    encoding = Charset.forName(checkedEnc);
                }
            }
        }
    }

    private long lastModifiedSafe(final Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (final IOException ioe) {
            LOG.error(ioe);
            return -1;
        }
    }

    @Override
    public QName isModule() throws IOException {
        try (final InputStream is = new BufferedInputStream(Files.newInputStream(path))) {
            return getModuleDecl(is);
        }
    }

    @Override
    public String toString() {
    	return path();
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }
}
