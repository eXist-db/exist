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

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.Collator;
import org.apache.log4j.Logger;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public abstract class BinaryValue extends AtomicValue {

    private final static Logger LOG = Logger.getLogger(BinaryValue.class);

    protected final int READ_BUFFER_SIZE = 4 * 1024; //4kb

    private final BinaryValueType binaryValueType;

    protected BinaryValue(BinaryValueType binaryValueType) {
        this.binaryValueType = binaryValueType;
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
            int value = compareTo((BinaryValue)other);
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

    //TODO need to understand the expense of this
    private int compareTo(BinaryValue otherValue) {
        return this.getReadOnlyBuffer().compareTo(otherValue.getReadOnlyBuffer());
    }

    @Override
    public Object toJavaObject(Class<?> target) throws XPathException {
        if(target.isAssignableFrom(getClass())) {
            return this;
        }

        if(target == byte[].class) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                streamBinaryTo(baos);
                return baos.toByteArray();
            } catch(IOException ioe) {
                LOG.error("Unable to Stream BinaryValue to byte[]: " + ioe.getMessage(), ioe);
            }

        }

        throw new XPathException("Cannot convert value of type " + Type.getTypeName(getType()) + " to Java object of type " + target.getName());
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
    public AtomicValue convertTo(int requiredType) throws XPathException {
        switch(requiredType) {
            case Type.UNTYPED_ATOMIC:
                //TODO still needed? Added trim() since it looks like a new line character is added
                return new UntypedAtomicValue(getStringValue());
            case Type.STRING:
                //TODO still needed? Added trim() since it looks like a new line character is added
                return new StringValue(getStringValue());
            default:
                throw new XPathException("cannot convert " + Type.getTypeName(getType()) + " to " + Type.getTypeName(requiredType));
        }
    }

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

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            streamTo(baos);
        } catch(IOException ex) {
            throw new XPathException("Unable to encode string value: " + ex.getMessage(), ex);
        } finally {
            try {
                baos.close();   //close the stream to ensure all data is flushed
            } catch(IOException ioe) {
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
        OutputStream safeOutputStream = makeSafeOutputStream(os);

        //get the encoder
        FilterOutputStream fos = getBinaryValueType().getEncoder(safeOutputStream);

        //stream with the encoder
        streamBinaryTo(fos);

        //we do have to close the encoders output stream though
        //to ensure that all bytes have been written, this is
        //particularly nessecary for Apache Commons Codec stream encoders
        try {
            fos.close();
        } catch(IOException ioe) {
            LOG.error("Unable to close stream: " + ioe.getMessage(), ioe);
        }
    }

    //TODO the expense of this function needs to be measured
    public abstract ByteBuffer getReadOnlyBuffer();

    //TODO the expense of the underlying call to getReadOnlyBuffer needs to be established
    public InputStream getInputStream() {
        return new InputStream() {

            private final ByteBuffer b = getReadOnlyBuffer();

            @Override
            public int read() throws IOException {
                return b.get();
            }
        };
    }

    public abstract void close() throws IOException;

   /**
     * Creates a safe OutputStream that cannot be closed
     */
    protected OutputStream makeSafeOutputStream(OutputStream os) {
        return new FilterOutputStream(os){
            @Override
            public void close() throws IOException {
                //do nothing
            }
        };
    }
}