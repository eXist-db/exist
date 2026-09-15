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
package org.exist.http.urlrewrite;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import java.io.IOException;

/**
 * Backing store for {@link CachingResponseWrapper}: an in-memory {@link ServletOutputStream} a
 * buffered pipeline step writes its body to, instead of the real response.
 */
class CachingServletOutputStream extends ServletOutputStream {
    private final UnsynchronizedByteArrayOutputStream ostream = new UnsynchronizedByteArrayOutputStream(512);

    protected byte[] getData() {
        return ostream.toByteArray();
    }

    @Override
    public void write(final int b) throws IOException {
        ostream.write(b);
    }

    @Override
    public void write(final byte b[]) throws IOException {
        ostream.write(b);
    }

    @Override
    public void write(final byte b[], final int off, final int len) throws IOException {
        ostream.write(b, off, len);
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(final WriteListener writeListener) {
        throw new UnsupportedOperationException();
    }
}
