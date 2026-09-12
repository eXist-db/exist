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

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;

import java.io.IOException;
import java.util.Objects;

/**
 * Backing store for {@link ControllerRequestWrapper#setData}: lets a {@code <exist:view>} step
 * read the previous pipeline step's output as its own request body.
 */
class CachingServletInputStream extends ServletInputStream {
    private final UnsynchronizedByteArrayInputStream istream;

    public CachingServletInputStream(final byte[] data) {
        istream = new UnsynchronizedByteArrayInputStream(Objects.requireNonNullElseGet(data, () -> new byte[0]));
    }

    @Override
    public int read() throws IOException {
        return istream.read();
    }

    @Override
    public int read(final byte b[]) throws IOException {
        return istream.read(b);
    }

    @Override
    public int read(final byte b[], final int off, final int len) throws IOException {
        return istream.read(b, off, len);
    }

    @Override
    public int available() {
        return istream.available();
    }

    @Override
    public boolean isFinished() {
        return istream.available() == 0;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(final ReadListener readListener) {
        throw new UnsupportedOperationException();
    }
}
