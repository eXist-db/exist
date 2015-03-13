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

import java.util.Objects;

/**
 * Similar to {@link java.util.function.Function} but
 * permits a single statically know Exception to be thrown
 *
 * @param <T> Function parameter type
 * @param <R> Function return type
 * @param <E> Function throws exception type
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
@FunctionalInterface
public interface FunctionE<T, R, E extends Throwable> {
    R apply(final T t) throws E;

    default <V> FunctionE<T, V, E> andThen(FunctionE<? super R, ? extends V, ? extends E> after) {
        Objects.requireNonNull(after);
        return (T t) -> after.apply(apply(t));
    }
}
