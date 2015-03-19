package org.exist.xquery.value;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.XPathException;

import java.io.*;

/**
 * Representation of an XSD binary value e.g. (xs:base64Binary or xs:hexBinary)
 * whose source is backed by a pre-encoded String.
 *
 * Note - BinaryValueFromBinaryString is a special case of BinaryValue
 * where the value is already encoded.
 * 
 * @author Adam Retter <adam@existsolutions.com>
 */
public class BinaryValueFromBinaryString extends BinaryValue {

    private final static Logger LOG = LogManager.getLogger(BinaryValueFromBinaryString.class);
    
    private final String value;

    public BinaryValueFromBinaryString(BinaryValueType binaryValueType, String value) throws XPathException {
        super(null, binaryValueType);
        this.value = binaryValueType.verifyAndFormatString(value);
    }

    @Override
    public BinaryValue convertTo(BinaryValueType binaryValueType) throws XPathException {
        //TODO temporary approach, consider implementing a TranscodingBinaryValueFromBinaryString(BinaryValueFromBinaryString) class
        //that only does the transncoding lazily

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FilterOutputStream fos = null;
        try {

            //transcode
            fos = binaryValueType.getEncoder(baos);
            streamBinaryTo(fos);

        } catch(final IOException ioe) {
            throw new XPathException(ioe);
        } finally {
            if(fos != null) {
                try {
                    fos.close();
                } catch(final IOException ioe) {
                    LOG.error("Unable to close stream: " + ioe.getMessage(), ioe);
                }
            }

            try {
                baos.close();
            } catch(final IOException ioe) {
                LOG.error("Unable to close stream: " + ioe.getMessage(), ioe);
            }
        }

        return new BinaryValueFromBinaryString(binaryValueType, new String(baos.toByteArray()));
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
        } catch(final IOException ioe) {
            LOG.error("Unable to close stream: " + ioe.getMessage(), ioe);
        }
    }

    @Override
    public void streamTo(OutputStream os) throws IOException {
        //write
        final byte data[] = value.getBytes(); //TODO consider a more efficient approach for writting large strings
        os.write(data);
    }

    @Override
    public InputStream getInputStream() {

        //TODO consider a more efficient approach for writting large strings
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            streamBinaryTo(baos);
        } catch(final IOException ioe) {
            LOG.error("Unable to get read only buffer: " + ioe.getMessage(), ioe);
        }

        return new ByteArrayInputStream(baos.toByteArray());
    }

    @Override
    public void close() throws IOException {
    }
}