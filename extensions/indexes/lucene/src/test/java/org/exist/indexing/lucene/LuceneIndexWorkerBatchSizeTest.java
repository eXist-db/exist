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

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LuceneIndexWorkerBatchSizeTest {

    @Test
    public void clampBatchClausesNegativeBecomesOne() {
        assertEquals(1, LuceneIndexWorker.clampReindexDeleteBatchClauses(-1, 1024));
    }

    @Test
    public void clampBatchClausesZeroBecomesOne() {
        assertEquals(1, LuceneIndexWorker.clampReindexDeleteBatchClauses(0, 1024));
    }

    @Test
    public void clampBatchClausesHugeIsCappedAtMax() {
        assertEquals(1024, LuceneIndexWorker.clampReindexDeleteBatchClauses(Integer.MAX_VALUE, 1024));
    }

    @Test
    public void clampBatchClausesValidValueIsPreserved() {
        assertEquals(256, LuceneIndexWorker.clampReindexDeleteBatchClauses(256, 1024));
    }

    /**
     * Pins the reindex delete query composition so future refactors cannot silently
     * drop either the keyword docId batch (faster TermInSetQuery path) or the
     * node-scope guard that protects ft:index named-field records during xmldb:reindex.
     */
    @Test
    public void reindexDeleteQueryUsesKeywordDocIdAndNodeScopedCanary() {
        final Query query = LuceneIndexWorker.reindexNodeDeleteQueryForDocIds(List.of(new BytesRef("7")));
        assertTrue("Expected BooleanQuery composition", query instanceof BooleanQuery);
        final BooleanQuery bq = (BooleanQuery) query;
        assertEquals(2, bq.clauses().size());
        assertEquals(BooleanClause.Occur.MUST, bq.clauses().get(0).occur());
        assertEquals(BooleanClause.Occur.MUST, bq.clauses().get(1).occur());
        assertTrue("Expected docIdKeyword delete path",
                bq.clauses().get(0).query().toString().contains(LuceneIndexWorker.FIELD_DOC_ID_KEYWORD));
        assertTrue("Expected node-scoped delete guard",
                bq.clauses().get(1).query().toString().contains(LuceneUtil.FIELD_NODE_ID_DV));
    }
}
