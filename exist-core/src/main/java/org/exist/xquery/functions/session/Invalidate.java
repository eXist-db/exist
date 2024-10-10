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
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Optional;


/**
 * @author wolf
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class Invalidate extends SessionFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("invalidate", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
                    "Invalidate (remove) the current HTTP session if present",
                    null,
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE));

    public Invalidate(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Optional<SessionWrapper> session) throws XPathException {
        if (!session.isPresent()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        return withValidSession(session.get(), s -> {
            s.invalidate();
            return Sequence.EMPTY_SEQUENCE;
        }).orElse(Sequence.EMPTY_SEQUENCE);
    }
}
