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
package org.exist.xquery.functions.request;

import java.util.List;
import java.util.Map;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import javax.annotation.Nonnull;

/**
 * Retrieve the part headers of each uploaded file in a multi-part request.
 */
public class GetUploadedFileHeaders extends StrictRequestFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("get-uploaded-file-headers", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
            "Retrieve the part headers of each uploaded file submitted under a parameter name in a " +
            "multi-part request. Returns one map (header name to header value) per uploaded file, in " +
            "submission order and aligned with request:get-uploaded-file-name. Header names are keyed " +
            "as submitted. Returns the empty sequence if the request is not a multi-part request or the " +
            "parameter name does not point to a file part.",
            new SequenceType[] {
                new FunctionParameterSequenceType("upload-param-name", Type.STRING, Cardinality.EXACTLY_ONE, "The parameter name")
            },
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_MORE, "one map of header name to header value per uploaded file"));

    public GetUploadedFileHeaders(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final RequestWrapper request)
            throws XPathException {
        final String uploadParamName = args[0].getStringValue();
        final List<Map<String, String>> fileHeaders = request.getUploadedFileHeaders(uploadParamName);
        if (fileHeaders == null || fileHeaders.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final ValueSequence result = new ValueSequence();
        for (final Map<String, String> headers : fileHeaders) {
            final MapType map = new MapType(this, context);
            for (final Map.Entry<String, String> header : headers.entrySet()) {
                map.add(new StringValue(this, header.getKey()), new StringValue(this, header.getValue()));
            }
            result.add(map);
        }
        return result;
    }
}
