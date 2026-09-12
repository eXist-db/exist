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

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.util.ExpressionDumper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XQuery 4.0 record type system.
 */
public class RecordTypeTest {

    private static ExistEmbeddedServer existEmbeddedServer;

    @BeforeAll
    static void startDb() throws Exception {
        existEmbeddedServer = new ExistEmbeddedServer(true, true);
        existEmbeddedServer.startDb();
    }

    @AfterAll
    static void stopDb() {
        if (existEmbeddedServer != null) {
            existEmbeddedServer.stopDb();
        }
    }

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

    /** Simple expression wrapper for testing — returns a fixed Sequence. */
    private static class ConstantExpr extends AbstractExpression {
        private final Sequence value;

        ConstantExpr(final XQueryContext context, final Sequence value) {
            super(context);
            this.value = value;
        }

        @Override
        public Sequence eval(final Sequence contextSequence, final Item contextItem) {
            return value;
        }

        @Override
        public int returnsType() {
            return value.isEmpty() ? Type.EMPTY_SEQUENCE : value.getItemType();
        }

        @Override
        public void analyze(final AnalyzeContextInfo contextInfo) {}

        @Override
        public void dump(final ExpressionDumper dumper) {
            dumper.display(value.toString());
        }
    }

    // === Phase 3: FieldAccessor tests ===

