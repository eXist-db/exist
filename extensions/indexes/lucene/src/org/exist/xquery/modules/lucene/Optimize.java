/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * \$Id\$
 */

package org.exist.xquery.modules.lucene;

import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

public class Optimize extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("optimize", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Calls Lucene's optimize method to merge all index segments " +
            "into a single one. This is a costly operation and should not be used " +
            "except for data sets which can be expected to remain unchanged for a while. " +
            "The optimize will block the index for other write operations and may take " +
            "some time. You need to be a user in group dba to call this function.",
            new SequenceType[0],
            new FunctionReturnSequenceType(Type.EMPTY, Cardinality.ZERO, "")
        );

    public Optimize(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (!context.getUser().hasDbaRole())
            throw new XPathException(this, "user has to be a member of the dba group to call " +
                "the optimize function. Calling user was " + context.getUser().getName());
        LuceneIndexWorker index = (LuceneIndexWorker)
                context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        index.optimize();
        return Sequence.EMPTY_SEQUENCE;
    }
}
