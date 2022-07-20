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
 * Returns the time when this session was created, or
 * January 1, 1970 GMT if it is an invalidated session
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class GetCreationTime extends SessionFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("get-creation-time", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
                    "Returns the time when this session was created. If a session does not " +
                            "exist, a new one is created. If the session is already invalidated, it " +
                            "returns January 1, 1970 GMT",
                    null,
                    new FunctionReturnSequenceType(Type.DATE_TIME, Cardinality.EXACTLY_ONE, "the date-time when the session was created"));

    public GetCreationTime(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Optional<SessionWrapper> session) throws XPathException {
        if (!session.isPresent()) {
            return XPathUtil.javaObjectToXPath(-1, context, this);
        }

        final Date creationTime = withValidSession(session.get(), SessionWrapper::getCreationTime).map(Date::new)
                .orElseGet(() -> new Date(0));
        return new DateTimeValue(this, creationTime);
    }
}
