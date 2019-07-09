/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2016 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.source;

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
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;


/**
 * A source implementation reading from the path system.
 * 
 * @author wolf
 */
public class FileSource extends AbstractSource {

    private final static Logger LOG = LogManager.getLogger(FileSource.class);

    private final Path path;
    private Charset encoding;
    private final boolean checkEncoding;

    private String filePath;
    private long lastModified;

    /**
     * Defaults to UTF-8 encoding for the path path
     * @param path to file source
     * @param checkXQEncoding enable / disable XQEncoding
     */
    public FileSource(final Path path, final boolean checkXQEncoding) {
        this(path, StandardCharsets.UTF_8, checkXQEncoding);
    }

    public FileSource(final Path path, final Charset encoding, final boolean checkXQEncoding) {
        this.path = path;
        this.encoding = encoding;
        this.checkEncoding = checkXQEncoding;
        this.filePath = path.toAbsolutePath().toString();
        this.lastModified = lastModifiedSafe(path);
    }

    @Override
    public String path() {
        return getFilePath();
    }

    @Override
    public String type() {
        return "File";
    }

    @Override
    public Object getKey() {
        return filePath;
    }
    
    public String getFilePath() {
    	return filePath;
    }

    public Path getPath() {
    	return path;
    }

    @Override
    public Validity isValid(final DBBroker broker) {
        final long currentLastModified = lastModifiedSafe(path);
        if(currentLastModified == -1 || currentLastModified > lastModified) {
            return Validity.INVALID;
        } else {
            return Validity.VALID;
        }
    }

    @Override
    public Validity isValid(final Source other) {
        return Validity.INVALID;
    }

    @Override
    public Reader getReader() throws IOException {
        checkEncoding();
        return Files.newBufferedReader(path, encoding);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public String getContent() throws IOException {
        checkEncoding();
        return new String(Files.readAllBytes(path), encoding);
    }

    @Override
    public Charset getEncoding() throws IOException {
        checkEncoding();
        return encoding;
    }

    private void checkEncoding() throws IOException {
        if (checkEncoding) {
            try(final InputStream is = Files.newInputStream(path)) {
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
        } catch(final IOException ioe) {
            LOG.error(ioe);
            return -1;
        }
    }

    @Override
    public QName isModule() throws IOException {
        try(final InputStream is = Files.newInputStream(path)) {
            return getModuleDecl(is);
        }
    }

    @Override
    public String toString() {
    	return filePath;
    }

	@Override
	public void validate(final Subject subject, final int perm) throws PermissionDeniedException {
		// TODO protected?
	}
}
