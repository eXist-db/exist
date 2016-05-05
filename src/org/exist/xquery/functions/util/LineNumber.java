/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-16 The eXist Project
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
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ONE, "The line number of this expression")
    );

    public LineNumber(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence)
            throws XPathException {
        return new IntegerValue(getLine());
    }
}
