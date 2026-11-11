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
package org.exist.indexing.lucene;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the log of a reindex says about the vectors it embeds again, see https://github.com/eXist-db/exist/issues/6989.
 */
class RecomputedVectorsTest {

    private static final long MINUTE = TimeUnit.MINUTES.toNanos(1);

    private final AtomicLong now = new AtomicLong(1_000);
    private final RecomputedVectors recomputed = new RecomputedVectors(now::get, MINUTE);

    @Test
    void countsByField() {
        assertTrue(recomputed.add("title"), "first node of title");
        assertTrue(recomputed.add("abstract"), "first node of abstract");
        assertFalse(recomputed.add("title"), "second node of title");

        assertEquals(3, recomputed.total());
        assertEquals("abstract 1, title 2", recomputed.byField());
    }

    @Test
    void reportsProgressOncePerInterval() {
        assertFalse(recomputed.isProgressDue(), "before the interval");
        now.addAndGet(MINUTE);
        assertTrue(recomputed.isProgressDue(), "after the interval");
        assertFalse(recomputed.isProgressDue(), "again at once");
        now.addAndGet(MINUTE - 1);
        assertFalse(recomputed.isProgressDue(), "before the next interval");
        now.addAndGet(1);
        assertTrue(recomputed.isProgressDue(), "after the next interval");
    }

    @Test
    void elapsedInHoursMinutesSeconds() {
        now.addAndGet(TimeUnit.HOURS.toNanos(2) + TimeUnit.MINUTES.toNanos(14) + TimeUnit.SECONDS.toNanos(5) + 999_999_999L);
        assertEquals("2:14:05", recomputed.elapsed());
    }
}
