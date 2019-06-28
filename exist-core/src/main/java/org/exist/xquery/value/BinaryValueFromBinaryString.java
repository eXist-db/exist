package org.exist.xquery.value;

import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.xquery.XPathException;

import java.io.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Representation of an XSD binary value e.g. (xs:base64Binary or xs:hexBinary)
 * whose source is backed by a pre-encoded String.
 *
 * Note - BinaryValueFromBinaryString is a special case of BinaryValue
 * where the value is already encoded.
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class BinaryValueFromBinaryString extends BinaryValue {

    private final static Logger LOG = LogManager.getLogger(BinaryValueFromBinaryString.class);

    private final String value;
    private boolean closed = false;

    public BinaryValueFromBinaryString(BinaryValueType binaryValueType, String value) throws XPathException {
        super(null, binaryValueType);
        this.value = binaryValueType.verifyAndFormatString(value);
    }

    @Override
    public BinaryValue convertTo(BinaryValueType binaryValueType) throws XPathException {
        //TODO temporary approach, consider implementing a TranscodingBinaryValueFromBinaryString(BinaryValueFromBinaryString) class
        //that only does the transncoding lazily

        final FastByteArrayOutputStream baos = new FastByteArrayOutputStream();
        FilterOutputStream fos = null;
        try {

            //transcode
            fos = binaryValueType.getEncoder(baos);
            streamBinaryTo(fos);

        } catch (final IOException ioe) {
            throw new XPathException(ioe);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (final IOException ioe) {
                    LOG.error("Unable to close stream: {}", ioe.getMessage(), ioe);
                }
            }

            try {
                baos.close();
            } catch (final IOException ioe) {
                LOG.error("Unable to close stream: {}", ioe.getMessage(), ioe);
            }
        }

        return new BinaryValueFromBinaryString(binaryValueType, baos.toString(UTF_8));
    }

    @Override
    public void streamBinaryTo(OutputStream os) throws IOException {

        //we need to create a safe output stream that cannot be closed
        final OutputStream safeOutputStream = new CloseShieldOutputStream(os);

        //get the decoder
        final FilterOutputStream fos = getBinaryValueType().getDecoder(safeOutputStream);

        //write with the decoder
        final byte data[] = value.getBytes();
        fos.write(data);

        //we do have to close the decoders output stream though
        //to ensure that all bytes have been written, this is
        //particularly nessecary for Apache Commons Codec stream encoders
        try {
            fos.close();
        } catch (final IOException ioe) {
            LOG.error("Unable to close stream: {}", ioe.getMessage(), ioe);
        }
    }

    @Override
    public void streamTo(OutputStream os) throws IOException {
        //write
        final byte data[] = value.getBytes(); //TODO consider a more efficient approach for writing large strings
        os.write(data);
    }

    @Override
    public InputStream getInputStream() {
        //TODO consider a more efficient approach for writting large strings
        final FastByteArrayOutputStream baos = new FastByteArrayOutputStream();
        try {
            streamBinaryTo(baos);
        } catch (final IOException ioe) {
            LOG.error("Unable to get read only buffer: {}", ioe.getMessage(), ioe);
        }
        return baos.toFastByteInputStream();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    @Override
    public void incrementSharedReferences() {
        // we don't need reference counting, as there is nothing to cleanup when all references are returned
    }
}