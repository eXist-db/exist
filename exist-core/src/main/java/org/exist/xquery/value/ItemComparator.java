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
package org.exist.xquery.value;

import org.exist.xquery.Constants;

import java.util.Comparator;

/**
 * Comparator for comparing instances of Item
 * apart from the XQuery atomic types there are
 * two Node types in eXist org.exist.dom.persistent.*
 * and org.exist.dom.memtree.* this class is
 * used so that both types can be compared to each other
 * as Item even though they have quite different inheritance
 * hierarchies.
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ItemComparator implements Comparator<Item> {

    public final static ItemComparator INSTANCE = new ItemComparator();

    private ItemComparator() {
    }

    @Override
    public int compare(final Item n1, final Item n2) {
        if (n1 instanceof org.exist.dom.memtree.NodeImpl && (!(n2 instanceof org.exist.dom.memtree.NodeImpl))) {
            return Constants.INFERIOR;
        } else if (n1 instanceof Comparable) {
            return ((Comparable) n1).compareTo(n2);
        } else {
            return Constants.INFERIOR;
        }
    }
}
