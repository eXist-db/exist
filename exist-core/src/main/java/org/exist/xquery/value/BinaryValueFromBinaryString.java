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

import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import java.io.*;
import java.nio.charset.Charset;

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

//    private final Charset encoding;
    private final String value;
    private boolean closed = false;

    public BinaryValueFromBinaryString(BinaryValueType binaryValueType, String value) throws XPathException {
        this(null, binaryValueType, value);
    }

    public BinaryValueFromBinaryString(final Expression expression, BinaryValueType binaryValueType, String value) throws XPathException {
        super(expression, null, binaryValueType);
        this.value = binaryValueType.verifyAndFormatString(value);
        //this.encoding = Charset.defaultCharset();
    }

    public BinaryValueFromBinaryString(BinaryValueType binaryValueType, String value, Charset encoding) throws XPathException {
        super(null, null, binaryValueType);
        this.value = binaryValueType.verifyAndFormatString(value);
//        this.encoding = encoding;
    }

    @Override
    public BinaryValue convertTo(final BinaryValueType binaryValueType) throws XPathException {
        //TODO temporary approach, consider implementing a TranscodingBinaryValueFromBinaryString(BinaryValueFromBinaryString) class
        //that only does the transcoding lazily
        try (final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream(); final FilterOutputStream fos = binaryValueType.getEncoder(baos)) {
            //transcode
            streamBinaryTo(fos);
            return new BinaryValueFromBinaryString(getExpression(), binaryValueType, baos.toString(UTF_8));
        } catch (final IOException ioe) {
            throw new XPathException(getExpression(), ioe);
        }
    }

    @Override
    public void streamBinaryTo(OutputStream os) throws IOException {

        //we need to create a safe output stream that cannot be closed
        final OutputStream safeOutputStream = new CloseShieldOutputStream(os);

        //get the decoder
        final FilterOutputStream fos = getBinaryValueType().getDecoder(safeOutputStream);

        //write with the decoder
        final byte[] data = value.getBytes();
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
        final byte[] data = value.getBytes(); //TODO consider a more efficient approach for writing large strings
        os.write(data);
    }

    @Override
    public InputStream getInputStream() {
        //TODO consider a more efficient approach for writing large strings
        final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream();
        try {
            streamBinaryTo(baos);
        } catch (final IOException ioe) {
            LOG.error("Unable to get read only buffer: {}", ioe.getMessage(), ioe);
        }
        return baos.toInputStream();
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