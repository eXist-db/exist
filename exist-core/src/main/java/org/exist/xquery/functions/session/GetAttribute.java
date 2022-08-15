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
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Optional;

/**
 * Returns an attribute stored in the current session or an empty sequence
 * if the attribute does not exist.
 *
 * @author Wolfgang Meier
 * @author Loren Cahlander
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class GetAttribute extends SessionFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("get-attribute", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
                    "Returns an attribute stored in the current session object or an empty sequence " +
                            "if the attribute cannot be found.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE, "The session attribute name")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the attribute value"));

    public GetAttribute(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Optional<SessionWrapper> session) throws XPathException {
        if (!session.isPresent()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String attributeName = args[0].getStringValue();

        final Optional<Object> maybeAttributeValue = withValidSession(session.get(), s -> Optional.ofNullable(s.getAttribute(attributeName))).orElse(Optional.empty());
        if (!maybeAttributeValue.isPresent()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Object o = maybeAttributeValue.get();
        return XPathUtil.javaObjectToXPath(o, context, this);
    }
}
