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

import org.exist.dom.QName;
import org.exist.http.AcceptHeader;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Implements the {@code request:parse-accept-header} function, which parses the
 * HTTP {@code Accept} header of the current request into a sequence of maps, one
 * per media range, ordered by descending quality then descending specificity.
 */
public class ParseAcceptHeader extends StrictRequestFunction {

    private static final StringValue KEY_TYPE = new StringValue("type");
    private static final StringValue KEY_QUALITY = new StringValue("quality");
    private static final StringValue KEY_PARAMETERS = new StringValue("parameters");

    public static final FunctionSignature signature = new FunctionSignature(
            new QName("parse-accept-header", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
            "Parses the HTTP Accept header of the current request into a sequence of maps, one per media range, "
                    + "ordered by descending quality and then descending specificity. Each map has keys 'type' "
                    + "(the media type as a string, e.g. 'text/html'), 'quality' (the q value as an xs:double, "
                    + "defaulting to 1.0), and 'parameters' (a map(xs:string, xs:string) of any other media-range "
                    + "parameters). Returns the empty sequence if the request has no Accept header.",
            new SequenceType[] {},
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_MORE,
                    "one map per media range, highest preference first"));

    public ParseAcceptHeader(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final RequestWrapper request) throws XPathException {
        final String accept = request.getHeader("Accept");
        if (accept == null || accept.isBlank()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final ValueSequence result = new ValueSequence();
        for (final AcceptHeader.MediaRange range : AcceptHeader.parse(accept)) {
            final MapType map = new MapType(context);
            map.add(KEY_TYPE, new StringValue(this, range.mediaType()));
            map.add(KEY_QUALITY, new DoubleValue(this, range.quality()));

            final MapType parameters = new MapType(context);
            for (final Map.Entry<String, String> parameter : range.parameters().entrySet()) {
                parameters.add(new StringValue(this, parameter.getKey()), new StringValue(this, parameter.getValue()));
            }
            map.add(KEY_PARAMETERS, parameters);

            result.add(map);
        }
        return result;
    }
}
