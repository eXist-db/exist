/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery.value;

import com.googlecode.junittoolbox.ParallelParameterized;
import org.exist.xquery.Cardinality;
import org.exist.xquery.RangeSequence;
import org.exist.xquery.XPathException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@RunWith(ParallelParameterized.class)
public class SubSequenceRangeTest {

    private static final long RANGE_START = 1;
    private static final long RANGE_END = 99;

    @Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"0 until 1",       0,      1,  0},
                {"0 until 100",     0,    100, 99},
                {"1 until 100",     1,    100, 99},
                {"2 until 100",     2,    100, 98},
                {"10 until 90",    10,     90, 80},
                {"1 until 99",      1,     99, 98},
                {"1 until 100",     1,    100, 99},
                {"1 until 101",     1,    101, 99},
                {"-1 until 110",   -1,    110, 99},
                {"-4 until 6",     -4,      6,  5},
                {"-4 until -7",    -4,     -7,  0},
                {"99 until 100",   99,    100,  1},
                {"100 until 101",  100,   101,  0}
        });
    }

    @Parameter
    public String subSequenceStartEndName;

    @Parameter(value = 1)
    public long fromInclusive;

    @Parameter(value = 2)
    public int toExclusive;

    @Parameter(value = 3)
    public int expectedSubsequenceLength;

    private static final RangeSequence range = new RangeSequence(new IntegerValue(RANGE_START), new IntegerValue(RANGE_END));

    private SubSequence getSubsequence() {
        return new SubSequence(fromInclusive, toExclusive, range);
    }

    @Test
    public void getItemCount() {
        assertEquals(expectedSubsequenceLength, getSubsequence().getItemCount());
    }

    @Test
    public void isEmpty() {
        if (expectedSubsequenceLength == 0) {
            assertTrue(getSubsequence().isEmpty());
        } else {
            assertFalse(getSubsequence().isEmpty());
        }
    }

    @Test
    public void hasOne() {
        if (expectedSubsequenceLength == 1) {
            assertTrue(getSubsequence().hasOne());
        } else {
            assertFalse(getSubsequence().hasOne());
        }
    }

    @Test
    public void hasMany() {
        if (expectedSubsequenceLength > 1) {
            assertTrue(getSubsequence().hasMany());
        } else {
            assertFalse(getSubsequence().hasMany());
        }
    }

    @Test
    public void getCardinality() {
        final int expectedCardinality;
        if (expectedSubsequenceLength == 0) {
            expectedCardinality = Cardinality.EMPTY;
        } else if (expectedSubsequenceLength == 1) {
            expectedCardinality = Cardinality.ONE;
        } else {
            expectedCardinality = Cardinality.MANY;
        }
        assertEquals(expectedCardinality, getSubsequence().getCardinality());
    }

    @Test
    public void iterate_loop() throws XPathException {
        final SequenceIterator it = getSubsequence().iterate();
        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(expectedSubsequenceLength, count);
    }

    @Test
    public void iterate_skip_loop() throws XPathException {
        final SequenceIterator it = getSubsequence().iterate();

        assertEquals(expectedSubsequenceLength, it.skippable());

        final long skipped = it.skip(2);
        assertTrue(skipped <= 2);
        final long remaining = expectedSubsequenceLength - skipped;

        assertEquals(remaining, it.skippable());

        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(remaining, count);
    }

    @Test
    public void iterate_loop_skip_loop() throws XPathException {
        final SequenceIterator it = getSubsequence().iterate();

        final int loopOneMax = 5;
        int count = 0;
        for (int i = 0; it.hasNext() && i < loopOneMax; i++) {
            it.nextItem();
            count++;
        }

        final long expectedLoopOneConsumed = Math.min(loopOneMax, expectedSubsequenceLength);
        assertEquals(expectedLoopOneConsumed, count);

        long remaining = expectedSubsequenceLength - expectedLoopOneConsumed;

        assertEquals(remaining, it.skippable());

        final long skipped = it.skip(3);
        assertTrue(skipped <= 3);
        remaining -= skipped;

        assertEquals(remaining, it.skippable());

        count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(remaining, count);
    }
}
