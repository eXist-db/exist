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
package org.exist.xquery.value;

import org.exist.xquery.Cardinality;
import org.exist.xquery.functions.map.MapType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XQuery 4.0 record type system.
 */
public class RecordTypeTest {

    @Test
    void testTypeHierarchy() {
        assertTrue(Type.subTypeOf(Type.RECORD, Type.MAP_ITEM));
        assertTrue(Type.subTypeOf(Type.RECORD, Type.FUNCTION));
        assertTrue(Type.subTypeOf(Type.RECORD, Type.ITEM));
        assertFalse(Type.subTypeOf(Type.MAP_ITEM, Type.RECORD));
    }

    @Test
    void testTypeName() {
        assertEquals("record(*)", Type.getTypeName(Type.RECORD));
    }

    @Test
    void testFieldDeclaration() {
        final RecordType.FieldDeclaration field = new RecordType.FieldDeclaration(
                "name", new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE), false);
        assertEquals("name", field.getName());
        assertFalse(field.isOptional());
        assertNotNull(field.getType());
    }

    @Test
    void testOptionalField() {
        final RecordType.FieldDeclaration field = new RecordType.FieldDeclaration(
                "age", new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE), true);
        assertTrue(field.isOptional());
    }

    @Test
    void testRecordTypeToString() {
        final List<RecordType.FieldDeclaration> fields = Arrays.asList(
                new RecordType.FieldDeclaration("name",
                        new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE), false),
                new RecordType.FieldDeclaration("age",
                        new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE), true)
        );
        final RecordType rt = new RecordType(fields, false);
        final String str = rt.toString();
        assertTrue(str.startsWith("record("));
        assertTrue(str.contains("name"));
        assertTrue(str.contains("age?"));
        assertTrue(str.endsWith(")"));
    }

    @Test
    void testExtensibleRecordType() {
        final RecordType rt = new RecordType(
                List.of(new RecordType.FieldDeclaration("x", null, false)),
                true);
        assertTrue(rt.isExtensible());
        assertTrue(rt.toString().contains("*"));
    }

    @Test
    void testSequenceTypeRecordAPI() {
        final SequenceType st = new SequenceType();
        assertFalse(st.isRecordType());

        final List<RecordType.FieldDeclaration> fields = List.of(
                new RecordType.FieldDeclaration("name",
                        new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE), false)
        );
        st.setRecordType(new RecordType(fields, false));
        assertTrue(st.isRecordType());
        assertEquals(Type.RECORD, st.getPrimaryType());
        assertNotNull(st.getFieldDeclarations());
        assertEquals(1, st.getFieldDeclarations().size());
        assertFalse(st.isRecordExtensible());
    }
}