    @Test
    void testFieldAccessorEval() throws EXistException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);

            // Build a map: map { "name": "Alice", "age": 30 }
            final MapType map = new MapType(null, context);
            map.add(new StringValue("name"), new StringValue("Alice"));
            map.add(new StringValue("age"), new IntegerValue(30));

            // Create a FieldAccessor for ".name"
            final Expression baseExpr = new ConstantExpr(context, map);
            final FieldAccessor accessor = new FieldAccessor(context, baseExpr, "name");
            accessor.analyze(new AnalyzeContextInfo());

            final Sequence result = accessor.eval(Sequence.EMPTY_SEQUENCE, null);
            assertFalse(result.isEmpty());
            assertEquals("Alice", result.getStringValue());
        }
    }

    @Test
    void testFieldAccessorMissingField() throws EXistException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);

            final MapType map = new MapType(null, context);
            map.add(new StringValue("name"), new StringValue("Alice"));

            final Expression baseExpr = new ConstantExpr(context, map);
            final FieldAccessor accessor = new FieldAccessor(context, baseExpr, "missing");
            accessor.analyze(new AnalyzeContextInfo());

            final Sequence result = accessor.eval(Sequence.EMPTY_SEQUENCE, null);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void testFieldAccessorNonMap() throws EXistException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);

            final Expression baseExpr = new ConstantExpr(context, new StringValue("not a map"));
            final FieldAccessor accessor = new FieldAccessor(context, baseExpr, "name");
            accessor.analyze(new AnalyzeContextInfo());

            assertThrows(XPathException.class, () ->
                    accessor.eval(Sequence.EMPTY_SEQUENCE, null));
        }
    }

    @Test
    void testFieldAccessorEmptySequence() throws EXistException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);

            final Expression baseExpr = new ConstantExpr(context, Sequence.EMPTY_SEQUENCE);
            final FieldAccessor accessor = new FieldAccessor(context, baseExpr, "name");
            accessor.analyze(new AnalyzeContextInfo());

            final Sequence result = accessor.eval(Sequence.EMPTY_SEQUENCE, null);
            assertTrue(result.isEmpty());
        }
    }

    // === Phase 4: RecordTypeCheck tests ===

    @Test
    void testRecordTypeCheckPass() throws EXistException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);

            // Build record type: record(name as xs:string, age as xs:integer)
            final RecordType rt = new RecordType(List.of(
                    new RecordType.FieldDeclaration("name",
                            new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE), false),
                    new RecordType.FieldDeclaration("age",
                            new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE), false)
            ), false);

            // Build a matching map
            final MapType map = new MapType(null, context);
            map.add(new StringValue("name"), new StringValue("Alice"));
            map.add(new StringValue("age"), new IntegerValue(30));

            final Expression baseExpr = new ConstantExpr(context, map);
            final RecordTypeCheck check = new RecordTypeCheck(context, rt, baseExpr);
            check.analyze(new AnalyzeContextInfo());

            final Sequence result = check.eval(Sequence.EMPTY_SEQUENCE, null);
            assertFalse(result.isEmpty());
        }
    }

    @Test
    void testRecordTypeCheckFailMissingField() throws EXistException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);

            final RecordType rt = new RecordType(List.of(
                    new RecordType.FieldDeclaration("name",
                            new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE), false),
                    new RecordType.FieldDeclaration("age",
                            new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE), false)
            ), false);

            // Map missing 'age'
            final MapType map = new MapType(null, context);
            map.add(new StringValue("name"), new StringValue("Alice"));

            final Expression baseExpr = new ConstantExpr(context, map);
            final RecordTypeCheck check = new RecordTypeCheck(context, rt, baseExpr);
            check.analyze(new AnalyzeContextInfo());

            final XPathException ex = assertThrows(XPathException.class, () ->
                    check.eval(Sequence.EMPTY_SEQUENCE, null));
            assertTrue(ex.getMessage().contains("Missing required field"));
        }
    }

    @Test
    void testRecordTypeCheckOptionalFieldOK() throws EXistException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);

            // age? is optional
            final RecordType rt = new RecordType(List.of(
                    new RecordType.FieldDeclaration("name",
                            new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE), false),
                    new RecordType.FieldDeclaration("age",
                            new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE), true)
            ), false);

            // Map without 'age' — should pass since it's optional
            final MapType map = new MapType(null, context);
            map.add(new StringValue("name"), new StringValue("Alice"));

            final Expression baseExpr = new ConstantExpr(context, map);
            final RecordTypeCheck check = new RecordTypeCheck(context, rt, baseExpr);
            check.analyze(new AnalyzeContextInfo());

            final Sequence result = check.eval(Sequence.EMPTY_SEQUENCE, null);
            assertFalse(result.isEmpty());
        }
    }

    @Test
    void testRecordTypeCheckNonMapFails() throws EXistException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);

            final RecordType rt = new RecordType(List.of(
                    new RecordType.FieldDeclaration("x", null, false)
            ), false);

            final Expression baseExpr = new ConstantExpr(context, new StringValue("not a map"));
            final RecordTypeCheck check = new RecordTypeCheck(context, rt, baseExpr);
            check.analyze(new AnalyzeContextInfo());

            assertThrows(XPathException.class, () ->
                    check.eval(Sequence.EMPTY_SEQUENCE, null));
        }
    }

    @Test
    void testRecordTypeCheckExtensibleAllowsExtraKeys() throws EXistException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);

            // record(name as xs:string, *)
            final RecordType rt = new RecordType(List.of(
                    new RecordType.FieldDeclaration("name",
                            new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE), false)
            ), true);

            final MapType map = new MapType(null, context);
            map.add(new StringValue("name"), new StringValue("Alice"));
            map.add(new StringValue("extra"), new IntegerValue(42));

            final Expression baseExpr = new ConstantExpr(context, map);
            final RecordTypeCheck check = new RecordTypeCheck(context, rt, baseExpr);
            check.analyze(new AnalyzeContextInfo());

            final Sequence result = check.eval(Sequence.EMPTY_SEQUENCE, null);
            assertFalse(result.isEmpty());
        }
    }

    @Test
    void testRecordTypeCheckNonExtensibleDropsExtraKeys() throws EXistException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(java.util.Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);

            // record(name as xs:string) — NOT extensible
            // Per XQ4, coercion drops extra keys (not rejects them)
            final RecordType rt = new RecordType(List.of(
                    new RecordType.FieldDeclaration("name",
                            new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE), false)
            ), false);

            final MapType map = new MapType(null, context);
            map.add(new StringValue("name"), new StringValue("Alice"));
            map.add(new StringValue("extra"), new IntegerValue(42));

            final Expression baseExpr = new ConstantExpr(context, map);
            final RecordTypeCheck check = new RecordTypeCheck(context, rt, baseExpr);
            check.analyze(new AnalyzeContextInfo());

            final Sequence result = check.eval(Sequence.EMPTY_SEQUENCE, null);
            assertFalse(result.isEmpty());
            // Extra key "extra" should be dropped — only "name" remains
            final org.exist.xquery.functions.map.AbstractMapType resultMap =
                    (org.exist.xquery.functions.map.AbstractMapType) result.itemAt(0);
            assertEquals(1, resultMap.size());
            assertEquals("Alice", resultMap.get(new StringValue("name")).getStringValue());
        }
    }
}
