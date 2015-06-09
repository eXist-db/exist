/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.util.function;

/**
 * A tuple of two values
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 *
 * @param <T1> The type of the first value
 * @param <T2> The type of the second value
 */
public class Tuple2<T1, T2> {
    public final T1 _1;
    public final T2 _2;

    public Tuple2(final T1 _1, final T2 _2) {
        this._1 = _1;
        this._2 = _2;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof Tuple2) {
            final Tuple2 other = (Tuple2)obj;
            return _1.equals(other._1) && _2.equals(other._2);
        }
        return false;
    }
}
