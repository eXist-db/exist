package org.exist.xquery.value;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public class BinaryValueFromString extends BinaryValue {

    private final String value;

    public BinaryValueFromString(BinaryValueType binaryValueType, String value) {
        super(binaryValueType);
        this.value = value;
    }

    @Override
    public void streamBinaryTo(OutputStream os) throws IOException {
        byte data[] = value.getBytes();
        os.write(data);
    }

    @Override
    public ByteBuffer getReadOnlyBuffer() {
        byte data[] = value.getBytes();
        ByteBuffer buf = ByteBuffer.allocate(data.length);
        buf.put(data);
        return buf;
    }

    @Override
    public void close() throws IOException {
    }
}