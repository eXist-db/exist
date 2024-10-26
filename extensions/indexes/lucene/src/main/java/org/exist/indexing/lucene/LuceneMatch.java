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
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.Query;
import org.exist.dom.persistent.Match;
import org.exist.numbering.NodeId;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.modules.lucene.LuceneModule;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.*;

/**
 * Match class containing the score of a match and a reference to
 * the query that generated it.
 */
public class LuceneMatch extends Match {

    private float score = 0.0f;
    private final Query query;

    private LuceneIndexWorker.LuceneFacets facets;

    private Map<String, FieldValue[]> fields = null;

    private LuceneMatch(int contextId, NodeId nodeId, Query query) {
        this(contextId, nodeId, query, null);
    }

    LuceneMatch(int contextId, NodeId nodeId, Query query, LuceneIndexWorker.LuceneFacets facets) {
        super(contextId, nodeId, null);
        this.query = query;
        this.facets = facets;
    }

    private LuceneMatch(LuceneMatch copy) {
        super(copy);
        this.score = copy.score;
        this.query = copy.query;
        this.facets = copy.facets;
        this.fields = copy.fields;
    }

    @Override
    public Match createInstance(int contextId, NodeId nodeId, String matchTerm) {
        return null;
    }

    public Match createInstance(int contextId, NodeId nodeId, Query query) {
        return new LuceneMatch(contextId, nodeId, query);
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

    protected void addField(String name, IndexableField[] values) {
        if (fields == null) {
            fields = new HashMap<>();
        }
        final FieldValue[] v = new FieldValue[values.length];
        int i = 0;
        for (IndexableField value : values) {
            if (value.numericValue() != null) {
                v[i++] = new NumericField(value.numericValue());
            } else {
                v[i++] = new StringField(value.stringValue());
            }
        }
        fields.put(name, v);
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

    private static class StringField implements FieldValue {

        private final String value;

        StringField(String value) {
            this.value = value;
        }

        @Override
        public AtomicValue getValue(int type) throws XPathException {
            switch(type) {
                case Type.TIME:
                    return new TimeValue(value);
                case Type.DATE_TIME:
                    return new DateTimeValue(value);
                case Type.DATE:
                    return new DateValue(value);
                case Type.FLOAT:
                    return new FloatValue(value);
                case Type.DOUBLE:
                    return new DoubleValue(value);
                case Type.DECIMAL:
                    return new DecimalValue(value);
                case Type.INTEGER:
                case Type.INT:
                case Type.UNSIGNED_INT:
                case Type.LONG:
                case Type.UNSIGNED_LONG:
                    return new IntegerValue(value);
                default:
                    return new StringValue(value);
            }
        }
    }

    private static class NumericField implements FieldValue {

        private final Number value;

        NumericField(Number value) {
            this.value = value;
        }

        @Override
        public AtomicValue getValue(int type) throws XPathException {
            switch(type) {
                case Type.TIME:
                    final Date time = new Date(value.longValue());
                    final GregorianCalendar gregorianCalendar = new GregorianCalendar();
                    gregorianCalendar.setTime(time);
                    final XMLGregorianCalendar calendar = TimeUtils.getInstance().newXMLGregorianCalendar(gregorianCalendar);
                    return new TimeValue(calendar);
                case Type.DATE_TIME:
                    throw new XPathException((Expression) null, LuceneModule.EXXQDYFT0004, "Cannot convert numeric field to xs:dateTime");
                case Type.DATE:
                    final long dl = value.longValue();
                    final int year = (int)(dl >> 16) & 0xFFFF;
                    final int month = (int)(dl >> 8) & 0xFF;
                    final int day = (int)(dl & 0xFF);
                    final DateValue date = new DateValue();
                    date.calendar.setYear(year);
                    date.calendar.setMonth(month);
                    date.calendar.setDay(day);
                    return date;
                case Type.FLOAT:
                    return new FloatValue(value.floatValue());
                case Type.DOUBLE:
                    return new DoubleValue(value.floatValue());
                case Type.DECIMAL:
                    return new DecimalValue(value.doubleValue());
                case Type.INTEGER:
                case Type.INT:
                case Type.UNSIGNED_INT:
                case Type.LONG:
                case Type.UNSIGNED_LONG:
                    return new IntegerValue(value.longValue());
                default:
                    return new StringValue(value.toString());
            }
        }
    }
}
