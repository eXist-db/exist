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

package org.exist.xquery.functions.integer;

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represent format modifier part of the formatting picture for integer formatting
 * {@see https://www.w3.org/TR/xpath-functions-31/#formatting-integers}
 */
class FormatModifier {

    enum Numbering {CARDINAL, ORDINAL}

    enum LetterSequence {ALPHABETIC, TRADITIONAL}

    final String modifier;
    final boolean isEmpty;
    Numbering numbering = Numbering.CARDINAL;
    String variation;
    LetterSequence letterSequence = LetterSequence.ALPHABETIC;

    static final Pattern modifierPattern = Pattern.compile("^(?:([co])(\\((.+)\\))?)?([at])?$");

    FormatModifier(final String modifier) throws XPathException {
        this.modifier = modifier;
        this.isEmpty = modifier.isEmpty();
        if (!isEmpty) {
            parseModifier();
        }
    }

    private void parseModifier() throws XPathException {
        final Matcher m = modifierPattern.matcher(modifier);
        if (!m.matches()) {
            throw new XPathException((Expression) null, ErrorCodes.FODF1310, "Modifier " + modifier + " is not a valid pattern modifier");
        }
        final String n = m.group(1);
        if (n != null) {
            if ("c".equals(n)) numbering = Numbering.CARDINAL;
            if ("o".equals(n)) numbering = Numbering.ORDINAL;
        }
        final String v = m.group(3);
        if (v != null) {
            variation = v;
        }
        final String l = m.group(4);
        if (l != null) {
            if ("a".equals(l)) letterSequence = LetterSequence.ALPHABETIC;
            if ("t".equals(l)) letterSequence = LetterSequence.TRADITIONAL;
        }
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("numbering=").append(numbering).append("::");
        sb.append("variation=").append(variation).append("::");
        sb.append("lettersequence=").append(letterSequence).append("::");
        return sb.substring(0, sb.length() - 2);
    }
}
