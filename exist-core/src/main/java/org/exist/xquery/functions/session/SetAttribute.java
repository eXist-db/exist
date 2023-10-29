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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Optional;

/**
 * @author Wolfgang Meier
 * @author Loren Cahlander
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class SetAttribute extends SessionFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("set-attribute", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
                    "Stores a value in the current session using the supplied attribute name. If no session exists, then one will be created.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE, "The attribute name"),
                            new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The value to be stored in the session by the attribute name")
                    },
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE));


    public SetAttribute(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Optional<SessionWrapper> maybeSession) throws XPathException {
        final SessionWrapper session = getValidOrCreateSession(maybeSession);

        final String attributeName = args[0].getStringValue();
        final Sequence attributeValue = args[1];

        session.setAttribute(attributeName, attributeValue);

        return Sequence.EMPTY_SEQUENCE;
    }
}
