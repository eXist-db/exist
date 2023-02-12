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
package org.exist.storage.util;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.IntegerValue;
import org.exist.dom.QName;

public class PauseFunction extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("pause", TestUtilModule.NAMESPACE_URI),
            "Pause for the specified number of seconds.",
            new SequenceType[] { new FunctionParameterSequenceType("seconds", Type.INT, Cardinality.EXACTLY_ONE, "Seconds to pause.") },
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
        );

    public PauseFunction(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        int t = ((IntegerValue)args[0].itemAt(0)).getInt();
        synchronized (this) {
            try {
                wait(t * 1000);
            } catch (InterruptedException e) {
            }
        }
        return Sequence.EMPTY_SEQUENCE;
    }
}
