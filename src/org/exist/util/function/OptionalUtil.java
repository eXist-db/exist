/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2016 The eXist Project
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

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Functional utility methods that are missing from {@link java.util.Optional}
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class OptionalUtil {

    /**
     * Return the left Optional if present, else thr right Optional
     *
     * @param left The left of the disjunction
     * @param right The right of the disjunction
     *
     * @return left if present, else right
     */
    public static <T> Optional<T> or(final Optional<T> left, final Optional<T> right) {
        if(left.isPresent()) {
            return left;
        } else {
            return right;
        }
    }

    /**
     * A lazy version of {@link OptionalUtil#or(Optional, Optional)}
     *
     * @param left The left of the disjunction
     * @param right A lazily evaluated supplier of Optional for the right of the disjunction,
     *              only evaluated if the left is empty
     *
     * @param left if present, else the evaluation of the the right
     */
    public static <T> Optional<T> or(final Optional<T> left, final Supplier<Optional<T>> right) {
        if(left.isPresent()) {
            return left;
        } else {
            return right.get();
        }
    }
}
