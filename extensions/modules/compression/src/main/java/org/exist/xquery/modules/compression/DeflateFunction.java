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
package org.exist.xquery.modules.compression;

import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.exist.dom.QName;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import static java.util.zip.Deflater.DEFAULT_COMPRESSION;


/**
 * Deflate compression
 * 
 * @author <a href="mailto:olaf@existsolutions.com">Olaf Schreck</a>
 * @version 1.0
 */
public class DeflateFunction extends BasicFunction
{
    private final static QName DEFLATE_FUNCTION_NAME = new QName("deflate", CompressionModule.NAMESPACE_URI, CompressionModule.PREFIX);

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
	    DEFLATE_FUNCTION_NAME,
            "Deflate data (RFC 1950)",
            new SequenceType[] {
                new FunctionParameterSequenceType("data", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The data to Deflate")
            },
            new SequenceType(
            Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE)
	),
        new FunctionSignature(
	    DEFLATE_FUNCTION_NAME,
            "Deflate data (RFC 1951)",
            new SequenceType[] {
                new FunctionParameterSequenceType("data", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The data to Deflate"),
                new FunctionParameterSequenceType("raw", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "If true, create raw deflate data that is not wrapped inside zlib header and checksum.")
            },
            new SequenceType(
            Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE)
        )
    };


    public DeflateFunction(XQueryContext context, FunctionSignature signature)
    {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
    {
        // is there some data to Deflate?
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final BinaryValue bin = (BinaryValue) args[0].itemAt(0);

	    boolean rawflag = false;
        if (args.length > 1 && !args[1].isEmpty()) {
            rawflag = args[1].itemAt(0).convertTo(Type.BOOLEAN).effectiveBooleanValue();
        }

    	final Deflater deflater = new Deflater(DEFAULT_COMPRESSION, rawflag);

        // deflate the data
        try (
                final UnsynchronizedByteArrayOutputStream stream = new UnsynchronizedByteArrayOutputStream();
	            DeflaterOutputStream dos = new DeflaterOutputStream(stream, deflater)
        ) {
            bin.streamBinaryTo(dos);
            dos.flush();
            dos.finish();
            
            return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), stream.toInputStream(), this);
        } catch(IOException ioe) {
            throw new XPathException(this, ioe.getMessage(), ioe);
        }
    }
}
