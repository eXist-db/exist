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
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

import java.util.Optional;

/**
 * Abstract for functions in the {@link ResponseFunction}
 * which need access to the http response.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class ResponseFunction extends BasicFunction {

    public ResponseFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public final Sequence eval(final Sequence[] args, final Sequence contextSequence)
            throws XPathException {
        return eval(args,
                Optional.ofNullable(context.getHttpContext())
                    .map(XQueryContext.HttpContext::getResponse)
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
    protected abstract Sequence eval(final Sequence[] args, final Optional<ResponseWrapper> response) throws XPathException;
}
