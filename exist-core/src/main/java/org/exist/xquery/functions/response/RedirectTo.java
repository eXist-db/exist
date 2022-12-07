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
import java.io.IOException;


/**
 * Performs an HTTP Redirect.
 *
 * @author  Wolfgang Meier (wolfgang@exist-db.org)
 */
public class RedirectTo extends StrictResponseFunction {
    private static final Logger logger = LogManager.getLogger(RedirectTo.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("redirect-to", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
			"Sends a HTTP redirect response (302) to the client.",
			new SequenceType[] { new FunctionParameterSequenceType("uri", Type.STRING, Cardinality.EXACTLY_ONE, "The URI to redirect the client to") },
			new SequenceType(Type.EMPTY, Cardinality.EMPTY_SEQUENCE));

    public RedirectTo(final XQueryContext context)
    {
        super( context, signature );
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final ResponseWrapper response) throws XPathException {
        try {
            final String redirectURI = args[0].getStringValue();
            response.sendRedirect(redirectURI);
            return Sequence.EMPTY_SEQUENCE;
        } catch(final IOException e) {
            throw new XPathException(this, "An IO exception occurred during redirect: " + e.getMessage(), e);
        }
    }
}
