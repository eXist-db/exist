/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.value;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Collator;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.log4j.Logger;
import org.apache.commons.io.output.ByteArrayOutputStream;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public abstract class BinaryValue extends AtomicValue {

    private final static Logger LOG = Logger.getLogger(BinaryValue.class);

    protected final int READ_BUFFER_SIZE = 4 * 1024; //4kb

    private final BinaryValueManager binaryValueManager;
    private final BinaryValueType binaryValueType;

    protected BinaryValue(BinaryValueManager binaryValueManager, BinaryValueType binaryValueType) {
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
    public boolean compareTo(Collator collator, int operator, AtomicValue other) throws XPathException {
        if (other.getType() == Type.HEX_BINARY || other.getType() == Type.BASE64_BINARY) {
            final int value = compareTo((BinaryValue)other);
            switch(operator) {
                case Constants.EQ:
                    return value == 0;
                case Constants.NEQ:
                    return value != 0;
                case Constants.GT:
                    return value > 0;
                case Constants.GTEQ:
                    return value >= 0;
                case Constants.LT:
                    return value < 0;
                case Constants.LTEQ:
                    return value <= 0;
                default:
                    throw new XPathException("Type error: cannot apply operator to numeric value");
            }
        } else {
            throw new XPathException("Cannot compare value of type xs:hexBinary with " + Type.getTypeName(other.getType()));
        }
    }

    @Override
    public int compareTo(Collator collator, AtomicValue other) throws XPathException {
        if (other.getType() == Type.HEX_BINARY || other.getType() == Type.BASE64_BINARY) {
            return compareTo((BinaryValue)other);
        } else {
            throw new XPathException("Cannot compare value of type xs:hexBinary with " + Type.getTypeName(other.getType()));
        }
    }

    private int compareTo(BinaryValue otherValue) {

        final InputStream is = getInputStream();
        final InputStream otherIs = otherValue.getInputStream();

        if(is == null && otherIs == null) {
            return 0;
        } else if(is == null) {
            return -1;
        } else if(otherIs == null) {
            return 1;
        } else {
            int read = -1;
            int otherRead = -1;
            while(true) {
                try {
                    read = is.read();
                } catch(final IOException ioe) {
                    return -1;
                }

                try {
                    otherRead = otherIs.read();
                } catch(final IOException ioe) {
                    return 1;
                }

                return read - otherRead;
            }
        }
    }

    @Override
    public <T> T toJavaObject(Class<T> target) throws XPathException {
        if(target.isAssignableFrom(getClass())) {
            return (T)this;
        }

        if(target == byte[].class) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                streamBinaryTo(baos);
                return (T)baos.toByteArray();
            } catch(final IOException ioe) {
                LOG.error("Unable to Stream BinaryValue to byte[]: " + ioe.getMessage(), ioe);
            }

        }

        throw new XPathException("Cannot convert value of type " + Type.getTypeName(getType()) + " to Java object of type " + target.getName());
    }
    
    /**
     * Return the underlying Java object for this binary value. Might be a File or byte[].
     */
    public <T> T toJavaObject() throws XPathException {
    	return (T)toJavaObject(byte[].class);
    }

    @Override
    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException("Cannot compare values of type " + Type.getTypeName(getType()));
    }

    @Override
    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException("Cannot compare values of type " + Type.getTypeName(getType()));
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {

        final AtomicValue result;

        if(requiredType == getType() || requiredType == Type.ITEM || requiredType == Type.ATOMIC) {
            result = this;
        } else {
            switch(requiredType) {
                case Type.BASE64_BINARY:
                    result = convertTo(new Base64BinaryValueType());
                    break;
                case Type.HEX_BINARY:
                    result = convertTo(new HexBinaryValueType());
                    break;
                case Type.UNTYPED_ATOMIC:
                    //TODO still needed? Added trim() since it looks like a new line character is added
                    result = new UntypedAtomicValue(getStringValue());
                    break;
                case Type.STRING:
                    //TODO still needed? Added trim() since it looks like a new line character is added
                    result = new StringValue(getStringValue());
                    break;
                default:
                    throw new XPathException("cannot convert " + Type.getTypeName(getType()) + " to " + Type.getTypeName(requiredType));
            }
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
        throw new XPathException("FORG0006: value of type " + Type.getTypeName(getType()) + " has no boolean value.");
    }
    
    //TODO ideally this should be moved out into serialization where we can stream the output from the buf/channel by calling streamTo()
    @Override
    public String getStringValue() throws XPathException {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            streamTo(baos);
        } catch(final IOException ex) {
            throw new XPathException("Unable to encode string value: " + ex.getMessage(), ex);
        } finally {
            try {
                baos.close();   //close the stream to ensure all data is flushed
            } catch(final IOException ioe) {
                LOG.error("Unable to close stream: " + ioe.getMessage(), ioe);
            }
        }

        return new String(baos.toByteArray());
    }

    /**
     * Streams the raw binary data
     */
    public abstract void streamBinaryTo(OutputStream os) throws IOException;

    /**
     * Streams the encoded binary data
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
        } catch(final IOException ioe) {
            LOG.error("Unable to close stream: " + ioe.getMessage(), ioe);
        }
    }

    public abstract InputStream getInputStream();

    public abstract void close() throws IOException;
}
