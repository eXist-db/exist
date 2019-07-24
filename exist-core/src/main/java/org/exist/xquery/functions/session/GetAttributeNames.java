/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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

import java.util.Enumeration;

import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author Loren Cahlander
 */
public class GetAttributeNames extends StrictSessionFunction {
    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("get-attribute-names", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
                    "Returns a sequence containing the names of all session attributes defined within the "
                            + "current HTTP session.",
                    null,
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "the list of attribute names"));

    public GetAttributeNames(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final SessionWrapper session)
            throws XPathException {
        final Enumeration<String> attributeNames = session.getAttributeNames();
        if (!attributeNames.hasMoreElements()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final ValueSequence result = new ValueSequence();
        while (attributeNames.hasMoreElements()) {
            final String attributeName = attributeNames.nextElement();
            result.add(new StringValue(attributeName));
        }
        return result;
    }
}
