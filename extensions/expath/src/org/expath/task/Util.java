/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.expath.task;

import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.ValueSequence;

public class Util {

    static Sequence sequenceOf(final RealWorld realWorld, final Sequence other) throws XPathException {
        final ValueSequence newSequence = new ValueSequence();
        newSequence.add(realWorld);
        newSequence.addAll(other);
        return newSequence;
    }

    static Sequence sequenceOf(final Item... items) {
        return new ValueSequence(items);
    }


    static Item head(final Sequence sequence) {
        return sequence.itemAt(0);
    }

    static Sequence tail(final Sequence sequence) throws XPathException {
        return sequence.tail();
    }
}
