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
package org.exist.xquery.modules.range;

import com.ibm.icu.text.Collator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.indexing.range.RangeIndex;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.GeneralComparison;
import org.exist.xquery.InternalFunctionCall;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;

/**
 * Decides whether a range index may answer a comparison that names a collation: a collation argument, or a default
 * collation other than codepoint (see {@link GeneralComparison#hasExplicitCollation()}). A comparison that names none
 * is always answered with the index's own configuration, by design.
 */
final class IndexCollation {

    private static final Logger LOG = LogManager.getLogger(IndexCollation.class);

    private IndexCollation() {
        // static helpers only
    }

    /**
     * The comparison a predicate expression stands for, if it names a collation: the comparison itself, or the one a
     * {@link Lookup} replaced.
     *
     * @param expression a predicate's expression
     * @return the comparison, or null if the expression is not a comparison or names no collation
     */
    static @Nullable GeneralComparison namingCollation(final Expression expression) {
        Expression candidate = expression;
        if (expression instanceof final InternalFunctionCall call) {
            final Function function = call.getFunction();
            candidate = function instanceof final Lookup lookup ? lookup.getFallback() : function;
        }
        return candidate instanceof final GeneralComparison comparison && comparison.hasExplicitCollation()
                ? comparison : null;
    }

    /**
     * Whether an index can answer a comparison in the collation the comparison names.
     *
     * @param comparison the comparison, which names a collation
     * @param contextSequence the context, to evaluate a collation argument against
     * @param indexCollator the index's collator, or null if it compares by codepoint or with {@code case="no"}
     * @param caseSensitive false if the index compares case-insensitively ({@code case="no"})
     * @param operator the index operator the comparison becomes
     * @return true if the index may answer the comparison
     */
    static boolean answers(final GeneralComparison comparison, final Sequence contextSequence,
            @Nullable final Collator indexCollator, final boolean caseSensitive, final RangeIndex.Operator operator) {
        final Collator requested;
        try {
            requested = comparison.getCollator(contextSequence);
        } catch (final XPathException e) {
            // the comparison reports the error when it is evaluated
            return false;
        }
        if (indexCollator == null) {
            // compares by codepoint, unless case="no" makes it case-insensitive
            return requested == null && caseSensitive;
        }
        // a collated index cannot serve substring operators; evaluated as written they honour the collation. An index that
        // also folds case (case="no") keeps its keys in lower case, which a collation that tells the cases apart does not
        // match: it declines, whatever collation the query names.
        return caseSensitive && operator.supportsCollation() && indexCollator.equals(requested);
    }

    /**
     * Reports that an index steps aside because of a collation, so that a slower query can be explained.
     *
     * @param expression the expression the message is about
     * @param what the index, as the message names it
     */
    static void reportDeclined(final Expression expression, final String what) {
        final String message = what + " not used: the query names a collation the index does not compare in";
        if (LOG.isTraceEnabled()) {
            LOG.trace("{}: {}", message, expression);
        }
        final Profiler profiler = expression.getContext().getProfiler();
        if (profiler.isEnabled()) {
            profiler.message(expression, Profiler.OPTIMIZATIONS, "OPTIMIZATION", message);
        }
    }
}
