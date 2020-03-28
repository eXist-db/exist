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

import static org.exist.xquery.Cardinality.InternalValue.*;

/**
 * Defines <a href="https://www.w3.org/TR/xpath-31/#prod-xpath31-OccurrenceIndicator">XPath Occurrence Indicators</a>
 * (*,?,+), and additionally defines {@link #EMPTY_SEQUENCE}, and {@link #EXACTLY_ONE} for convenience.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public enum Cardinality {

    EMPTY_SEQUENCE(ZERO),

    //TODO(AR) can we eliminate this?
    EXACTLY_ONE(ONE),

    //TODO(AR) eliminate this in favour of probably ONE_OR_MORE
    _MANY(MANY),

    /**
     * indicator '?'
     */
    ZERO_OR_ONE((byte)(ZERO | ONE)),

    /**
     * indicator '+'
     */
    ONE_OR_MORE((byte)(ONE | MANY)),

    /**
     * indicator '*'
     */
    ZERO_OR_MORE((byte)(ZERO | ONE | MANY));


    private final byte val;

    Cardinality(final byte val) {
        this.val = val;
    }

    /**
     * The cardinality represents a sequence of at least one value.
     *
     * @return true if the cardinality represents a sequence of at least one value, or false otherwise.
     */
    public boolean atLeastOne() {
        return (val & ZERO) == 0;
    }

    /**
     * The cardinality represents a sequence of at most one value.
     *
     * @return true if the cardinality represents a sequence of at most one value, or false otherwise.
     */
    public boolean atMostOne() {
        return (val & MANY) == 0;
    }

    /**
     * Tests whether this Cardinality is a sub-cardinality or equal
     * of {@code other}.
     *
     * @param other the other cardinality
     *
     * @return true if this is a sub-cardinality or equal cardinality of {@code other}.
     */
    public boolean isSubCardinalityOrEqualOf(final Cardinality other) {
        return (val | other.val) == other.val;
    }

    /**
     * Tests whether this Cardinality is a super-cardinality or equal
     * of {@code other}.
     *
     * @param other the other cardinality
     *
     * @return true if this is a super-cardinality or equal cardinality of {@code other}.
     */
    public boolean isSuperCardinalityOrEqualOf(final Cardinality other) {
        return (val & other.val) == other.val;
    }

    /**
     * Given two cardinalities, return a cardinality that is capable of
     * representing both,
     * i.e.: {@code a.isSubCardinalityOrEqualOf(max(a, b)) && b.isSubCardinalityOrEqualOf(max(a, b)) && b.max(a, b))
     *
     * @param a the first cardinality
     * @param b the second cardinality
     */
    public static Cardinality superCardinalityOf(final Cardinality a, final Cardinality b) {
        final byte max = (byte)(a.val | b.val);
        for (final Cardinality cardinality : Cardinality.values()) {
            if (cardinality.val == max) {
                return cardinality;
            }
        }
        throw new IllegalStateException();
    }

    /**
     * Get an XQuery notation representation of the cardinality.
     *
     * @return the XQuery notation
     */
    public String toXQueryCardinalityString() {
        switch (this) {
            case EMPTY_SEQUENCE:
                return "empty-sequence()";

            case EXACTLY_ONE:
                return "";

            case ZERO_OR_ONE:
                return "?";

            case _MANY:
            case ONE_OR_MORE:
                return "+";

            case ZERO_OR_MORE:
                return "*";

            default:
                // impossible
                throw new IllegalArgumentException("Unknown cardinality: " + name());
        }
    }

    /**
     * Get a human pronounceable description of the cardinality.
     *
     * @return a pronounceable description
     */
    public String getHumanDescription() {
        switch (this) {
            case EMPTY_SEQUENCE:
                return "empty";
            case EXACTLY_ONE:
                return "exactly one";
            case ZERO_OR_ONE:
                return "zero or one";

            case _MANY:
            case ONE_OR_MORE:
                return "one or more";

            case ZERO_OR_MORE:
                return "zero or more";
            default:
                // impossible
                throw new IllegalArgumentException("Unknown cardinality: " + name());
        }
    }

    static class InternalValue {
        static final byte ZERO = 1;
        static final byte ONE = 2;
        static final byte MANY = 4;
    }
}
