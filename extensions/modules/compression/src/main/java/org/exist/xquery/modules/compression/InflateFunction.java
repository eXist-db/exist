/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2018 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */

package org.exist.xquery.modules.compression;

import java.io.IOException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.exist.dom.QName;
import org.exist.util.io.FastByteArrayOutputStream;
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


/**
 * Inflate uncompression
 * 
 * @author <a href="mailto:olaf@existsolutions.com">Olaf Schreck</a>
 * @version 1.0
 */
public class InflateFunction extends BasicFunction
{

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("inflate", CompressionModule.NAMESPACE_URI, CompressionModule.PREFIX),
            "Inflate data (RFC 1950)",
            new SequenceType[] {
                new FunctionParameterSequenceType("inflate-data", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The inflate data to uncompress.")
            },
            new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE)
        ),
        new FunctionSignature(
            new QName("inflate", CompressionModule.NAMESPACE_URI, CompressionModule.PREFIX),
            "Inflate data (RFC 1951)",
            new SequenceType[] {
                new FunctionParameterSequenceType("inflate-data", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The inflate data to uncompress."),
                new FunctionParameterSequenceType("raw", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "If true, expect raw deflate data that is not wrapped inside zlib header and checksum.")
            },
            new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE)
        )
    };

    public InflateFunction(XQueryContext context, FunctionSignature signature)
    {
            super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
    {
        // is there some data to inflate?
        if(args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;

        final BinaryValue bin = (BinaryValue) args[0].itemAt(0);

	boolean rawflag = false;
        if(args.length > 1 && !args[1].isEmpty())
	    rawflag = args[1].itemAt(0).convertTo(Type.BOOLEAN).effectiveBooleanValue();

	Inflater infl = new Inflater(rawflag);

        // uncompress the data
        try(final InflaterInputStream iis = new InflaterInputStream(bin.getInputStream(), infl);
                final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
            int read = -1;
            final byte[] b = new byte[4096];
            while ((read = iis.read(b)) != -1) {
                baos.write(b, 0, read);
            }

            return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), baos.toFastByteInputStream());
        } catch(final IOException ioe) {
            throw new XPathException(this, ioe.getMessage(), ioe);
        }
    }
}
