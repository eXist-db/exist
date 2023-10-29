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
package org.exist.xquery.functions.response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.io.IOException;
import java.io.OutputStream;
import org.exist.xquery.value.BinaryValue;

import javax.annotation.Nonnull;

public class StreamBinary extends StrictResponseFunction {

    private static final Logger logger = LogManager.getLogger(StreamBinary.class);
    private static final FunctionParameterSequenceType BINARY_DATA_PARAM = new FunctionParameterSequenceType("binary-data", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The binary data to stream");
    private static final FunctionParameterSequenceType CONTENT_TYPE_PARAM = new FunctionParameterSequenceType("content-type", Type.STRING, Cardinality.EXACTLY_ONE, "The ContentType HTTP header value");
    private static final FunctionParameterSequenceType FILENAME_PARAM = new FunctionParameterSequenceType("filename", Type.STRING, Cardinality.ZERO_OR_ONE, "The filename.  If provided, a Content-Disposition header is set for the filename in the HTTP Response");
    public final static FunctionSignature signature =
            new FunctionSignature(
            new QName("stream-binary", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
            "Streams the binary data to the current servlet response output stream. The ContentType "
            + "HTTP header is set to the value given in $content-type."
            + "Note: the servlet output stream will be closed afterwards and mime-type settings in the prolog "
            + "will not be passed.",
            new SequenceType[]{BINARY_DATA_PARAM, CONTENT_TYPE_PARAM, FILENAME_PARAM},
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
            );

    public StreamBinary(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final ResponseWrapper response) throws XPathException {
        if(args[0].isEmpty() || args[1].isEmpty()) {
            return (Sequence.EMPTY_SEQUENCE);
        }

        final BinaryValue binary = (BinaryValue) args[0].itemAt(0);
        final String contentType = args[1].getStringValue();
        String filename = null;

        if((args.length > 2) && !args[2].isEmpty()) {
            filename = args[2].getStringValue();
        }

        if(!"org.exist.http.servlets.HttpResponseWrapper".equals(response.getClass().getName())) {
            throw (new XPathException(this, ErrorCodes.XPDY0002, signature.toString() + " can only be used within the EXistServlet or XQueryServlet"));
        }

        response.setHeader("Content-Type", contentType);

        if(filename != null) {
            response.setHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");
        }

        try {
            final OutputStream os = response.getOutputStream();
            binary.streamBinaryTo(response.getOutputStream());
            os.close();

            //commit the response
            response.flushBuffer();
        } catch (final IOException e) {
            throw (new XPathException(this, "IO exception while streaming data: " + e.getMessage(), e));
        }

        return Sequence.EMPTY_SEQUENCE;
    }
}
