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
package org.exist.xquery.functions.response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import javax.annotation.Nonnull;


/**
 * Set's a HTTP server status code on the HTTP Response.
 *
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @see org.exist.xquery.Function
 */
public class SetStatusCode extends StrictResponseFunction {
    private static final Logger logger = LogManager.getLogger(SetStatusCode.class);

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("set-status-code", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
                    "Sets a HTTP server status code on the HTTP Response.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("code", Type.INTEGER, Cardinality.EXACTLY_ONE, "The status code")
                    },
                    new SequenceType(Type.EMPTY, Cardinality.EMPTY));

    public SetStatusCode(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final ResponseWrapper response)
            throws XPathException {
        final int code = ((IntegerValue) args[0].convertTo(Type.INTEGER)).getInt();

        response.setStatusCode(code);

        return Sequence.EMPTY_SEQUENCE;
    }
}
