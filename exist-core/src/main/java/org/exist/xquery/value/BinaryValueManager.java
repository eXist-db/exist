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

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public interface BinaryValueManager {

    void registerBinaryValueInstance(final BinaryValue binaryValue);

    /**
     * Deregister a value which has already been released.
     *
     * <p>A manager which does not track scopes need not implement this.</p>
     *
     * @param binaryValue the value to deregister
     */
    default void destroyBinaryValue(final BinaryValue binaryValue) {
    }

    /**
     * Open a frame which owns every {@link BinaryValue} registered while it is the innermost one.
     *
     * <p>Ownership is what decides who may release a value: the scope that created it, never a
     * scope which merely received it - so a called function cannot close its caller's value.</p>
     *
     * <p>A manager which does not track scopes need not implement this; its values are then
     * released only by its own cleanup.</p>
     */
    default void pushBinaryValueFrame() {
    }

    /**
     * Close the innermost frame and release the values it owns.
     *
     * @param escaping values reachable from this sequence leave the frame rather than being
     *     released, and are owned by the enclosing frame from now on; null releases them all
     */
    default void popBinaryValueFrame(@Nullable final Sequence escaping) {
    }

    /**
     * Close the innermost frame, handing everything it owns to the enclosing frame.
     *
     * <p>For scopes whose escape set is unknown: deferring release to the end of the query is
     * recoverable, closing a value the query still needs is not.</p>
     */
    default void promoteBinaryValueFrame() {
    }

    void runCleanupTasks(final Predicate<Object> predicate);
    default void runCleanupTasks() {
        runCleanupTasks(o -> true);
    }

    String getCacheClass();
}
