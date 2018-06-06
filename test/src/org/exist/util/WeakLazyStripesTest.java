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

package org.exist.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class WeakLazyStripesTest {

    @Test
    public void stripeReuse() {
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
}
