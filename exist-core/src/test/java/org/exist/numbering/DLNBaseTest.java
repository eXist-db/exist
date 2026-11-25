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
package org.exist.numbering;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
class DLNBaseTest {

    @Test
    void hashCodeMatchesForEqualIds() {
        final DLN id = new DLN("1.2.3");
        final DLN equalId = new DLN("1.2.3");

        assertEquals(id, equalId);
        assertEquals(id.hashCode(), equalId.hashCode());
    }

    @Test
    void hashSetFindsParentId() {
        final NodeId parentId = new DLN("1.2.3");
        final NodeId returnedParentId = new DLN("1.2.3.4").getParentId();
        final Set<NodeId> ids = new HashSet<>();
        ids.add(parentId);

        assertTrue(ids.contains(returnedParentId));
    }

    @Test
    void DLNByteArrayConstructor_roundTrip() {
        final DLNBase dlnBase = new DLNBase();

        for(int i = 0; i < 100; i++) {
            dlnBase.incrementLevelId();

            if(i % 10 == 0) {
                for(final int levelId : dlnBase.getLevelIds()) {
                    dlnBase.addLevelId(levelId, true);
                }
            }

            final byte[] data = new byte[dlnBase.size()];
            dlnBase.serialize(data, 0);

            final DLN reconstructedDln = new DLN(dlnBase.units(), data, 0);

            assertEquals(dlnBase, reconstructedDln);
        }
    }
}
