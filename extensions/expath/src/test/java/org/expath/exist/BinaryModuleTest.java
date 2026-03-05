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
package org.expath.exist;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for the EXPath Binary Module 4.0 implementation.
 */
public class BinaryModuleTest {

    @ClassRule
    public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String IMPORT = "import module namespace bin = 'http://expath.org/ns/binary';\n";

    /**
     * Helper to compare binary results as hex strings.
     * Wraps the XQuery expression in string(xs:hexBinary(...)) to get a hex string representation.
     */
    private void assertBinaryAsHex(final String expectedHex, final String binaryExpr) throws Exception {
        assertQuery(expectedHex,
                IMPORT + "string(xs:hexBinary(" + binaryExpr + "))");
    }

    // ========== Conversion Functions ==========

    @Test
    public void hex() throws Exception {
        assertBinaryAsHex("DEADBEEF", "bin:hex('DEADBEEF')");
    }

    @Test
    public void hexWithWhitespace() throws Exception {
        assertBinaryAsHex("DEADBEEF", "bin:hex('DE AD BE EF')");
    }

    @Test
    public void hexWithUnderscores() throws Exception {
        assertBinaryAsHex("DEADBEEF", "bin:hex('DEAD_BEEF')");
    }

    @Test
    public void hexOddLength() throws Exception {
        assertBinaryAsHex("0F", "bin:hex('F')");
    }

    @Test
    public void hexEmpty() throws Exception {
        assertQueryEmpty(IMPORT + "bin:hex(())");
    }

    @Test
    public void hexEmptyString() throws Exception {
        assertQuery("0",
                IMPORT + "bin:length(bin:hex(''))");
    }

    @Test
    public void hexInvalidChar() throws Exception {
        assertQueryError("non-numeric-character",
                IMPORT + "bin:hex('GG')");
    }

    @Test
    public void bin() throws Exception {
        assertBinaryAsHex("FF", "bin:bin('11111111')");
    }

    @Test
    public void binPadding() throws Exception {
        assertBinaryAsHex("01", "bin:bin('1')");
    }

    @Test
    public void binEmpty() throws Exception {
        assertQueryEmpty(IMPORT + "bin:bin(())");
    }

    @Test
    public void binInvalidChar() throws Exception {
        assertQueryError("non-numeric-character",
                IMPORT + "bin:bin('102')");
    }

    @Test
    public void octal() throws Exception {
        assertBinaryAsHex("FF", "bin:octal('377')");
    }

    @Test
    public void octalEmpty() throws Exception {
        assertQueryEmpty(IMPORT + "bin:octal(())");
    }

    @Test
    public void octalInvalidChar() throws Exception {
        assertQueryError("non-numeric-character",
                IMPORT + "bin:octal('89')");
    }

    @Test
    public void toOctets() throws Exception {
        assertQuery("222 173 190 239",
                IMPORT + "string-join(for $o in bin:to-octets(bin:hex('DEADBEEF')) return string($o), ' ')");
    }

    @Test
    public void fromOctets() throws Exception {
        assertBinaryAsHex("FF00", "bin:from-octets((255, 0))");
    }

    @Test
    public void fromOctetsEmpty() throws Exception {
        assertQuery("0",
                IMPORT + "bin:length(bin:from-octets(()))");
    }

    // ========== Basic Functions ==========

    @Test
    public void length() throws Exception {
        assertQuery("4",
                IMPORT + "bin:length(bin:hex('DEADBEEF'))");
    }

    @Test
    public void part() throws Exception {
        assertBinaryAsHex("ADBE", "bin:part(bin:hex('DEADBEEF'), 1, 2)");
    }

    @Test
    public void partNoSize() throws Exception {
        assertBinaryAsHex("BEEF", "bin:part(bin:hex('DEADBEEF'), 2)");
    }

    @Test
    public void partEmpty() throws Exception {
        assertQueryEmpty(IMPORT + "bin:part((), 0, 1)");
    }

    @Test
    public void partOutOfRange() throws Exception {
        assertQueryError("index-out-of-range",
                IMPORT + "bin:part(bin:hex('FF'), 0, 5)");
    }

    @Test
    public void partNegativeSize() throws Exception {
        assertQueryError("negative-size",
                IMPORT + "bin:part(bin:hex('FF'), 0, -1)");
    }

    @Test
    public void join() throws Exception {
        assertBinaryAsHex("DEADBEEF", "bin:join((bin:hex('DEAD'), bin:hex('BEEF')))");
    }

    @Test
    public void joinEmpty() throws Exception {
        assertQuery("0",
                IMPORT + "bin:length(bin:join(()))");
    }

    @Test
    public void insertBefore() throws Exception {
        assertBinaryAsHex("DEFFADBE", "bin:insert-before(bin:hex('DEADBE'), 1, bin:hex('FF'))");
    }

    @Test
    public void insertBeforeEmpty() throws Exception {
        assertQueryEmpty(IMPORT + "bin:insert-before((), 0, bin:hex('FF'))");
    }

