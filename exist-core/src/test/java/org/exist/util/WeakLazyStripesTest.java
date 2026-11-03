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

package org.exist.util;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class WeakLazyStripesTest {

    @Test
    void stripeReuse() {
        final WeakLazyStripes<Integer, UUID> stripes = new WeakLazyStripes<>(key -> UUID.randomUUID());

        // get some stripes
        final List<UUID> first = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            first.add(stripes.get(i));
        }

        // attempt to get the same stripes again
        for (int i = 0; i < 2000; i++) {
            assertEquals(first.get(i), stripes.get(i));
        }
    }

    /**
     * Reads don't count themselves, so check that reads alone still remove the entries of stripes that the GC has
     * cleared: a read polls the reference queue, and drains it when it finds a cleared reference.
     */
    @Test
    void readsRemoveClearedStripes() throws InterruptedException {
        final WeakLazyStripes<Integer, UUID> stripes = new WeakLazyStripes<>(key -> UUID.randomUUID());

        final UUID kept = stripes.get(-1);
        for (int i = 0; i < 2000; i++) {
            stripes.get(i);  // not kept, so the GC may clear these stripes
        }
        assertEquals(2001, stripes.size());

        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (stripes.size() > 1 && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(10);
            for (int i = 0; i < 1000; i++) {
                assertEquals(kept, stripes.get(-1));  // reads only: no stripe is created
            }
        }

        assertEquals(1, stripes.size(), "the entries of cleared stripes are removed by reads");
        assertEquals(kept, stripes.get(-1));
    }

    @Test
    void writesRemoveClearedStripes() throws InterruptedException {
        final WeakLazyStripes<Integer, UUID> stripes = new WeakLazyStripes<>(key -> UUID.randomUUID());

        stripes.get(-1);  // not kept, so the GC may clear it
        final List<UUID> kept = new ArrayList<>();
        int key = 0;
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (stripes.size() > kept.size() && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(10);
            kept.add(stripes.get(key++));  // each a write, which drains the reference queue
        }

        assertEquals(kept.size(), stripes.size(), "the entry of the cleared stripe is removed by a write");

        // the cleared stripe is created again on its next use
        final UUID again = stripes.get(-1);
        assertNotNull(again);
        assertEquals(again, stripes.get(-1));
    }
}
