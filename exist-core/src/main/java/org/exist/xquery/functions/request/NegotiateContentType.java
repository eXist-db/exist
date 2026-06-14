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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implements the {@code request:negotiate-content-type} function, which selects
 * the best media type to return for the current request by matching the media
 * types the server can produce against the HTTP {@code Accept} header.
 */
public class NegotiateContentType extends StrictRequestFunction {

    private static final String FN_NAME = "negotiate-content-type";

    private static final FunctionParameterSequenceType FS_PARAM_AVAILABLE = new FunctionParameterSequenceType(
            "available", Type.STRING, Cardinality.ZERO_OR_MORE,
            "The media types the server can produce, in order of preference.");
    private static final FunctionParameterSequenceType FS_PARAM_DEFAULT = new FunctionParameterSequenceType(
            "default", Type.STRING, Cardinality.ZERO_OR_ONE,
            "The media type to fall back to when no item of $available is acceptable.");

    private static final String DESCRIPTION =
            "Selects the best media type to return for the current request by matching the media types the "
                    + "server can produce ($available) against the HTTP Accept header of the request. Quality "
                    + "values (q=) and the */* and type/* wildcards are honored, per RFC 7231. A missing or empty "
                    + "Accept header means no preference, in which case the first item of $available is returned.";

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName(FN_NAME, RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
                    DESCRIPTION + " Returns the empty sequence if no item of $available is acceptable; the caller "
                            + "should then respond with 406 Not Acceptable.",
                    new SequenceType[] { FS_PARAM_AVAILABLE },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                            "the best matching media type, or the empty sequence if none is acceptable")),
            new FunctionSignature(
                    new QName(FN_NAME, RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
                    DESCRIPTION + " Returns $default if no item of $available is acceptable.",
                    new SequenceType[] { FS_PARAM_AVAILABLE, FS_PARAM_DEFAULT },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                            "the best matching media type, or $default if none is acceptable"))
    };

    public NegotiateContentType(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final RequestWrapper request) throws XPathException {
        final List<String> available = new ArrayList<>(args[0].getItemCount());
        for (final SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
            available.add(i.nextItem().getStringValue());
        }

        final Optional<String> best = AcceptHeader.negotiate(request.getHeader("Accept"), available);
        if (best.isPresent()) {
            return new StringValue(this, best.get());
        }
        if (args.length > 1) {
            return args[1];
        }
        return Sequence.EMPTY_SEQUENCE;
    }
}
