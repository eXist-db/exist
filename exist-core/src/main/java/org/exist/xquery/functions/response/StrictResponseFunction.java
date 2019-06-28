/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.response;

import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Abstract for functions in the {@link ResponseModule}
 * which need access to the http response, and
 * should raise an {@link ErrorCodes#XPDY0002} if
 * the request is not available.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class StrictResponseFunction extends ResponseFunction {

    public StrictResponseFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public final Sequence eval(final Sequence[] args, final Optional<ResponseWrapper> request)
            throws XPathException {
        return eval(
                args,
                request.orElseThrow(() -> new XPathException(this, ErrorCodes.XPDY0002, "No response object found in the current XQuery context."))
        );
    }

    /**
     * Evaluate the function with the Http Response.
     *
     * @param args the arguments to the function.
     * @param response the http response
     *
     * @return the result of the function.
     *
     * @throws XPathException an XPath Exception
     */
    protected abstract Sequence eval(final Sequence[] args, @Nonnull final ResponseWrapper response) throws XPathException;
}
