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
package org.exist.xquery.functions.map;

import org.exist.xquery.Expression;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.ArrayListValueSequence;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A map that represents an XQuery 4.0 record instance.
 *
 * <p>Extends {@link MapType} with two capabilities needed for record types:</p>
 * <ul>
 *   <li>The {@link #keys()} method returns keys in the declared field order
 *       (required by record coercion per XQ4 spec).</li>
 *   <li>The {@link #getType()} / {@link #getItemType()} methods can report
 *       a named record type (e.g., {@code Type.DATETIME_RECORD}) so that
 *       {@code instance of fn:dateTime-record} works correctly.</li>
 * </ul>
 */
public class RecordMapType extends MapType {

    private final List<String> fieldOrder;
    private final int recordTypeCode;

    /**
     * Create a record map with declaration-order keys and a custom type code.
     *
     * @param expression the source expression (may be null)
     * @param context    the XQuery context
     * @param fieldOrder the declared field names in declaration order
     * @param recordTypeCode the type code to report (e.g., {@code Type.RECORD} or {@code Type.DATETIME_RECORD})
     */
    public RecordMapType(@Nullable final Expression expression, final XQueryContext context,
                         final List<String> fieldOrder, final int recordTypeCode) {
        super(expression, context);
        this.fieldOrder = fieldOrder;
        this.recordTypeCode = recordTypeCode;
    }

    /**
     * Create a record map with declaration-order keys and the generic RECORD type.
     */
    public RecordMapType(@Nullable final Expression expression, final XQueryContext context,
                         final List<String> fieldOrder) {
        this(expression, context, fieldOrder, Type.RECORD);
    }

    @Override
    public int getType() {
        return recordTypeCode;
    }

    @Override
    public int getItemType() {
        return recordTypeCode;
    }

    /**
     * Returns keys in the declared field order, including only fields
     * that are actually present in the map.
     */
    @Override
    public Sequence keys() {
        final ArrayListValueSequence seq = new ArrayListValueSequence(size());
        for (final String fieldName : fieldOrder) {
            final StringValue key = new StringValue(fieldName);
            if (contains(key)) {
                seq.add(key);
            }
        }
        return seq;
    }
}
