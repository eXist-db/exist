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
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

public class LineNumber extends BasicFunction {

    protected static final Logger logger = LogManager.getLogger(LineNumber.class);

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("line-number", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Retrieves the line number of the expression",
            null,
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "The line number of this expression")
    );

    public LineNumber(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence)
            throws XPathException {
        return new IntegerValue(this, getLine());
    }
}
