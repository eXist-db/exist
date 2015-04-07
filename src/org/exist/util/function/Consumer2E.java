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
 * Similar to {@link org.exist.util.function.ConsumerE} but
 * permits two statically know Exceptions to be thrown
 *
 * @param <T> the type of the input to the operation
 * @param <E1> Function throws exception type
 * @param <E2> Function throws exception type
 */
@FunctionalInterface
public interface Consumer2E<T, E1 extends Throwable, E2 extends Throwable> {
    void accept(T t) throws E1, E2;
}
