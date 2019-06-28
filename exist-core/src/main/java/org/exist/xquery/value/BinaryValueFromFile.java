package org.exist.xquery.value;

import org.exist.util.io.ByteBufferAccessor;
import org.exist.util.io.ByteBufferInputStream;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Representation of an XSD binary value e.g. (xs:base64Binary or xs:hexBinary)
 * whose source is backed by a File
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class BinaryValueFromFile extends BinaryValue {

    private final Path file;
    private final FileChannel channel;
    private final MappedByteBuffer buf;
    private final Optional<BiConsumer<Boolean, Path>> closeListener;

    protected BinaryValueFromFile(final BinaryValueManager manager, final BinaryValueType binaryValueType, final Path file, final Optional<BiConsumer<Boolean, Path>> closeListener) throws XPathException {
        super(manager, binaryValueType);
        try {
            this.file = file;
            this.channel = new RandomAccessFile(file.toFile(), "r").getChannel();
            this.buf = channel.map(MapMode.READ_ONLY, 0, channel.size());
            this.closeListener = closeListener;
        } catch (final IOException ioe) {
            throw new XPathException(ioe);
        }
    }

    public static BinaryValueFromFile getInstance(final BinaryValueManager manager, final BinaryValueType binaryValueType, final Path file) throws XPathException {
        final BinaryValueFromFile binaryFile = new BinaryValueFromFile(manager, binaryValueType, file, Optional.empty());
        manager.registerBinaryValueInstance(binaryFile);
        return binaryFile;
    }

    public static BinaryValueFromFile getInstance(final BinaryValueManager manager, final BinaryValueType binaryValueType, final Path file, final BiConsumer<Boolean, Path> closeListener) throws XPathException {
        final BinaryValueFromFile binaryFile = new BinaryValueFromFile(manager, binaryValueType, file, Optional.of(closeListener));
        manager.registerBinaryValueInstance(binaryFile);
        return binaryFile;
    }

    @Override
    public BinaryValue convertTo(final BinaryValueType binaryValueType) throws XPathException {
        final BinaryValueFromFile binaryFile = new BinaryValueFromFile(getManager(), binaryValueType, file, Optional.empty());
        getManager().registerBinaryValueInstance(binaryFile);
        return binaryFile;
    }

    @Override
    public void streamBinaryTo(final OutputStream os) throws IOException {
        if (!channel.isOpen()) {
            throw new IOException("Underlying channel has been closed");
        }

        try {
            final byte data[] = new byte[READ_BUFFER_SIZE];
            while (buf.hasRemaining()) {
                final int remaining = buf.remaining();
                final int readLen = Math.min(remaining, READ_BUFFER_SIZE);

                buf.get(data, 0, readLen);

                os.write(data, 0, readLen);
            }
            os.flush();
        } finally {
            //reset the buf
            buf.position(0);
        }
    }

    @Override
    public boolean isClosed() {
        return !channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        boolean closed = false;
        try {
            channel.close();
            closed = true;
        } finally {
            final boolean finalClosed = closed;
            closeListener.ifPresent(cl -> cl.accept(finalClosed, file));
        }
    }

    @Override
    public InputStream getInputStream() {
        return new ByteBufferInputStream(new ByteBufferAccessor() {

            private ByteBuffer roBuf;

            @Override
            public ByteBuffer getBuffer() {
                if (roBuf == null) {
                    roBuf = buf.asReadOnlyBuffer();
                }
                return roBuf;
            }
        });
    }

    @Override
    public Object toJavaObject() throws XPathException {
        return file;
    }

    @Override
    public void destroy(final XQueryContext context, final Sequence contextSequence) {
        // do not close if this object is part of the contextSequence
        if (contextSequence == this ||
                (contextSequence instanceof ValueSequence && ((ValueSequence) contextSequence).containsValue(this))) {
            return;
        }
        try {
            this.close();
        } catch (final IOException e) {
            // ignore at this point
        }
        context.destroyBinaryValue(this);
    }

    @Override
    public void incrementSharedReferences() {
        // we don't need reference counting, as there is nothing to cleanup when all references are returned
    }
}