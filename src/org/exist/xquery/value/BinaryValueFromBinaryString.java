package org.exist.xquery.value;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.log4j.Logger;

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

    private final static Logger LOG = Logger.getLogger(BinaryValueFromBinaryString.class);
    
    private final String value;

    public BinaryValueFromBinaryString(BinaryValueType binaryValueType, String value) {
        super(binaryValueType);
        this.value = value;
    }

    @Override
    public void streamBinaryTo(OutputStream os) throws IOException {
        
        //we need to create a safe output stream that cannot be closed
        OutputStream safeOutputStream = new CloseShieldOutputStream(os);
        
        //get the decoder
        FilterOutputStream fos = getBinaryValueType().getDecoder(safeOutputStream);
        
        //write with the decoder
        byte data[] = value.getBytes();
        fos.write(data);
        
        //we do have to close the decoders output stream though
        //to ensure that all bytes have been written, this is
        //particularly nessecary for Apache Commons Codec stream encoders
        try {
            fos.close();
        } catch(IOException ioe) {
            LOG.error("Unable to close stream: " + ioe.getMessage(), ioe);
        }
    }

    @Override
    public void streamTo(OutputStream os) throws IOException {
        //write
        byte data[] = value.getBytes(); //TODO consider a more efficient approach for writting large strings
        os.write(data);
    }

    @Override
    public InputStream getInputStream() {

        //TODO consider a more efficient approach for writting large strings
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            streamBinaryTo(baos);
        } catch(IOException ioe) {
            LOG.error("Unable to get read only buffer: " + ioe.getMessage(), ioe);
        }

        return new InputStream() {
            int offset = 0;
            final byte data[] = baos.toByteArray();

            @Override
            public int read() throws IOException {

                if(offset >= data.length) {
                    return -1;
                }
                return data[offset++];
            }
        };
    }

    @Override
    public void close() throws IOException {
    }
}