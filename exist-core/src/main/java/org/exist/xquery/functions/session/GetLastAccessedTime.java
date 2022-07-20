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
package org.exist.xquery.functions.session;

import java.util.Date;
import java.util.Optional;

import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Returns the last time the client sent a request associated
 * with this session January 1, 1970 GMT if it is an invalidated
 * session
 *
 * @author José María Fernández (jmfg@users.sourceforge.net)
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class GetLastAccessedTime extends SessionFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("get-last-accessed-time", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
                    "Returns the last time the client sent a request associated with this session. " +
                            "If a session does not exist, a new one is created. Actions that your application " +
                            "takes, such as getting or setting a value associated with the session, do not " +
                            "affect the access time.  If the session is already invalidated, it returns " +
                            "January 1, 1970 GMT",
                    null,
                    new FunctionReturnSequenceType(Type.DATE_TIME, Cardinality.EXACTLY_ONE, "the date-time when the session was last accessed"));

    public GetLastAccessedTime(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Optional<SessionWrapper> session) throws XPathException {
        if (!session.isPresent()) {
            return XPathUtil.javaObjectToXPath(-1, context, this);
        }

        final Date lastAccessedTime = withValidSession(session.get(), SessionWrapper::getLastAccessedTime).map(Date::new)
                .orElseGet(() -> new Date(0));
        return new DateTimeValue(this, lastAccessedTime);
    }
}
