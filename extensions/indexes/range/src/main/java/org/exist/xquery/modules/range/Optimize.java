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
package org.exist.xquery.modules.range;

import org.exist.dom.QName;
import org.exist.indexing.range.RangeIndex;
import org.exist.indexing.range.RangeIndexWorker;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class Optimize extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("optimize", RangeIndexModule.NAMESPACE_URI, RangeIndexModule.PREFIX),
            "Calls Lucene's optimize method to merge all index segments " +
            "into a single one. This is a costly operation and should not be used " +
            "except for data sets which can be expected to remain unchanged for a while. " +
            "The optimize will block the index for other write operations and may take " +
            "some time. You need to be a user in group dba to call this function.",
            new SequenceType[0],
            new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE, "")
        );

    public Optimize(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (!context.getSubject().hasDbaRole())
            throw new XPathException(this, "user has to be a member of the dba group to call " +
                "the optimize function. Calling user was " + context.getSubject().getName());
        RangeIndexWorker index = (RangeIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(RangeIndex.ID);
        index.optimize();
        return Sequence.EMPTY_SEQUENCE;
    }
}
