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

package org.exist.dom.persistent;

import org.exist.xquery.Constants;
import org.exist.xquery.value.SequenceIterator;
import org.junit.Test;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

public class NewArrayNodeSetTest {

    @Test
    public void iterate_loop() {
        final NewArrayNodeSet newArrayNodeSet = mockNewArrayNodeSet(99);

        final SequenceIterator it = newArrayNodeSet.iterate();
        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(99, count);
    }

    @Test
    public void iterate_skip_loop() {
        final NewArrayNodeSet newArrayNodeSet = mockNewArrayNodeSet(99);
        final SequenceIterator it = newArrayNodeSet.iterate();

        assertEquals(99, it.skippable());

        assertEquals(10, it.skip(10));

        assertEquals(89, it.skippable());

        int count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(89, count);
    }

    @Test
    public void iterate_loop_skip_loop() {
        final NewArrayNodeSet newArrayNodeSet = mockNewArrayNodeSet(99);
        final SequenceIterator it = newArrayNodeSet.iterate();

        int len = 20;
        int count = 0;
        for (int i = 0; it.hasNext() && i < len; i++) {
            it.nextItem();
            count++;
        }
        assertEquals(20, count);

        assertEquals(79, it.skippable());

        assertEquals(10, it.skip(10));

        assertEquals(69, it.skippable());

        count = 0;
        while (it.hasNext()) {
            it.nextItem();
            count++;
        }

        assertEquals(69, count);
    }

    private static NewArrayNodeSet mockNewArrayNodeSet(final int size) {
        final NodeProxy mockNodes[] = new NodeProxy[size];
        for (int i = 0; i < mockNodes.length; i++) {
            final NodeProxy mockNodeProxy = createMock(NodeProxy.class);
            replay(mockNodeProxy);
            mockNodes[i] = mockNodeProxy;
        }
        return new NewArrayNodeSetStub(mockNodes);
    }

    private static class NewArrayNodeSetStub extends NewArrayNodeSet {
        public NewArrayNodeSetStub(final NodeProxy... nodes) {
            for(final NodeProxy node : nodes) {
                addInternal(node, Constants.NO_SIZE_HINT);
            }
        }

        @Override
        public SequenceIterator iterate() {
            return new NewArrayIterator();
        }
    }
}
