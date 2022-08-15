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
package org.exist.xquery.value;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.io.CachingFilterInputStream;
import org.exist.util.io.FilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCacheFactory;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Representation of an XSD binary value e.g. (xs:base64Binary or xs:hexBinary)
 * whose source is backed by an InputStream
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class BinaryValueFromInputStream extends BinaryValue {

    private final static Logger LOG = LogManager.getLogger(BinaryValueFromInputStream.class);

    private final CachingFilterInputStream is;
    private final FilterInputStreamCache cache;

    protected BinaryValueFromInputStream(final BinaryValueManager manager, final BinaryValueType binaryValueType, final InputStream is) throws XPathException {
        this(null, manager, binaryValueType, is);
    }

    protected BinaryValueFromInputStream(final Expression expression, final BinaryValueManager manager, final BinaryValueType binaryValueType, final InputStream is) throws XPathException {
        super(expression, manager, binaryValueType);

        try {

            this.cache = FilterInputStreamCacheFactory.getCacheInstance(manager::getCacheClass, is);
            this.is = new CachingFilterInputStream(cache);

        } catch (final IOException ioe) {
            throw new XPathException(getExpression(), ioe);
        }

        //mark the start of the stream so that we can re-read again as required
        this.is.mark(Integer.MAX_VALUE);
    }

    public static BinaryValueFromInputStream getInstance(final BinaryValueManager manager, final BinaryValueType binaryValueType, final InputStream is) throws XPathException {
        return getInstance(manager, binaryValueType, is, null);
    }

    public static BinaryValueFromInputStream getInstance(final BinaryValueManager manager, final BinaryValueType binaryValueType, final InputStream is, final Expression expression) throws XPathException {
        final BinaryValueFromInputStream binaryInputStream = new BinaryValueFromInputStream(expression, manager, binaryValueType, is);
        manager.registerBinaryValueInstance(binaryInputStream);
        return binaryInputStream;
    }

    @Override
    public BinaryValue convertTo(final BinaryValueType binaryValueType) throws XPathException {
        try {
            final BinaryValueFromInputStream binaryInputStream = new BinaryValueFromInputStream(getExpression(), getManager(), binaryValueType, new CachingFilterInputStream(is));
            getManager().registerBinaryValueInstance(binaryInputStream);
            return binaryInputStream;
        } catch (InstantiationException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public void streamBinaryTo(final OutputStream os) throws IOException {
        try {
            int read = -1;
            final byte data[] = new byte[READ_BUFFER_SIZE];
            while ((read = is.read(data)) > -1) {
                os.write(data, 0, read);
            }
        } finally {
            //reset the buf
            try {
                is.reset();
            } catch (final IOException ioe) {
                LOG.error("Unable to reset stream: {}", ioe.getMessage(), ioe);
            }
        }
    }

    @Override
    public InputStream getInputStream() {
        try {
            return new CachingFilterInputStream(is);
        } catch (InstantiationException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public boolean isClosed() {
        return is.isClosed();
    }

    @Override
    public void close() throws IOException {
        is.close();
    }

    @Override
    public void destroy(final XQueryContext context, @Nullable final Sequence contextSequence) {
        // do not close if this object is part of the contextSequence
        if (contextSequence != null && (contextSequence == this || contextSequence.containsReference(this))) {
            return;
        }
        LOG.debug("Closing input stream");
        try {
            this.close();
        } catch (final IOException e) {
            LOG.warn("Error during cleanup of binary value: {}", e.getMessage(), e);
        }
        context.destroyBinaryValue(this);
    }

    @Override
    public void incrementSharedReferences() {
        cache.incrementSharedReferences();
    }
}
