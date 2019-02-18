/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
    public Sequence eval(final Sequence[] args, final Optional<SessionWrapper> session)
            throws XPathException {
        if (!session.isPresent()) {
            return XPathUtil.javaObjectToXPath(Integer.valueOf(-1), context);
        }

        try {
            final long lastAccessedTime = session.get().getLastAccessedTime();
            return new DateTimeValue(new Date(lastAccessedTime));
        } catch (final IllegalStateException ise) {
            return new DateTimeValue(new Date(0));
        }
    }
}
