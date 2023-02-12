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

import javax.annotation.Nonnull;


/**
 * Set's a HTTP header on the HTTP Response.
 *
 * @author <a href="mailto:adam.retter@devon.gov.uk">Adam Retter</a>
 * @see org.exist.xquery.Function
 */
public class SetHeader extends StrictResponseFunction {
    private static final Logger logger = LogManager.getLogger(SetHeader.class);
    private static final FunctionParameterSequenceType NAME_PARAM = new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE, "The header name");
    private static final FunctionParameterSequenceType VALUE_PARAM = new FunctionParameterSequenceType("value", Type.STRING, Cardinality.EXACTLY_ONE, "The header value");

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("set-header", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
                    "Sets a HTTP Header on the HTTP Response.",
                    new SequenceType[]{NAME_PARAM, VALUE_PARAM},
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE));

    public SetHeader(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final ResponseWrapper response)
            throws XPathException {
        final String name = args[0].getStringValue();
        final String value = args[1].getStringValue();

        response.setHeader(name, value);

        return Sequence.EMPTY_SEQUENCE;
    }
}
