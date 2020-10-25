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

import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;

import java.io.*;

public class BinarySource extends AbstractSource {

    //TODO replace this with a streaming approach
    private byte[] data;
    private boolean checkEncoding = false;
    private String encoding = "UTF-8";

    public BinarySource(final byte[] data, final boolean checkXQEncoding) {
        this.data = data;
        this.checkEncoding = checkXQEncoding;
    }

    @Override
    public String path() {
        return type();
    }

    @Override
    public String type() {
        return "Binary";
    }

    @Override
    public Object getKey() {
        return data;
    }

    @Override
    public Validity isValid(final DBBroker broker) {
        return Source.Validity.VALID;
    }

    @Override
    public Validity isValid(final Source other) {
        return Source.Validity.VALID;
    }

    @Override
    public Reader getReader() throws IOException {
        checkEncoding();
        return new InputStreamReader(getInputStream(), encoding);
    }

    @Override
    public InputStream getInputStream() {
        return new UnsynchronizedByteArrayInputStream(data);
    }

    @Override
    public String getContent() throws IOException {
        checkEncoding();
        return new String(data, encoding);
    }

    private void checkEncoding() throws IOException {
        if (checkEncoding) {
            try (final InputStream is = getInputStream()) {
                String checkedEnc = guessXQueryEncoding(is);
                if (checkedEnc != null) {
                    encoding = checkedEnc;
                }
            }
        }
    }

    @Override
    public void validate(final Subject subject, final int perm) {
        // TODO protected?
    }
}