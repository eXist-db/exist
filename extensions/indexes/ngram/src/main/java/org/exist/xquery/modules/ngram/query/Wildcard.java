/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.xquery.modules.ngram.query;

public class Wildcard implements WildcardedExpression, MergeableExpression {
    final int minimumLength;
    final int maximumLength;

    public int getMinimumLength() {
        return minimumLength;
    };

    public int getMaximumLength() {
        return maximumLength;
    };

    public Wildcard(final int minimumLength, final int maximumLength) {
        this.minimumLength = minimumLength;
        this.maximumLength = maximumLength;
    }

    @Override
    public String toString() {
        return "Wildcard(" + minimumLength + ", " + maximumLength + ")";
    }

    @Override
    public WildcardedExpression mergeWith(final WildcardedExpression otherExpression) {
        Wildcard other = (Wildcard) otherExpression;
        int newMaximumLength = (this.maximumLength == Integer.MAX_VALUE || other.maximumLength == Integer.MAX_VALUE) ? Integer.MAX_VALUE
            : this.maximumLength + other.maximumLength;
        return new Wildcard(this.minimumLength + other.minimumLength, newMaximumLength);
    }

    @Override
    public boolean mergeableWith(final WildcardedExpression otherExpression) {
        return (otherExpression instanceof Wildcard);
    }
}
