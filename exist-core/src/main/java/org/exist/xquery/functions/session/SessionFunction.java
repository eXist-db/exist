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
package org.exist.xquery.functions.session;

import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.Sequence;

import java.util.Optional;

/**
 * Abstract for functions in the {@link SessionFunction}
 * which need access to the http session.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class SessionFunction extends BasicFunction {

    public SessionFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public final Sequence eval(final Sequence[] args, final Sequence contextSequence)
            throws XPathException {
        return eval(args,
                Optional.ofNullable(context.getHttpContext())
                    .map(XQueryContext.HttpContext::getSession)
        );
    }

    /**
     * Evaluate the function with the Http Session.
     *
     * @param args the arguments to the function.
     * @param session the http session
     *
     * @return the result of the function.
     *
     * @throws XPathException an XPath Exception
     */
    protected abstract Sequence eval(final Sequence[] args, final Optional<SessionWrapper> session) throws XPathException;

    /**
     * Gets an existing Session, or creates a new Session.
     *
     * @param session a possible existing session.
     *
     * @return a session.
     *
     * @throws XPathException if a session could not be created.
     */
    protected SessionWrapper getOrCreateSession(final Optional<SessionWrapper> session) throws XPathException {
        return getOrCreateSession(this, context, session);
    }

    /**
     * Gets an existing Session, or creates a new Session.
     *
     * @param expr the calling expression.
     * @param context the XQuery context.
     * @param session a possible existing session.
     *
     * @return a session.
     *
     * @throws XPathException if a session could not be created.
     */
    static SessionWrapper getOrCreateSession(final Expression expr, final XQueryContext context, final Optional<SessionWrapper> session) throws XPathException {
        if (session.isPresent()) {
            return session.get();
        }

        final RequestWrapper request = Optional.ofNullable(context.getHttpContext())
                .map(XQueryContext.HttpContext::getRequest)
                .orElseThrow(() -> new XPathException(expr, ErrorCodes.XPDY0002, "No response object found in the current XQuery context."));

        final SessionWrapper newSession = request.getSession(true);
        context.setHttpContext(context.getHttpContext().setSession(newSession));
        return newSession;
    }
}
