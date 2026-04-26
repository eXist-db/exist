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

import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compilation context passed to {@link Expression#optimize(CompileContext)}.
 *
 * Carries the {@link XQueryContext}, captures rewrite decisions for visibility
 * (consumed by {@code util:explain}-style diagnostics), and records whether any
 * rewrite occurred so the driver knows to re-analyze.
 *
 * The {@link #replaceWith(Expression, Expression, String)} helper is the
 * preferred return path: each {@code optimize()} method computes its
 * replacement and returns {@code cc.replaceWith(this, replacement, reason)}.
 */
public final class CompileContext {

    public final XQueryContext qc;

    private final List<String> log = new ArrayList<>();
    private boolean changed;

    public CompileContext(final XQueryContext qc) {
        this.qc = qc;
    }

    /**
     * Records an expression rewrite and returns the replacement.
     *
     * If {@code replacement == original}, this is a no-op and nothing is
     * logged. Callers can therefore unconditionally return
     * {@code cc.replaceWith(this, candidate, reason)} — when the candidate
     * happens to be {@code this}, the log stays clean.
     *
     * @param original    the expression being rewritten
     * @param replacement the replacement (may be {@code original})
     * @param reason      short human-readable cause, e.g. {@code "constant fold"}
     * @return {@code replacement}
     */
    public Expression replaceWith(final Expression original, final Expression replacement,
                                  final String reason) {
        if (replacement != original) {
            changed = true;
            log.add(String.format("REWRITE %s → %s (%s)",
                    abbreviate(original), abbreviate(replacement), reason));
        }
        return replacement;
    }

    /** Free-form info entry for diagnostics. */
    public void info(final String fmt, final Object... args) {
        log.add(args.length == 0 ? fmt : String.format(fmt, args));
    }

    /**
     * Pre-evaluates an expression with no dependencies and returns a literal
     * wrapping the result. Caller is responsible for ensuring
     * {@code expr.getDependencies() == Dependency.NO_DEPENDENCY}.
     *
     * @param expr expression to pre-evaluate
     * @return a {@link LiteralValue} wrapping the result, or {@code expr}
     *         itself if the result is not an {@link AtomicValue}
     * @throws XPathException if evaluation fails
     */
    public Expression preEval(final Expression expr) throws XPathException {
        final Sequence value = expr.eval(null, null);
        if (value.hasOne() && value.itemAt(0) instanceof AtomicValue atom) {
            final LiteralValue literal = new LiteralValue(qc, atom);
            literal.setLocation(expr.getLine(), expr.getColumn());
            return replaceWith(expr, literal, "constant fold");
        }
        return expr;
    }

    /** True if any {@link #replaceWith} actually swapped expressions. */
    public boolean hasOptimized() {
        return changed;
    }

    /** Read-only view of the log. */
    public List<String> log() {
        return Collections.unmodifiableList(log);
    }

    private static String abbreviate(final Expression e) {
        if (e == null) {
            return "<null>";
        }
        final String s = e.toString();
        if (s.length() <= 60) {
            return s;
        }
        return s.substring(0, 57) + "...";
    }
}
