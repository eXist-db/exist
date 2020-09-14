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
package org.exist.xquery.value;

public class SingleItemIterator implements SequenceIterator {

    private final Item item;
    private boolean more = true;

    public SingleItemIterator(final Item item) {
        this.item = item;
    }

    @Override
    public boolean hasNext() {
        return more;
    }

    @Override
    public Item nextItem() {
        if (!more) {
            return null;
        }
        more = false;
        return item;
    }

    @Override
    public long skippable() {
        if (more) {
            return 1;
        }
        return 0;
    }

    @Override
    public long skip(final long n) {
        final long skip = Math.min(n, more ? 1 : 0);
        if (skip == 1) {
            more = false;
        }
        return skip;
    }
}
