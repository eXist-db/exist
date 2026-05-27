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
package org.exist.util;

/**
 * Conversion utilities that delegate to Saxon's value classes for spec-compliant
 * lexical forms of XDM numeric types.
 *
 * Centralising these calls keeps Saxon class references out of eXist's own
 * {@code DoubleValue} / {@code FloatValue}, where the class names collide with
 * Saxon's identically-named classes. Call sites here use a single point of
 * contact to the Saxon API, which makes future Saxon upgrades easier to audit.
 */
public final class SaxonConversions {

    private SaxonConversions() {
        // utility class — not instantiable
    }

    /**
     * Convert a {@code double} to its XDM lexical form per F&amp;O 3.1 §4.10.2,
     * using Saxon's spec-compliant implementation.
     *
     * @param value the double to convert
     * @return the XDM lexical form (e.g. {@code "NaN"}, {@code "-INF"}, {@code "1.5E2"})
     */
    public static String doubleToString(final double value) {
        return net.sf.saxon.value.DoubleValue.doubleToString(value).toString();
    }

    /**
     * Convert a {@code float} to its XDM lexical form per F&amp;O 3.1 §4.10.2,
     * using Saxon's spec-compliant implementation.
     *
     * @param value the float to convert
     * @return the XDM lexical form
     */
    public static String floatToString(final float value) {
        return net.sf.saxon.value.FloatValue.floatToString(value).toString();
    }
}
