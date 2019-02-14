/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2010 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.modules.sort;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.indexing.sort.SortIndex;
import org.exist.indexing.sort.SortIndexWorker;
import org.exist.util.LockException;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

public class HasIndex extends BasicFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("has-index", SortModule.NAMESPACE_URI, SortModule.PREFIX),
                    "Check if the sort index, $id, exists.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("id", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the index.")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if the sort index, $id, exists, false() otherwise."));

    public HasIndex(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String id = args[0].getStringValue();
        final SortIndexWorker index = (SortIndexWorker)
                context.getBroker().getIndexController().getWorkerByIndexId(SortIndex.ID);
        try {
            return BooleanValue.valueOf(index.hasIndex(id));
        } catch (final EXistException e) {
            throw new XPathException(this, e.getMessage(), e);
        } catch (final LockException e) {
            throw new XPathException(this, "Caught lock error while searching index. Giving up.", e);
        }
    }
}
