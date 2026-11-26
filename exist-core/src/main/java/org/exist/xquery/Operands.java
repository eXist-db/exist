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

import java.util.function.Consumer;

/**
 * An expression that lists its operands, the expressions it evaluates or hands the focus to.
 *
 * <p>The dependency report of such an expression is derived: its {@link AbstractExpression#ownDependencies() own
 * contribution} plus what each operand reads of the focus. An expression that holds operands and does not implement this
 * interface reports {@link Dependency#CONSERVATIVE_DEPENDENCIES}, so a class that forgets to list an operand costs speed,
 * not a wrong answer. Whoever adds a field of an expression type adds it to {@link #forEachOperand(Consumer)}.</p>
 */
public interface Operands {

    /**
     * @param action called with each operand, in no particular order
     */
    void forEachOperand(Consumer<Expression> action);

    /**
     * Passes the non-null operands to the action.
     *
     * @param action the consumer of the operands
     * @param operands the operands, any of which may be null
     */
    static void each(final Consumer<Expression> action, final Expression... operands) {
        for (final Expression operand : operands) {
            if (operand != null) {
                action.accept(operand);
            }
        }
    }

    /**
     * Passes the non-null operands to the action.
     *
     * @param action the consumer of the operands
     * @param operands the operands, any of which may be null
     */
    static void each(final Consumer<Expression> action, final Iterable<? extends Expression> operands) {
        if (operands != null) {
            for (final Expression operand : operands) {
                if (operand != null) {
                    action.accept(operand);
                }
            }
        }
    }
}
