package org.exist.xquery.value;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import org.exist.util.io.ByteBufferAccessor;
import org.exist.util.io.ByteBufferInputStream;
import org.exist.xquery.XPathException;

/**
 * Representation of an XSD binary value e.g. (xs:base64Binary or xs:hexBinary)
 * whose source is backed by a File
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class BinaryValueFromFile extends BinaryValue {

    private FileChannel channel;
    private MappedByteBuffer buf;

    protected BinaryValueFromFile(BinaryValueType binaryValueType, File f) throws XPathException {
        super(binaryValueType);
        try {
            channel = new RandomAccessFile(f, "r").getChannel();
            buf = channel.map(MapMode.READ_ONLY, 0, channel.size());
        } catch(IOException ioe) {
            throw new XPathException(ioe.getMessage(), ioe);
        }
    }

    public static BinaryValueFromFile getInstance(BinaryValueManager manager, BinaryValueType binaryValueType, File f) throws XPathException {
        BinaryValueFromFile binaryFile = new BinaryValueFromFile(binaryValueType, f);
        manager.registerBinaryValueInstance(binaryFile);
        return binaryFile;
    }

    @Override
    public void streamBinaryTo(OutputStream os) throws IOException {
        if(!channel.isOpen()) {
            throw new IOException("Underlying channel has been closed");
        }

        try {
            byte data[] = new byte[READ_BUFFER_SIZE];
            while(buf.hasRemaining()) {
                int remaining = buf.remaining();
                if(remaining < READ_BUFFER_SIZE) {
                    data = new byte[remaining];
                }

                buf.get(data);

                os.write(data, 0, data.length);
            }
            os.flush();
        } finally {
            //reset the buf
            buf.position(0);
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteBufferInputStream(new ByteBufferAccessor() {

            private ByteBuffer roBuf;

            @Override
            public ByteBuffer getBuffer() {
                if(roBuf == null) {
                    roBuf = buf.asReadOnlyBuffer();
                }
                return roBuf;
            }
        });
    }
}