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

import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.exist.xquery.Cardinality.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class CardinalityTest {

    @Test
    public void atLeastOne() {
        assertFalse(EMPTY_SEQUENCE.atLeastOne());
        assertFalse(ZERO_OR_ONE.atLeastOne());
        assertTrue(ONE_OR_MORE.atLeastOne());
        assertFalse(ZERO_OR_MORE.atLeastOne());
    }

    @Test
    public void atMostOne() {
        assertTrue(EMPTY_SEQUENCE.atMostOne());
        assertTrue(ZERO_OR_ONE.atMostOne());
        assertFalse(ONE_OR_MORE.atMostOne());
        assertFalse(ZERO_OR_MORE.atMostOne());
    }

    @Test
    public void isSubCardinalityOrEqualOf() {
        isSubCardinalityOrEqualOf(EMPTY_SEQUENCE, EMPTY_SEQUENCE);
        notSubCardinalityOrEqualOf(EMPTY_SEQUENCE, EXACTLY_ONE);
        isSubCardinalityOrEqualOf(EMPTY_SEQUENCE, ZERO_OR_ONE);
        notSubCardinalityOrEqualOf(EMPTY_SEQUENCE, ONE_OR_MORE);
        isSubCardinalityOrEqualOf(EMPTY_SEQUENCE, ZERO_OR_MORE);

        notSubCardinalityOrEqualOf(EXACTLY_ONE, EMPTY_SEQUENCE);
        isSubCardinalityOrEqualOf(EXACTLY_ONE, EXACTLY_ONE);
        isSubCardinalityOrEqualOf(EXACTLY_ONE, ZERO_OR_ONE);
        isSubCardinalityOrEqualOf(EXACTLY_ONE, ONE_OR_MORE);
        isSubCardinalityOrEqualOf(EXACTLY_ONE, ZERO_OR_MORE);

        notSubCardinalityOrEqualOf(ZERO_OR_ONE, EMPTY_SEQUENCE);
        notSubCardinalityOrEqualOf(ZERO_OR_ONE, EXACTLY_ONE);
        isSubCardinalityOrEqualOf(ZERO_OR_ONE, ZERO_OR_ONE);
        notSubCardinalityOrEqualOf(ZERO_OR_ONE, ONE_OR_MORE);
        isSubCardinalityOrEqualOf(ZERO_OR_ONE, ZERO_OR_MORE);

        notSubCardinalityOrEqualOf(ONE_OR_MORE, EMPTY_SEQUENCE);
        notSubCardinalityOrEqualOf(ONE_OR_MORE, EXACTLY_ONE);
        notSubCardinalityOrEqualOf(ONE_OR_MORE, ZERO_OR_ONE);
        isSubCardinalityOrEqualOf(ONE_OR_MORE, ONE_OR_MORE);
        isSubCardinalityOrEqualOf(ONE_OR_MORE, ZERO_OR_MORE);

        notSubCardinalityOrEqualOf(ZERO_OR_MORE, EMPTY_SEQUENCE);
        notSubCardinalityOrEqualOf(ZERO_OR_MORE, EXACTLY_ONE);
        notSubCardinalityOrEqualOf(ZERO_OR_MORE, ZERO_OR_ONE);
        notSubCardinalityOrEqualOf(ZERO_OR_MORE, ONE_OR_MORE);
        isSubCardinalityOrEqualOf(ZERO_OR_MORE, ZERO_OR_MORE);
    }

    private static void isSubCardinalityOrEqualOf(final Cardinality subject, final Cardinality test) {
        assertTrue(subject.name() + ".isSubCardinalityOrEqualOf(" + test.name() + ") == false, expected true",
                subject.isSubCardinalityOrEqualOf(test));
    }

    private static void notSubCardinalityOrEqualOf(final Cardinality subject, final Cardinality test) {
        assertFalse(subject.name() + ".isSubCardinalityOrEqualOf(" + test.name() + ") == true, expected false",
                subject.isSubCardinalityOrEqualOf(test));
    }

    @Test
    public void isSuperCardinalityOf() {
        isSuperCardinalityOrEqualOf(EMPTY_SEQUENCE, EMPTY_SEQUENCE);
        notSuperCardinalityOrEqualOf(EMPTY_SEQUENCE, EXACTLY_ONE);
        notSuperCardinalityOrEqualOf(EMPTY_SEQUENCE, ZERO_OR_ONE);
        notSuperCardinalityOrEqualOf(EMPTY_SEQUENCE, ONE_OR_MORE);
        notSuperCardinalityOrEqualOf(EMPTY_SEQUENCE, ZERO_OR_MORE);

        notSuperCardinalityOrEqualOf(EXACTLY_ONE, EMPTY_SEQUENCE);
        isSuperCardinalityOrEqualOf(EXACTLY_ONE, EXACTLY_ONE);
        notSuperCardinalityOrEqualOf(EXACTLY_ONE, ZERO_OR_ONE);
        notSuperCardinalityOrEqualOf(EXACTLY_ONE, ONE_OR_MORE);
        notSuperCardinalityOrEqualOf(EXACTLY_ONE, ZERO_OR_MORE);

        isSuperCardinalityOrEqualOf(ZERO_OR_ONE, EMPTY_SEQUENCE);
        isSuperCardinalityOrEqualOf(ZERO_OR_ONE, EXACTLY_ONE);
        isSuperCardinalityOrEqualOf(ZERO_OR_ONE, ZERO_OR_ONE);
        notSuperCardinalityOrEqualOf(ZERO_OR_ONE, ONE_OR_MORE);
        notSuperCardinalityOrEqualOf(ZERO_OR_ONE, ZERO_OR_MORE);

        notSuperCardinalityOrEqualOf(ONE_OR_MORE, EMPTY_SEQUENCE);
        isSuperCardinalityOrEqualOf(ONE_OR_MORE, EXACTLY_ONE);
        notSuperCardinalityOrEqualOf(ONE_OR_MORE, ZERO_OR_ONE);
        isSuperCardinalityOrEqualOf(ONE_OR_MORE, ONE_OR_MORE);
        notSuperCardinalityOrEqualOf(ONE_OR_MORE, ZERO_OR_MORE);

        isSuperCardinalityOrEqualOf(ZERO_OR_MORE, EMPTY_SEQUENCE);
        isSuperCardinalityOrEqualOf(ZERO_OR_MORE, EXACTLY_ONE);
        isSuperCardinalityOrEqualOf(ZERO_OR_MORE, ZERO_OR_ONE);
        isSuperCardinalityOrEqualOf(ZERO_OR_MORE, ONE_OR_MORE);
        isSuperCardinalityOrEqualOf(ZERO_OR_MORE, ZERO_OR_MORE);
    }

    private static void isSuperCardinalityOrEqualOf(final Cardinality subject, final Cardinality test) {
        assertTrue(subject.name() + ".isSuperCardinalityOrEqualOf(" + test.name() + ") == false, expected true",
                subject.isSuperCardinalityOrEqualOf(test));
    }

    private static void notSuperCardinalityOrEqualOf(final Cardinality subject, final Cardinality test) {
        assertFalse(subject.name() + ".isSuperCardinalityOrEqualOf(" + test.name() + ") == true, expected false",
                subject.isSuperCardinalityOrEqualOf(test));
    }

    @Test
    public void superCardinalityOf() {
        assertEquals(EMPTY_SEQUENCE, Cardinality.superCardinalityOf(EMPTY_SEQUENCE, EMPTY_SEQUENCE));
        assertEquals(ZERO_OR_ONE, Cardinality.superCardinalityOf(EMPTY_SEQUENCE, EXACTLY_ONE));
        assertEquals(ZERO_OR_ONE, Cardinality.superCardinalityOf(EMPTY_SEQUENCE, ZERO_OR_ONE));
        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(EMPTY_SEQUENCE, ONE_OR_MORE));
        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(EMPTY_SEQUENCE, ZERO_OR_MORE));

        assertEquals(ZERO_OR_ONE, Cardinality.superCardinalityOf(EXACTLY_ONE, EMPTY_SEQUENCE));
        assertEquals(EXACTLY_ONE, Cardinality.superCardinalityOf(EXACTLY_ONE, EXACTLY_ONE));
        assertEquals(ZERO_OR_ONE, Cardinality.superCardinalityOf(EXACTLY_ONE, ZERO_OR_ONE));
        assertEquals(ONE_OR_MORE, Cardinality.superCardinalityOf(EXACTLY_ONE, ONE_OR_MORE));
        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(EXACTLY_ONE, ZERO_OR_MORE));

        assertEquals(ZERO_OR_ONE, Cardinality.superCardinalityOf(ZERO_OR_ONE, EMPTY_SEQUENCE));
        assertEquals(ZERO_OR_ONE, Cardinality.superCardinalityOf(ZERO_OR_ONE, EXACTLY_ONE));
        assertEquals(ZERO_OR_ONE, Cardinality.superCardinalityOf(ZERO_OR_ONE, ZERO_OR_ONE));
        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(ZERO_OR_ONE, ONE_OR_MORE));
        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(ZERO_OR_ONE, ZERO_OR_MORE));

        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(ONE_OR_MORE, EMPTY_SEQUENCE));
        assertEquals(ONE_OR_MORE, Cardinality.superCardinalityOf(ONE_OR_MORE, EXACTLY_ONE));
        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(ONE_OR_MORE, ZERO_OR_ONE));
        assertEquals(ONE_OR_MORE, Cardinality.superCardinalityOf(ONE_OR_MORE, ONE_OR_MORE));
        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(ONE_OR_MORE, ZERO_OR_MORE));

        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(ZERO_OR_MORE, EMPTY_SEQUENCE));
        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(ZERO_OR_MORE, EXACTLY_ONE));
        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(ZERO_OR_MORE, ZERO_OR_ONE));
        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(ZERO_OR_MORE, ONE_OR_MORE));
        assertEquals(ZERO_OR_MORE, Cardinality.superCardinalityOf(ZERO_OR_MORE, ZERO_OR_MORE));
    }
}
