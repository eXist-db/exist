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
import org.exist.xquery.RangeSequence;
import org.exist.xquery.XPathException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@Execution(ExecutionMode.CONCURRENT)
public class SubSequenceTest {

    private static final long RANGE_START = 1;
    private static final long RANGE_END = 99;

    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"0 until 10",      0,    10,   0},
                {"1 until 10",      1,    10,  99},
                {"1 until 11",      1,    11,  99},
                {"10 until 20",    10,    20,  99},
                {"11 until 20",    11,    20,  98},
                {"11 until 21",    11,    21,  80},
                {"89 until 99",    89,    99,  98},
                {"90 until 99",    90,    99,  99},
                {"90 until 100",   90,   100,  99},
                {"99 until 109",   99,   109,  99},
                {"100 until 109", 100,   109,   5},
                {"100 until 110", 100,   110,   0},
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
    void itemAt0(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) throws XPathException {
        initSubSequenceTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
        assertItemAt(0);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void itemAt1(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) throws XPathException {
        initSubSequenceTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
        assertItemAt(1);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void itemAt2(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) throws XPathException {
        initSubSequenceTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
        assertItemAt(2);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void itemAt8(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) throws XPathException {
        initSubSequenceTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
        assertItemAt(8);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void itemAt9(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) throws XPathException {
        initSubSequenceTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
        assertItemAt(9);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    void itemAt10(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) throws XPathException {
        initSubSequenceTest(subSequenceStartEndName, fromInclusive, toExclusive, expectedSubsequenceLength);
        assertItemAt(10);
    }

    private void assertItemAt(final int pos) throws XPathException {
        final long cleanFromInclusive = fromInclusive < 1 ? 1 : fromInclusive;
        long length = toExclusive - cleanFromInclusive;
        if (toExclusive > RANGE_END + 1) {
            length = RANGE_END - cleanFromInclusive + 1;
        }
        if (pos < length) {
            final long expected = RANGE_START + (cleanFromInclusive - 1) + pos;
            final long actual = getSubsequence().itemAt(pos).toJavaObject(Long.class).longValue();
            assertEquals(expected, actual);
        } else {
            assertNull(getSubsequence().itemAt(pos));
        }
    }

    public void initSubSequenceTest(String subSequenceStartEndName, long fromInclusive, int toExclusive, int expectedSubsequenceLength) {
        this.subSequenceStartEndName = subSequenceStartEndName;
        this.fromInclusive = fromInclusive;
        this.toExclusive = toExclusive;
        this.expectedSubsequenceLength = expectedSubsequenceLength;
    }
}
