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
package org.exist.xquery.functions.fn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Implements fn:char (XQuery 4.0).
 *
 * Returns a string containing a single character identified by its codepoint
 * or by an HTML5 character reference name.
 */
public class FnChar extends BasicFunction {

    private static final ErrorCodes.ErrorCode FOCH0005 = new ErrorCodes.ErrorCode(
            "FOCH0005", "Unknown character name");

    public static final FunctionSignature FN_CHAR = new FunctionSignature(
            new QName("char", Function.BUILTIN_FUNCTION_NS),
            "Returns a string containing a single character identified by codepoint or character name.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("value", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE,
                            "A codepoint (integer) or character name (string)")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the character"));

    private static volatile Map<String, String> htmlEntities;

    public FnChar(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final var item = args[0].itemAt(0);
        final int type = item.getType();

        if (Type.subTypeOf(type, Type.INTEGER)) {
            // Codepoint
            final long codepoint = ((IntegerValue) item).getLong();
            if (codepoint < 1 || codepoint > 0x10FFFF) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Codepoint " + codepoint + " is not in the valid range 1 to 1114111");
            }
            // Check for XML-illegal characters (surrogates, etc.)
            if (!isXmlChar((int) codepoint)) {
                throw new XPathException(this, FOCH0005,
                        "Codepoint " + codepoint + " is not a valid XML character");
            }
            return new StringValue(this, new String(Character.toChars((int) codepoint)));
        } else if (Type.subTypeOf(type, Type.DOUBLE) || Type.subTypeOf(type, Type.FLOAT)
                || Type.subTypeOf(type, Type.DECIMAL)) {
            // Numeric but not integer — try to convert
            final NumericValue num = (NumericValue) item;
            if (num.hasFractionalPart()) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Codepoint must be an integer, got " + Type.getTypeName(type));
            }
            final long codepoint = num.getLong();
            if (codepoint < 1 || codepoint > 0x10FFFF) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Codepoint " + codepoint + " is not in the valid range 1 to 1114111");
            }
            if (!isXmlChar((int) codepoint)) {
                throw new XPathException(this, FOCH0005,
                        "Codepoint " + codepoint + " is not a valid XML character");
            }
            return new StringValue(this, new String(Character.toChars((int) codepoint)));
        } else {
            // Character name lookup
            final String name = item.getStringValue();

            // Handle backslash escapes
            switch (name) {
                case "\\n": return new StringValue(this, "\n");
                case "\\r": return new StringValue(this, "\r");
                case "\\t": return new StringValue(this, "\t");
            }

            // Try HTML5 named character reference first (case-sensitive per spec)
            final Map<String, String> entities = getHtmlEntities();
            String resolved = entities.get(name);
            if (resolved != null) {
                return new StringValue(this, resolved);
            }

            // Try Unicode character name
            try {
                final int cp = Character.codePointOf(name.replace(" ", "_").replace("-", "_").toUpperCase());
                if (isXmlChar(cp)) {
                    return new StringValue(this, new String(Character.toChars(cp)));
                }
            } catch (final IllegalArgumentException e) {
                // Not a Unicode name either
            }

            throw new XPathException(this, FOCH0005,
                    "Unknown character name: " + name);
        }
    }

    private static boolean isXmlChar(final int cp) {
        return cp == 0x9 || cp == 0xA || cp == 0xD
                || (cp >= 0x20 && cp <= 0xD7FF)
                || (cp >= 0xE000 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0x10FFFF);
    }

    private static Map<String, String> getHtmlEntities() {
        if (htmlEntities == null) {
            synchronized (FnChar.class) {
                if (htmlEntities == null) {
                    htmlEntities = loadHtmlEntities();
                }
            }
        }
        return htmlEntities;
    }

    private static Map<String, String> loadHtmlEntities() {
        final Map<String, String> map = new HashMap<>(2500);

        // Load from bundled resource file
        final InputStream is = FnChar.class.getResourceAsStream("html5-entities.properties");
        if (is != null) {
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    final int eq = line.indexOf('=');
                    if (eq > 0) {
                        final String entityName = line.substring(0, eq);
                        final String codepoints = line.substring(eq + 1);
                        map.put(entityName, decodeCodepoints(codepoints));
                    }
                }
            } catch (final IOException e) {
                // Fall through with partial map
            }
        }

        // Add a few critical aliases if the file wasn't found
        if (map.isEmpty()) {
            addCommonEntities(map);
        }

        return map;
    }

    private static String decodeCodepoints(final String spec) {
        // Format: "U+XXXX" or "U+XXXX,U+YYYY"
        final StringBuilder sb = new StringBuilder();
        for (final String part : spec.split(",")) {
            final String trimmed = part.trim();
            if (trimmed.startsWith("U+") || trimmed.startsWith("u+")) {
                final int cp = Integer.parseInt(trimmed.substring(2), 16);
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    private static void addCommonEntities(final Map<String, String> map) {
        map.put("amp", "&");
        map.put("lt", "<");
        map.put("gt", ">");
        map.put("quot", "\"");
        map.put("apos", "'");
        map.put("nbsp", "\u00A0");
        map.put("tab", "\t");
        map.put("newline", "\n");
    }
}
