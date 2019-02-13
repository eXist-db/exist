/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  $Id: BuiltinFunctions.java 9598 2009-07-31 05:45:57Z ixitar $
 */
package org.exist.xquery.functions.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.util.io.FastByteArrayInputStream;
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
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class BinaryToString extends BasicFunction {

    protected static final Logger logger = LogManager.getLogger(BinaryToString.class);
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
        new QName("binary-to-string", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
        "Returns the contents of a binary resource as an xs:string value. The binary data "
        + "is transformed into a Java string using the encoding specified in the optional "
        + "second argument or the default of UTF-8.",
        new SequenceType[]{
            new FunctionParameterSequenceType("binary-resource", Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "The binary resource")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the string containing the encoded binary resource")),
        new FunctionSignature(
        new QName("binary-to-string", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
        "Returns the contents of a binary resource as an xs:string value. The binary data "
        + "is transformed into a Java string using the encoding specified in the optional "
        + "second argument or the default of UTF-8.",
        new SequenceType[]{
            new FunctionParameterSequenceType("binary-resource", Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "The binary resource"),
            new FunctionParameterSequenceType("encoding", Type.STRING, Cardinality.EXACTLY_ONE, "The encoding type.  i.e. 'UTF-8'")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the string containing the encoded binary resource")),
        new FunctionSignature(
        new QName("string-to-binary", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
        "Returns the contents of a string as an base64binary value. The string data "
        + "is transformed into a binary using the encoding specified in the optional "
        + "second argument or the default of UTF-8.",
        new SequenceType[]{
            new FunctionParameterSequenceType("encoded-string", Type.STRING, Cardinality.ZERO_OR_ONE, "The string containing the encoded binary resource")
        },
        new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the binary resource")),
        new FunctionSignature(
        new QName("string-to-binary", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
        "Returns the contents of a string as a base64binary value. The string data "
        + "is transformed into a binary using the encoding specified in the optional "
        + "second argument or the default of UTF-8.",
        new SequenceType[]{
            new FunctionParameterSequenceType("encoded-string", Type.STRING, Cardinality.ZERO_OR_ONE, "The string containing the encoded binary resource"),
            new FunctionParameterSequenceType("encoding", Type.STRING, Cardinality.EXACTLY_ONE, "the encoding type.  i.e. 'UTF-8'")
        },
        new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the binary resource"))
    };

    public BinaryToString(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {

        if(args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        String encoding = "UTF-8";
        if(args.length == 2) {
            encoding = args[1].getStringValue();
        }
        if(isCalledAs("binary-to-string")) {
            return binaryToString((BinaryValue) args[0].itemAt(0), encoding);
        } else {
            return stringToBinary(args[0].getStringValue(), encoding);
        }
    }

    protected StringValue binaryToString(BinaryValue binary, String encoding) throws XPathException {
        try (final FastByteArrayOutputStream os = new FastByteArrayOutputStream()) {
            binary.streamBinaryTo(os);
            return new StringValue(os.toString(encoding));
        } catch(final IOException ioe) {
            throw new XPathException(this, ioe);
        }
    }

    protected BinaryValue stringToBinary(String str, String encoding) throws XPathException {
        try {
            return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new FastByteArrayInputStream(str.getBytes(encoding)));
        } catch(final UnsupportedEncodingException e) {
            throw new XPathException(this, "Unsupported encoding: " + encoding);
        }
    }
}
