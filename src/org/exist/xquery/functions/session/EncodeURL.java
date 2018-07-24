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
 *  $Id$
 */
package org.exist.xquery.functions.session;

import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.*;
import org.exist.xquery.functions.response.StrictResponseFunction;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import javax.annotation.Nonnull;

/**
 * @author Wolfgang Meier
 * @author Loren Cahlander
 */
public class EncodeURL extends StrictResponseFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("encode-url", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
                    "Encodes the specified URL with the current HTTP session-id.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("url", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URL to encode")
                    },
                    new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE, "the encoded URL"));

    public EncodeURL(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    protected Sequence eval(final Sequence[] args, @Nonnull final ResponseWrapper response) throws XPathException {
        final String url = args[0].getStringValue();
        return new AnyURIValue(response.encodeURL(url));
    }
}
