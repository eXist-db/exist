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

import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Returns the ID of the current session or an empty sequence
 * if there is no session.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author Loren Cahlander
 */
public class GetID extends StrictSessionFunction {
    public final static FunctionSignature signature = new FunctionSignature(
            new QName("get-id", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
            "Returns the ID of the current session or an empty sequence if there is no session.",
            null,
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the session ID")
    );

    public GetID(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final SessionWrapper session) throws XPathException {
        final Expression expression = this;
        return withValidSession(session, SessionWrapper::getId)
                .filter(Objects::nonNull)
                .map(id -> (Sequence)new StringValue(expression, id))
                .orElse(Sequence.EMPTY_SEQUENCE);
    }
}