    @Test
    public void insertBeforeOutOfRange() throws Exception {
        assertQueryError("index-out-of-range",
                IMPORT + "bin:insert-before(bin:hex('FF'), 5, bin:hex('00'))");
    }

    @Test
    public void padLeft() throws Exception {
        assertBinaryAsHex("0000FF", "bin:pad-left(bin:hex('FF'), 2)");
    }

    @Test
    public void padLeftWithOctet() throws Exception {
        assertBinaryAsHex("ABABFF", "bin:pad-left(bin:hex('FF'), 2, 171)");
    }

    @Test
    public void padLeftNegativeCount() throws Exception {
        assertQueryError("negative-size",
                IMPORT + "bin:pad-left(bin:hex('FF'), -1)");
    }

    @Test
    public void padRight() throws Exception {
        assertBinaryAsHex("FF0000", "bin:pad-right(bin:hex('FF'), 2)");
    }

    @Test
    public void padRightWithOctet() throws Exception {
        assertBinaryAsHex("FFABAB", "bin:pad-right(bin:hex('FF'), 2, 171)");
    }

    @Test
    public void find() throws Exception {
        assertQuery("1",
                IMPORT + "bin:find(bin:hex('DEADBEEF'), 0, bin:hex('ADBE'))");
    }

    @Test
    public void findNotFound() throws Exception {
        assertQueryEmpty(IMPORT + "bin:find(bin:hex('DEADBEEF'), 0, bin:hex('CAFE'))");
    }

    @Test
    public void findFromOffset() throws Exception {
        assertQueryEmpty(IMPORT + "bin:find(bin:hex('DEADBEEF'), 2, bin:hex('DEAD'))");
    }

    @Test
    public void findEmpty() throws Exception {
        assertQueryEmpty(IMPORT + "bin:find((), 0, bin:hex('FF'))");
    }

    // ========== Text Functions ==========

    @Test
    public void decodeStringUtf8() throws Exception {
        assertQuery("hello",
                IMPORT + "bin:decode-string(bin:encode-string('hello'))");
    }

    @Test
    public void decodeStringWithEncoding() throws Exception {
        assertQuery("hello",
                IMPORT + "bin:decode-string(bin:encode-string('hello', 'UTF-8'), 'UTF-8')");
    }

    @Test
    public void decodeStringWithOffset() throws Exception {
        assertQuery("llo",
                IMPORT + "bin:decode-string(bin:encode-string('hello', 'UTF-8'), 'UTF-8', 2)");
    }

    @Test
    public void decodeStringWithOffsetAndSize() throws Exception {
        assertQuery("ll",
                IMPORT + "bin:decode-string(bin:encode-string('hello', 'UTF-8'), 'UTF-8', 2, 2)");
    }

    @Test
    public void decodeStringEmpty() throws Exception {
        assertQueryEmpty(IMPORT + "bin:decode-string(())");
    }

    @Test
    public void encodeStringUtf8() throws Exception {
        assertBinaryAsHex("41", "bin:encode-string('A')");
    }

    @Test
    public void encodeStringEmpty() throws Exception {
        assertQueryEmpty(IMPORT + "bin:encode-string(())");
    }

    @Test
    public void encodeStringUnknownEncoding() throws Exception {
        assertQueryError("unknown-encoding",
                IMPORT + "bin:encode-string('hello', 'NONEXISTENT')");
    }

    @Test
    public void decodeStringOutOfRange() throws Exception {
        assertQueryError("index-out-of-range",
                IMPORT + "bin:decode-string(bin:encode-string('hi'), 'UTF-8', 100)");
    }

    // ========== Packing Functions ==========

    @Test
    public void packUnpackDouble() throws Exception {
        assertQuery("true",
                IMPORT + "bin:unpack-double(bin:pack-double(3.14), 0) = 3.14");
    }

    @Test
    public void packUnpackDoubleLittleEndian() throws Exception {
        assertQuery("true",
                IMPORT + "bin:unpack-double(bin:pack-double(3.14, 'LE'), 0, 'LE') = 3.14");
    }

    @Test
    public void packDoubleSize() throws Exception {
        assertQuery("8",
                IMPORT + "bin:length(bin:pack-double(1.0))");
    }

    @Test
    public void packUnpackFloat() throws Exception {
        assertQuery("true",
                IMPORT + "abs(bin:unpack-float(bin:pack-float(xs:float(3.14)), 0) - 3.14) < 0.001");
    }

    @Test
    public void packFloatSize() throws Exception {
        assertQuery("4",
                IMPORT + "bin:length(bin:pack-float(xs:float(1.0)))");
    }

    @Test
    public void packUnpackInteger() throws Exception {
        assertQuery("42",
                IMPORT + "bin:unpack-integer(bin:pack-integer(42, 4), 0, 4)");
    }

    @Test
    public void packUnpackNegativeInteger() throws Exception {
        assertQuery("-1",
                IMPORT + "bin:unpack-integer(bin:pack-integer(-1, 4), 0, 4)");
    }

    @Test
    public void packUnpackIntegerLittleEndian() throws Exception {
        assertQuery("256",
                IMPORT + "bin:unpack-integer(bin:pack-integer(256, 4, 'LE'), 0, 4, 'LE')");
    }

