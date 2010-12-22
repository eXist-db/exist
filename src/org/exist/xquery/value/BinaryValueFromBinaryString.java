package org.exist.xquery.value;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import org.apache.log4j.Logger;

/**
 * BinaryValueFromBinaryString is a special case of BinaryValue
 * where the value is already encoded
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
        OutputStream safeOutputStream = makeSafeOutputStream(os);
        
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
        byte data[] = value.getBytes();
        os.write(data);
    }
    
    
    @Override
    public ByteBuffer getReadOnlyBuffer() {
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            streamBinaryTo(baos);
        } catch(IOException ioe) {
            LOG.error("Unable to get read only buffer: " + ioe.getMessage(), ioe);
        }
        byte data[] = baos.toByteArray();
        ByteBuffer buf = ByteBuffer.allocate(data.length);
        buf.put(data);
        return buf;
    }

    @Override
    public void close() throws IOException {
    }
}