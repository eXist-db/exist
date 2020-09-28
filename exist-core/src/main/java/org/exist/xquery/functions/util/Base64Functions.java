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
package org.exist.xquery.functions.util;

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Base64 String conversion functions.
 *
 * @author Andrzej Taramina (andrzej@chaeron.com)
 */

public class Base64Functions extends BasicFunction {
    protected static final Logger logger = LogManager.getLogger(Base64Functions.class);

    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("base64-encode", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Encodes the given string as Base64",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("string", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to be Base64 encoded")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the Base64 encoded output, with trailing newlines trimmed")
            ),

            new FunctionSignature(
                    new QName("base64-encode", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Encodes the given string as Base64",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("string", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to be Base64 encoded"),
                            new FunctionParameterSequenceType("trim", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "Trim trailing newlines?")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the Base64 encoded output"),
                    "This function is deprecated. The output does not need to be trimmed, please use util:base64-encode#1 instead."
            ),

            new FunctionSignature(
                    new QName("base64-decode", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Decode the given Base64 encoded string back to clear text",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("string", Type.STRING, Cardinality.ZERO_OR_ONE, "The Base64 string to be decoded")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the decoded output")
            ),

            new FunctionSignature(
                    new QName("base64-encode-url-safe", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Encodes the given string as Base64, url-safe. No padding and use - and _ instead of + and /.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("string", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to be Base64 encoded (url-safe)")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the Base64, url-safe encoded output without padding")
            )

    };


    public Base64Functions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }


    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String str = args[0].getStringValue();

        if (isCalledAs("base64-encode")) {
            String b64Str = Base64.encodeBase64String(str.getBytes(UTF_8));
            return new StringValue(b64Str);
        }

        if (isCalledAs("base64-encode-url-safe")) {
            String b64Str = Base64.encodeBase64URLSafeString(str.getBytes(UTF_8));
            return new StringValue(b64Str);
        }

        // isCalledAs("base64-decode")
        // Base64.decodeBase64 can handle standard and url-safe base64 encoded data
        final byte[] data = Base64.decodeBase64(str);
        return new StringValue(new String(data, UTF_8));
    }

}
