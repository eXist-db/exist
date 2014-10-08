/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.output.ByteArrayOutputStream;

import org.exist.dom.QName;
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
 * Compression into a GZip file
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.1
 */
public class UnGZipFunction extends BasicFunction
{

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("ungzip", CompressionModule.NAMESPACE_URI, CompressionModule.PREFIX),
            "UnGZip's data",
            new SequenceType[] {
                new FunctionParameterSequenceType("gzip-data", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The gzip data to uncompress.")
            },
            new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE)
        )
    };

    public UnGZipFunction(XQueryContext context, FunctionSignature signature)
    {
            super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
    {
        // is there some data to unGZip?
        if(args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;

        BinaryValue bin = (BinaryValue) args[0].itemAt(0);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // ungzip the data
        try
        {
            GZIPInputStream gzis = new GZIPInputStream(bin.getInputStream());
            int size;
            byte[] b = new byte[4096];
            while ((size = gzis.read(b, 0, 4096)) != -1)
            {
                baos.write(b, 0, size);
            }
            baos.flush();
            baos.close();
            return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new ByteArrayInputStream(baos.toByteArray()));
        }
        catch(IOException ioe)
        {
            throw new XPathException(this, ioe.getMessage());
        }
    }
}