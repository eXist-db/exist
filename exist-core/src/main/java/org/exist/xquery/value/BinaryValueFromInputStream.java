package org.exist.xquery.value;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.io.CachingFilterInputStream;
import org.exist.util.io.FilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCacheFactory;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

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
        super(manager, binaryValueType);

        try {

            this.cache = FilterInputStreamCacheFactory.getCacheInstance(manager::getCacheClass, is);
            this.is = new CachingFilterInputStream(cache);

        } catch (final IOException ioe) {
            throw new XPathException(ioe);
        }

        //mark the start of the stream so that we can re-read again as required
        this.is.mark(Integer.MAX_VALUE);
    }

    public static BinaryValueFromInputStream getInstance(final BinaryValueManager manager, final BinaryValueType binaryValueType, final InputStream is) throws XPathException {
        final BinaryValueFromInputStream binaryInputStream = new BinaryValueFromInputStream(manager, binaryValueType, is);
        manager.registerBinaryValueInstance(binaryInputStream);
        return binaryInputStream;
    }

    @Override
    public BinaryValue convertTo(final BinaryValueType binaryValueType) throws XPathException {
        try {
            final BinaryValueFromInputStream binaryInputStream = new BinaryValueFromInputStream(getManager(), binaryValueType, new CachingFilterInputStream(is));
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
    public void destroy(XQueryContext context, final Sequence contextSequence) {
        // do not close if this object is part of the contextSequence
        if (contextSequence == this
                || (contextSequence instanceof ValueSequence && ((ValueSequence) contextSequence).containsValue(this))) {
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