    @Test
    public void packIntegerNegativeSize() throws Exception {
        assertQueryError("negative-size",
                IMPORT + "bin:pack-integer(42, -1)");
    }

    @Test
    public void unpackUnsignedInteger() throws Exception {
        assertQuery("255",
                IMPORT + "bin:unpack-unsigned-integer(bin:hex('FF'), 0, 1)");
    }

    @Test
    public void unpackUnsignedIntegerVsSigned() throws Exception {
        assertQuery("-1",
                IMPORT + "bin:unpack-integer(bin:hex('FF'), 0, 1)");
    }

    @Test
    public void unpackDoubleOutOfRange() throws Exception {
        assertQueryError("index-out-of-range",
                IMPORT + "bin:unpack-double(bin:hex('FF'), 0)");
    }

    @Test
    public void packUnpackDoubleNaN() throws Exception {
        assertQuery("true",
                IMPORT + "let $nan := xs:double('NaN') return "
                        + "bin:unpack-double(bin:pack-double($nan), 0) != bin:unpack-double(bin:pack-double($nan), 0)");
    }

    @Test
    public void invalidOctetOrder() throws Exception {
        assertQueryError("XPTY0004",
                IMPORT + "bin:pack-double(1.0, 'INVALID')");
    }

    // ========== Bitwise Functions ==========

    @Test
    public void bitwiseOr() throws Exception {
        assertBinaryAsHex("FF", "bin:or(bin:hex('F0'), bin:hex('0F'))");
    }

    @Test
    public void bitwiseXor() throws Exception {
        assertBinaryAsHex("FF", "bin:xor(bin:hex('F0'), bin:hex('0F'))");
    }

    @Test
    public void bitwiseAnd() throws Exception {
        assertBinaryAsHex("F0", "bin:and(bin:hex('FF'), bin:hex('F0'))");
    }

    @Test
    public void bitwiseNot() throws Exception {
        assertBinaryAsHex("0F", "bin:not(bin:hex('F0'))");
    }

    @Test
    public void bitwiseOrEmpty() throws Exception {
        assertQueryEmpty(IMPORT + "bin:or((), bin:hex('FF'))");
    }

    @Test
    public void bitwiseDifferingLength() throws Exception {
        assertQueryError("differing-length-arguments",
                IMPORT + "bin:or(bin:hex('FF'), bin:hex('FFFF'))");
    }

    @Test
    public void shiftLeft() throws Exception {
        assertBinaryAsHex("10", "bin:shift(bin:hex('01'), 4)");
    }

    @Test
    public void shiftRight() throws Exception {
        assertBinaryAsHex("08", "bin:shift(bin:hex('80'), -4)");
    }

    @Test
    public void shiftLeftOverflow() throws Exception {
        assertBinaryAsHex("00", "bin:shift(bin:hex('FF'), 8)");
    }

    @Test
    public void shiftEmpty() throws Exception {
        assertQueryEmpty(IMPORT + "bin:shift((), 1)");
    }

    // ========== Roundtrip Tests ==========

    @Test
    public void hexRoundtrip() throws Exception {
        assertQuery("222 173 190 239",
                IMPORT + "string-join("
                        + "for $o in bin:to-octets(bin:hex('DEADBEEF')) return string($o), ' ')");
    }

    @Test
    public void octetsRoundtrip() throws Exception {
        assertBinaryAsHex("DEADBEEF", "bin:from-octets(bin:to-octets(bin:hex('DEADBEEF')))");
    }

    @Test
    public void textRoundtrip() throws Exception {
        assertQuery("Hello, World!",
                IMPORT + "bin:decode-string(bin:encode-string('Hello, World!'))");
    }

    @Test
    public void textRoundtripUtf16() throws Exception {
        assertQuery("Hello",
                IMPORT + "bin:decode-string(bin:encode-string('Hello', 'UTF-16'), 'UTF-16')");
    }

    // ========== Helper Methods ==========

    private void assertQuery(final String expected, final String query) throws Exception {
        final Sequence result = executeQuery(query);
        assertEquals("Query: " + query, expected, result.getStringValue());
    }

    private void assertQueryEmpty(final String query) throws Exception {
        final Sequence result = executeQuery(query);
        assertTrue("Expected empty sequence for query: " + query, result.isEmpty());
    }

    private void assertQueryError(final String errorCode, final String query) {
        try {
            executeQuery(query);
            fail("Expected error " + errorCode + " for query: " + query);
        } catch (final XPathException e) {
            final String msg = e.getMessage();
            final String code = e.getErrorCode() != null ? e.getErrorCode().getErrorQName().getLocalPart() : "";
            assertTrue("Expected error containing '" + errorCode + "' but got: " + code + " - " + msg,
                    code.contains(errorCode) || msg.contains(errorCode));
        } catch (final Exception e) {
            fail("Expected XPathException with " + errorCode + " but got: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }

    private Sequence executeQuery(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = brokerPool.getXQueryService();
        try (final DBBroker broker = brokerPool.getBroker()) {
            return xquery.execute(broker, query, null);
        }
    }
}
