/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
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

import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Optional;


/**
 * @author wolf
 */
public class Invalidate extends SessionFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("invalidate", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
                    "Invalidate (remove) the current HTTP session if present",
                    null,
                    new SequenceType(Type.EMPTY, Cardinality.EMPTY));

    public Invalidate(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Optional<SessionWrapper> session)
            throws XPathException {
        if (!session.isPresent()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        session.get().invalidate();
        return Sequence.EMPTY_SEQUENCE;
    }
}
