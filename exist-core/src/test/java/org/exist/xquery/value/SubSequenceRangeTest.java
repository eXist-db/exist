/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.exist.xquery.Cardinality;
import org.exist.xquery.RangeSequence;
import org.exist.xquery.XPathException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@Execution(ExecutionMode.CONCURRENT)
public class SubSequenceRangeTest {

    private static final long RANGE_START = 1;
    private static final long RANGE_END = 99;

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
    public String subSequenceStartEndName;
    public long fromInclusive;
    public int toExclusive;
    public int expectedSubsequenceLength;

    private static final RangeSequence range = new RangeSequence(new IntegerValue(RANGE_START), new IntegerValue(RANGE_END));

    private SubSequence getSubsequence() {
        return new SubSequence(fromInclusive, toExclusive, range);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void getItemCount(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) {
        initSubSequenceRangeTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
        assertEquals(expectedSubsequenceLength, getSubsequence().getItemCount());
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void isEmpty(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) {
        initSubSequenceRangeTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
        if (expectedSubsequenceLength == 0) {
            assertTrue(getSubsequence().isEmpty());
        } else {
            assertFalse(getSubsequence().isEmpty());
        }
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void hasOne(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) {
        initSubSequenceRangeTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
        if (expectedSubsequenceLength == 1) {
            assertTrue(getSubsequence().hasOne());
        } else {
            assertFalse(getSubsequence().hasOne());
        }
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void hasMany(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) {
        initSubSequenceRangeTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
        if (expectedSubsequenceLength > 1) {
            assertTrue(getSubsequence().hasMany());
        } else {
            assertFalse(getSubsequence().hasMany());
        }
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void getCardinality(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) {
        initSubSequenceRangeTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
        final Cardinality expectedCardinality;
        if (expectedSubsequenceLength == 0) {
            expectedCardinality = Cardinality.EMPTY_SEQUENCE;
        } else if (expectedSubsequenceLength == 1) {
            expectedCardinality = Cardinality.EXACTLY_ONE;
        } else {
            expectedCardinality = Cardinality._MANY;
        }
        assertEquals(expectedCardinality, getSubsequence().getCardinality());
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void iterate_loop(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) throws XPathException {
        initSubSequenceRangeTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
        final SequenceIterator it = getSubsequence().iterate();
        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(expectedSubsequenceLength, count);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void iterate_skip_loop(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) throws XPathException {
        initSubSequenceRangeTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void iterate_loop_skip_loop(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) throws XPathException {
        initSubSequenceRangeTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
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

    public void initSubSequenceRangeTest(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) {
        this.subSequenceStartEndName = subSequenceStartEndName;
        this.fromInclusive = fromInclusive;
        this.toExclusive = toExclusive;
        this.expectedSubsequenceLength = expectedSubsequenceLength;
    }
}
