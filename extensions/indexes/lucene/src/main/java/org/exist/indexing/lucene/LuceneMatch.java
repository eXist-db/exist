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

import org.apache.lucene.facet.Facets;
import org.apache.lucene.search.Query;
import org.exist.dom.persistent.Match;
import org.exist.numbering.NodeId;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.ValueSequence;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Match class containing the score of a match and a reference to
 * the query that generated it.
 */
public class LuceneMatch extends Match {

    private int luceneDocId = -1;
    private float score = 0.0f;
    private final Query query;

    private LuceneIndexWorker.LuceneFacets facets;

    private Map<String, FieldValue[]> fields = null;

    LuceneMatch(final int contextId, final int luceneDocId, final NodeId nodeId, final Query query, final LuceneIndexWorker.LuceneFacets facets) {
        super(contextId, nodeId, null);
        this.luceneDocId = luceneDocId;
        this.query = query;
        this.facets = facets;
    }

    private LuceneMatch(LuceneMatch copy) {
        super(copy);
        this.score = copy.score;
        this.luceneDocId = copy.luceneDocId;
        this.query = copy.query;
        this.facets = copy.facets;
        this.fields = copy.fields;
    }

    @Override
    public Match createInstance(int contextId, NodeId nodeId, String matchTerm) {
        return null;
    }

    public int getLuceneDocId() {
        return luceneDocId;
    }

    @Override
    public Match newCopy() {
        return new LuceneMatch(this);
    }

    @Override
    public String getIndexId() {
        return LuceneIndex.ID;
    }

    public Query getQuery() {
        return query;
    }

    public float getScore() {
        return score;
    }

    protected void setScore(float score) {
        this.score = score;
    }

    public Facets getFacets() {
        return this.facets.getFacets();
    }

    public @Nullable
    Sequence getField(String name, int type) throws XPathException {
        if (fields == null) {
            return null;
        }
        final FieldValue[] values = fields.get(name);
        if (values.length == 1) {
            return values[0].getValue(type);
        }
        final ValueSequence result = new ValueSequence(values.length);
        for (FieldValue value: values) {
            result.add(value.getValue(type));
        }
        return result;
    }

    // DW: missing hashCode() ?
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LuceneMatch)) {
            return false;
        }
        LuceneMatch o = (LuceneMatch) other;
        return (nodeId == o.nodeId || nodeId.equals(o.nodeId))
                && query == ((LuceneMatch) other).query;
    }

    @Override
    public boolean matchEquals(Match other) {
        return equals(other);
    }

    private interface FieldValue {

        AtomicValue getValue(int type) throws XPathException;
    }

}
