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

import com.ibm.icu.text.Collator;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import java.io.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public abstract class BinaryValue extends AtomicValue implements Closeable {

    private final static Logger LOG = LogManager.getLogger(BinaryValue.class);

    protected final int READ_BUFFER_SIZE = 16 * 1024; //16kb

    private final BinaryValueManager binaryValueManager;
    private final BinaryValueType binaryValueType;

    protected BinaryValue(final BinaryValueManager binaryValueManager, final BinaryValueType binaryValueType) {
        this(null, binaryValueManager, binaryValueType);
    }

    protected BinaryValue(final Expression expression, BinaryValueManager binaryValueManager, BinaryValueType binaryValueType) {
        super(expression);
        this.binaryValueManager = binaryValueManager;
        this.binaryValueType = binaryValueType;
    }

    protected final BinaryValueManager getManager() {
        return binaryValueManager;
    }

    protected BinaryValueType getBinaryValueType() {
        return binaryValueType;
    }

    @Override
    public int getType() {
        return getBinaryValueType().getXQueryType();
    }

    @Override
    public boolean compareTo(Collator collator, Comparison operator, AtomicValue other) throws XPathException {
        if (other.getType() == Type.HEX_BINARY || other.getType() == Type.BASE64_BINARY) {
            final int value = compareTo((BinaryValue) other);
            return switch (operator) {
                case EQ -> value == 0;
                case NEQ -> value != 0;
                case GT -> value > 0;
                case GTEQ -> value >= 0;
                case LT -> value < 0;
                case LTEQ -> value <= 0;
                default ->
                        throw new XPathException(getExpression(), "Type error: cannot apply operator to numeric value");
            };
        } else {
            throw new XPathException(getExpression(), "Cannot compare value of type xs:hexBinary with " + Type.getTypeName(other.getType()));
        }
    }

    @Override
    public int compareTo(Collator collator, AtomicValue other) throws XPathException {
        if (other.getType() == Type.HEX_BINARY || other.getType() == Type.BASE64_BINARY) {
            return compareTo((BinaryValue) other);
        } else {
            throw new XPathException(getExpression(), "Cannot compare value of type xs:hexBinary with " + Type.getTypeName(other.getType()));
        }
    }

    private int compareTo(BinaryValue otherValue) {

        final InputStream is = getInputStream();
        final InputStream otherIs = otherValue.getInputStream();

        if (is == null && otherIs == null) {
            return 0;
        } else if (is == null) {
            return -1;
        } else if (otherIs == null) {
            return 1;
        } else if (is == otherIs) {
            return 0;
        } else {
            int read;
            int otherRead;
            do {

                try {
                    read = is.read();
                } catch (final IOException ioe) {
                    return -1;
                }

                try {
                    otherRead = otherIs.read();
                } catch (final IOException ioe) {
                    return 1;
                }

                final int readComparison = Integer.compare(read, otherRead);
                if (readComparison != 0) {
                    return readComparison;
                }
            } while (read != -1 && otherRead != -1);
            return 0;
        }
    }

    @Override
    public <T> T toJavaObject(Class<T> target) throws XPathException {
        if (target.isAssignableFrom(getClass())) {
            return (T) this;
        }

        if (target == byte[].class) {
            try (final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {
                streamBinaryTo(baos);
                return (T) baos.toByteArray();
            } catch (final IOException ioe) {
                LOG.error("Unable to Stream BinaryValue to byte[]: {}", ioe.getMessage(), ioe);
                throw new XPathException(getExpression(), "Unable to Stream BinaryValue to byte[]: " + ioe.getMessage(), ioe);
            }
        }

        throw new XPathException(getExpression(), "Cannot convert value of type " + Type.getTypeName(getType()) + " to Java object of type " + target.getName());
    }

    /**
     * Return the underlying Java object for this binary value. Might be a File or byte[].
     *
     * @param <T> either File or byte[]
     * @return the value converted to a corresponding java object
     * @throws XPathException in case of dynamic error
     */
    public <T> T toJavaObject() throws XPathException {
        return (T) toJavaObject(byte[].class);
    }

    @Override
    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "Cannot compare values of type " + Type.getTypeName(getType()));
    }

    @Override
    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), "Cannot compare values of type " + Type.getTypeName(getType()));
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {

        final AtomicValue result;

        if (requiredType == getType() || requiredType == Type.ITEM || requiredType == Type.ANY_ATOMIC_TYPE) {
            result = this;
        } else {
            result = switch (requiredType) {
                case Type.BASE64_BINARY -> convertTo(new Base64BinaryValueType());
                case Type.HEX_BINARY -> convertTo(new HexBinaryValueType());
                case Type.UNTYPED_ATOMIC ->
                    //TODO still needed? Added trim() since it looks like a new line character is added
                        new UntypedAtomicValue(getExpression(), getStringValue());
                case Type.STRING ->
                    //TODO still needed? Added trim() since it looks like a new line character is added
                        new StringValue(getExpression(), getStringValue());
                default ->
                        throw new XPathException(getExpression(), ErrorCodes.FORG0001, "cannot convert " + Type.getTypeName(getType()) + " to " + Type.getTypeName(requiredType));
            };
        }
        return result;
    }

    public abstract BinaryValue convertTo(BinaryValueType binaryValueType) throws XPathException;

    @Override
    public int conversionPreference(Class<?> javaClass) {
        if (javaClass.isArray() && javaClass.isInstance(Byte.class)) {
            return 0;
        }

        return Integer.MAX_VALUE;
    }

    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException(getExpression(), "FORG0006: value of type " + Type.getTypeName(getType()) + " has no boolean value.");
    }

    //TODO ideally this should be moved out into serialization where we can stream the output from the buf/channel by calling streamTo()
    @Override
    public String getStringValue() throws XPathException {
        final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream();
        try {
            streamTo(baos);
        } catch (final IOException ex) {
            throw new XPathException(getExpression(), "Unable to encode string value: " + ex.getMessage(), ex);
        } finally {
            try {
                baos.close();   //close the stream to ensure all data is flushed
            } catch (final IOException ioe) {
                LOG.error("Unable to close stream: {}", ioe.getMessage(), ioe);
            }
        }
        return baos.toString(UTF_8);
    }

    /**
     * Streams the raw binary data
     *
     * @param os the output to stream to
     * @throws IOException if an error occurs while writing to the stream
     */
    public abstract void streamBinaryTo(OutputStream os) throws IOException;

    /**
     * Streams the encoded binary data
     * @param os the output to stream to
     * @throws IOException if an error occurs while writing to the stream
     */
    public void streamTo(OutputStream os) throws IOException {

        //we need to create a safe output stream that cannot be closed
        final OutputStream safeOutputStream = new CloseShieldOutputStream(os);

        //get the encoder
        final FilterOutputStream fos = getBinaryValueType().getEncoder(safeOutputStream);

        //stream with the encoder
        streamBinaryTo(fos);

        //we do have to close the encoders output stream though
        //to ensure that all bytes have been written, this is
        //particularly nessecary for Apache Commons Codec stream encoders
        try {
            fos.close();
        } catch (final IOException ioe) {
            LOG.error("Unable to close stream: {}", ioe.getMessage(), ioe);
        }
    }

    public abstract InputStream getInputStream();

    public abstract boolean isClosed();

    /**
     * Increments the number of shared references to this binary value.
     */
    public abstract void incrementSharedReferences();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BinaryValue that = (BinaryValue) o;
        return compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        final InputStream is = getInputStream();
        int hash = 7;

        if (is != null) {
            int read;
            do {
                try {
                    read = is.read();
                    if (read != -1) {
                        hash = 31 * hash + read;
                    }
                } catch (final IOException ioe) {
                    read = -1;
                }
            } while (read != -1);
        }
        return hash;
    }
}
