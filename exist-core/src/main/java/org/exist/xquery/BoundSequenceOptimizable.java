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

package org.exist.xquery;

/**
 * An {@link Optimizable} whose input can be supplied by an enclosing binding rather than by its
 * own arguments.
 *
 * <p>A function that takes a single item cannot be handed a path directly without violating its
 * signature, so the conformant way to ask "does any of these match?" is to quantify over the path:
 * {@code some $v in val satisfies matches($v, 'a')}. That spelling hides the path from the
 * function, which would otherwise have no way to know which QName a range index should be
 * consulted for.</p>
 *
 * <p>Implementing this lets the optimizer hand that path back, so the conformant spelling is not
 * also the slow one.</p>
 */
public interface BoundSequenceOptimizable extends Optimizable {

    /**
     * Supplies the sequence an enclosing quantified expression binds this call's input to, so that
     * index metadata can be derived from it instead of from the call's own arguments.
     *
     * <p>Called during static analysis, before {@link #canOptimizeSequence(org.exist.xquery.value.Sequence)}.</p>
     *
     * @param boundSequence the quantifier's input sequence
     */
    void optimizeOverBoundSequence(Expression boundSequence);

    /**
     * Whether the optimizer has called {@link #optimizeOverBoundSequence} on this call, that is,
     * whether it approved serving the enclosing quantified expression from the index.
     *
     * @return true if this call is optimized over its quantifier's input sequence
     */
    boolean isOptimizedOverBoundSequence();
}
