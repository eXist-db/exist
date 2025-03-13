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
package org.exist.storage.structural;

import com.googlecode.junittoolbox.ParallelRunner;
import org.easymock.EasyMock;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class NativeStructuralIndexWorkerTest {

    @Test
    public void getDocIdRanges_singleContiguous() {
        final NativeStructuralIndexWorker indexWorker = new NativeStructuralIndexWorker(null);

        final DocumentSet docs = documentIdSet(Arrays.asList(1,2,3,4,5,6));

        final List<NativeStructuralIndexWorker.Range> ranges = indexWorker.getDocIdRanges(docs);

        assertEquals(1, ranges.size());

        assertEquals(1, ranges.getFirst().start);
        assertEquals(6, ranges.getFirst().end);
    }

    @Test
    public void getDocIdRanges_multipleContiguous() {
        final NativeStructuralIndexWorker indexWorker = new NativeStructuralIndexWorker(null);

        final DocumentSet docs = documentIdSet(Arrays.asList(1,2,3,4,5,6, 88,89, 3,4,5,6, 77, 10,11,12));

        final List<NativeStructuralIndexWorker.Range> ranges = indexWorker.getDocIdRanges(docs);

        assertEquals(5, ranges.size());

        assertEquals(1, ranges.get(0).start);
        assertEquals(6, ranges.get(0).end);

        assertEquals(88, ranges.get(1).start);
        assertEquals(89, ranges.get(1).end);

        assertEquals(3, ranges.get(2).start);
        assertEquals(6, ranges.get(2).end);

        assertEquals(77, ranges.get(3).start);
        assertEquals(77, ranges.get(3).end);

        assertEquals(10, ranges.get(4).start);
        assertEquals(12, ranges.get(4).end);
    }

    @Test
    public void getDocIdRanges_singleId() {
        final NativeStructuralIndexWorker indexWorker = new NativeStructuralIndexWorker(null);

        final DocumentSet docs = documentIdSet(Arrays.asList(6574));

        final List<NativeStructuralIndexWorker.Range> ranges = indexWorker.getDocIdRanges(docs);

        assertEquals(1, ranges.size());

        assertEquals(6574, ranges.getFirst().start);
        assertEquals(6574, ranges.getFirst().end);
    }

    @Test
    public void getDocIdRanges_singleId_followed_by_continguousIds() {
        final NativeStructuralIndexWorker indexWorker = new NativeStructuralIndexWorker(null);

        final DocumentSet docs = documentIdSet(Arrays.asList(6574, 11,12,13,14,15));

        final List<NativeStructuralIndexWorker.Range> ranges = indexWorker.getDocIdRanges(docs);

        assertEquals(2, ranges.size());

        assertEquals(6574, ranges.get(0).start);
        assertEquals(6574, ranges.get(0).end);

        assertEquals(11, ranges.get(1).start);
        assertEquals(15, ranges.get(1).end);
    }

    @Test
    public void getDocIdRanges_contiguousIds_followed_by_single() {
        final NativeStructuralIndexWorker indexWorker = new NativeStructuralIndexWorker(null);

        final DocumentSet docs = documentIdSet(Arrays.asList(11,12,13,14,15, 6574));

        final List<NativeStructuralIndexWorker.Range> ranges = indexWorker.getDocIdRanges(docs);

        assertEquals(2, ranges.size());

        assertEquals(11, ranges.get(0).start);
        assertEquals(15, ranges.get(0).end);

        assertEquals(6574, ranges.get(1).start);
        assertEquals(6574, ranges.get(1).end);
    }

    @Test
    public void getDocIdRanges_multiple_singleIds() {
        final NativeStructuralIndexWorker indexWorker = new NativeStructuralIndexWorker(null);

        final DocumentSet docs = documentIdSet(Arrays.asList(6574, 200, 12, 24));

        final List<NativeStructuralIndexWorker.Range> ranges = indexWorker.getDocIdRanges(docs);

        assertEquals(4, ranges.size());

        assertEquals(6574, ranges.get(0).start);
        assertEquals(6574, ranges.get(0).end);

        assertEquals(200, ranges.get(1).start);
        assertEquals(200, ranges.get(1).end);

        assertEquals(12, ranges.get(2).start);
        assertEquals(12, ranges.get(2).end);

        assertEquals(24, ranges.get(3).start);
        assertEquals(24, ranges.get(3).end);
    }

    private DocumentSet documentIdSet(final List<Integer> documentIds) {
        final DocumentSet mockDocumentSet = createMock(DocumentSet.class);

        final List<DocumentImpl> docs = documentIds.stream().map(id -> {
            final DocumentImpl mockDocument = createMock(DocumentImpl.class);
            expect(mockDocument.getDocId()).andReturn(id).anyTimes();
            return mockDocument;
        }).collect(Collectors.toList());

        expect(mockDocumentSet.getDocumentIterator()).andReturn(docs.iterator());

        replay(mockDocumentSet);
        docs.forEach(EasyMock::replay);

        return mockDocumentSet;
    }
}
