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
package org.exist.xquery.functions.request;

import java.util.Enumeration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:alain.m.pannetier@gmail.com">Alain Pannetier</a>
 * @author <a href="adam.retter@devon.gov.uk">Adam Retter</a>
 */

public class GetHeaderNames extends StrictRequestFunction {

    protected static final Logger logger = LogManager.getLogger(GetHeaderNames.class);

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName(
                            "get-header-names",
                            RequestModule.NAMESPACE_URI,
                            RequestModule.PREFIX),
                    "Returns a sequence containing the names of all headers passed in the current request",
                    null,
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "a sequence containing the names of all headers passed in the current request"));

    public GetHeaderNames(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final RequestWrapper request)
            throws XPathException {
        final Enumeration<String> headerNames = request.getHeaderNames();
        if (!headerNames.hasMoreElements()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final ValueSequence result = new ValueSequence();
        while (headerNames.hasMoreElements()) {
            final String headerName = headerNames.nextElement();
            result.add(new StringValue(headerName));
        }
        return result;
    }
}
