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

import java.util.Enumeration;
import java.util.Optional;

import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author Loren Cahlander
 */
public class Clear extends StrictSessionFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("clear", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
                    "Removes all attributes from the current HTTP session. Does NOT invalidate the session.",
                    null,
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE));

    public Clear(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    protected Sequence eval(final Sequence[] args, @Nonnull final SessionWrapper session) throws XPathException {

        final Optional<Enumeration<String>> maybeAttributeNames = withValidSession(session, SessionWrapper::getAttributeNames);
        if (maybeAttributeNames.isPresent()) {
            final Enumeration<String> attributeNames = maybeAttributeNames.get();
            while (attributeNames.hasMoreElements()) {
                final String attributeName = attributeNames.nextElement();
                session.removeAttribute(attributeName);
            }
        }

        return Sequence.EMPTY_SEQUENCE;
    }
}
