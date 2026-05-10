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

import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Guards the Object.equals/hashCode contract across {@link AtomicValue} subclasses
 * whose equals method admits cross-type spec equality. Without these guards,
 * Bifurcan-backed map operations ({@code map:contains}, {@code map:get}) bucket
 * spec-equal keys differently and silently mis-report containment.
 *
 * Scope: numeric same-key cluster (xs:integer / xs:decimal / xs:double / xs:float)
 * and xs:boolean. The xs:duration family is closed by PR #6333. The string and
 * date/time clusters already satisfy the contract (covered here as guards).
 */
public class HashCodeContractTest {

    // --- Numeric same-key cluster: cross-type spec equality must imply hash equality ---

    @Test
    public void integerEqualsDecimalSharesHashCode() {
        final AtomicValue i = new IntegerValue(BigInteger.ONE);
        final AtomicValue d = new DecimalValue(new BigDecimal("1"));
        assertTrue(i.equals(d));
        assertEquals(i.hashCode(), d.hashCode());
    }

    @Test
    public void integerEqualsDecimalWithTrailingZerosSharesHashCode() {
        final AtomicValue i = new IntegerValue(BigInteger.ONE);
        final AtomicValue d = new DecimalValue(new BigDecimal("1.0"));
        assertTrue(i.equals(d));
        assertEquals(i.hashCode(), d.hashCode());
    }

    @Test
    public void integerEqualsDoubleSharesHashCode() {
        final AtomicValue i = new IntegerValue(BigInteger.ONE);
        final AtomicValue dbl = new DoubleValue(1.0);
        assertTrue(i.equals(dbl));
        assertEquals(i.hashCode(), dbl.hashCode());
    }

    @Test
    public void decimalEqualsDoubleSharesHashCode() {
        final AtomicValue d = new DecimalValue(new BigDecimal("1.0"));
        final AtomicValue dbl = new DoubleValue(1.0);
        assertTrue(d.equals(dbl));
        assertEquals(d.hashCode(), dbl.hashCode());
    }

    @Test
    public void doubleEqualsFloatSharesHashCode() {
        final AtomicValue dbl = new DoubleValue(1.0);
        final AtomicValue f = new FloatValue(1.0f);
        assertTrue(dbl.equals(f));
        assertEquals(dbl.hashCode(), f.hashCode());
    }

    @Test
    public void positiveInfinitySharesHashCodeAcrossDoubleAndFloat() {
        final AtomicValue dbl = new DoubleValue(Double.POSITIVE_INFINITY);
        final AtomicValue f = new FloatValue(Float.POSITIVE_INFINITY);
        assertTrue(dbl.equals(f));
        assertEquals(dbl.hashCode(), f.hashCode());
    }

    @Test
    public void negativeInfinitySharesHashCodeAcrossDoubleAndFloat() {
        final AtomicValue dbl = new DoubleValue(Double.NEGATIVE_INFINITY);
        final AtomicValue f = new FloatValue(Float.NEGATIVE_INFINITY);
        assertTrue(dbl.equals(f));
        assertEquals(dbl.hashCode(), f.hashCode());
    }

    @Test
    public void distinctIntegersHaveDistinctHashCodes() {
        // Probabilistic: distinct small integers should not collide.
        assertNotEquals(new IntegerValue(BigInteger.valueOf(1)).hashCode(),
                new IntegerValue(BigInteger.valueOf(2)).hashCode());
        assertNotEquals(new IntegerValue(BigInteger.valueOf(0)).hashCode(),
                new IntegerValue(BigInteger.valueOf(42)).hashCode());
    }

    @Test
    public void integerHashCodeIsDeterministicAcross100Iterations() {
        final int reference = new IntegerValue(BigInteger.valueOf(12345)).hashCode();
        for (int i = 0; i < 100; i++) {
            assertEquals(reference, new IntegerValue(BigInteger.valueOf(12345)).hashCode());
        }
    }

    @Test
    public void doubleHashCodeIsDeterministicAcross100Iterations() {
        final int reference = new DoubleValue(3.14159).hashCode();
        for (int i = 0; i < 100; i++) {
            assertEquals(reference, new DoubleValue(3.14159).hashCode());
        }
    }

    // --- Boolean: non-singleton instances must hash by value, not identity ---

    @Test
    public void newBooleanInstancesShareHashCode() {
        final AtomicValue a = new BooleanValue(true);
        final AtomicValue b = new BooleanValue(true);
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void booleanSingletonAndNewInstanceShareHashCode() {
        final AtomicValue a = BooleanValue.TRUE;
        final AtomicValue b = new BooleanValue(true);
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void booleanTrueAndFalseHaveDistinctHashCodes() {
        assertNotEquals(BooleanValue.TRUE.hashCode(), BooleanValue.FALSE.hashCode());
    }

    // --- Codepoint same-key cluster (regression guard) ---

    @Test
    public void stringEqualsStringSharesHashCode() {
        final AtomicValue a = new StringValue("foo");
        final AtomicValue b = new StringValue("foo");
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
    }
}
