/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist Project
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
package org.exist.xquery;

import java.util.Set;
import java.util.TreeSet;

import org.exist.xquery.value.Item;
import org.exist.xquery.value.ItemComparator;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class Except extends CombiningExpression {

    public Except(final XQueryContext context, final PathExpr left, final PathExpr right) {
        super(context, left, right);
    }

    @Override
    public Sequence combine(final Sequence ls, final Sequence rs) throws XPathException {
        final Sequence result;
        if (ls.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else if (rs.isEmpty()) {
            if (!Type.subTypeOf(ls.getItemType(), Type.NODE)) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "except operand is not a node sequence");
            }
            result = ls;
        } else {
            if (!(Type.subTypeOf(ls.getItemType(), Type.NODE) && Type.subTypeOf(rs.getItemType(), Type.NODE))) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "except operand is not a node sequence");
            }
            if (ls.isPersistentSet() && rs.isPersistentSet()) {
                result = ls.toNodeSet().except(rs.toNodeSet());
            } else {
                result = new ValueSequence();
                final Set<Item> set = new TreeSet<>(ItemComparator.INSTANCE);
                for (final SequenceIterator i = rs.unorderedIterator(); i.hasNext(); ) {
                    set.add(i.nextItem());
                }
                for (final SequenceIterator i = ls.unorderedIterator(); i.hasNext(); ) {
                    final Item next = i.nextItem();
                    if (!set.contains(next)) {
                        result.add(next);
                    }
                }
                result.removeDuplicates();
            }
        }

        return result;
    }

    @Override
    protected String getOperatorName() {
        return "except";
    }
}
