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
package org.exist.util;

import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.io.CachingFilterInputStream;
import org.exist.util.io.InputStreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class CachingFilterInputStreamInputSource extends EXistInputSource {
    private static final Logger LOG = LogManager.getLogger(CachingFilterInputStreamInputSource.class);

    private CachingFilterInputStream cachingFilterInputStream;
    private long length = -1;

    public CachingFilterInputStreamInputSource(final CachingFilterInputStream cachingFilterInputStream) {
        super();
        this.cachingFilterInputStream = cachingFilterInputStream;
    }

    @Override
    public Reader getCharacterStream() {
        assertOpen();

        return null;
    }

    /**
     * This method now does nothing, so collateral
     * effects from superclass with this one are avoided
     *
     * @throws IllegalStateException if the InputSource was previously closed
     */
    @Override
    public void setCharacterStream(final Reader r) {
        assertOpen();
        throw new IllegalStateException("CachingFilterInputStreamInputSource is immutable");
    }

    @Override
    public InputStream getByteStream() {
        assertOpen();

        try {
            return new CachingFilterInputStream(cachingFilterInputStream);
        } catch (final InstantiationException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * @see EXistInputSource#getByteStreamLength()
     *
     * @throws IllegalStateException if the InputSource was previously closed
     */
    @Override
    public long getByteStreamLength() {
        assertOpen();
        if (length == -1) {
            try (final CachingFilterInputStream is = new CachingFilterInputStream(cachingFilterInputStream);
                 final CountingOutputStream cos = new CountingOutputStream(NullOutputStream.NULL_OUTPUT_STREAM)) {
                InputStreamUtil.copy(is, cos);
                length = cos.getByteCount();
            } catch (final InstantiationException | IOException e) {
                LOG.error(e.getMessage(), e);
            }
        }
        return -1;
    }

    /**
     * Set a byte stream input.
     *
     * @param is the input stream.
     *
     * @throws IllegalStateException this class is immutable!
     */
    @Override
    public void setByteStream(final InputStream is) {
        assertOpen();
        throw new IllegalStateException("CachingFilterInputStreamInputSource is immutable");
    }

    /**
     * This method now does nothing, so collateral
     * effects from superclass with this one are avoided
     *
     * @throws IllegalStateException if the InputSource was previously closed
     */
    @Override
    public void setSystemId(final String systemId) {
        assertOpen();
        // Nothing, so collateral effects are avoided!
    }

    /**
     * @see EXistInputSource#getSymbolicPath()
     *
     * @throws IllegalStateException if the InputSource was previously closed
     */
    @Override
    public String getSymbolicPath() {
        assertOpen();
        return null;
    }
}
