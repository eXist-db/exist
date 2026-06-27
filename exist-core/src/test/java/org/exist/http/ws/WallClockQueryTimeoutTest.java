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
package org.exist.http.ws;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WallClockQueryTimeoutTest {

    @Test
    public void scheduleInvokesCallbackAfterDelay() throws Exception {
        final WallClockQueryTimeout timeout = new WallClockQueryTimeout();
        final CountDownLatch latch = new CountDownLatch(1);
        timeout.schedule(50, latch::countDown);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(timeout.wasTriggered());
    }

    @Test
    public void cancelPreventsCallback() throws Exception {
        final WallClockQueryTimeout timeout = new WallClockQueryTimeout();
        final CountDownLatch latch = new CountDownLatch(1);
        timeout.schedule(200, latch::countDown);
        timeout.cancel();
        assertFalse(latch.await(500, TimeUnit.MILLISECONDS));
        assertFalse(timeout.wasTriggered());
    }
}
