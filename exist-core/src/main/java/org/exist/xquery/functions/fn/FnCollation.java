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

import io.lacuna.bifurcan.IEntry;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;
import org.exist.util.Collations;

import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * fn:collation() — Returns the default collation URI.
 * fn:collation($uri as xs:string) — Returns the collation URI if supported.
 * fn:collation($options as map(*)) — Constructs a UCA collation URI from options.
 * fn:collation-available($collation as xs:string) — Returns true if the collation is supported.
 */
public class FnCollation extends BasicFunction {

    private static final String UCA_BASE = Collations.UCA_COLLATION_URI;

    /**
     * Valid UCA option keys as defined in the XQuery 4.0 specification.
     * Map keys use hyphenated form; URI parameters use camelCase.
     */
    private static final Map<String, String> OPTION_KEY_TO_URI_PARAM = Map.ofEntries(
            Map.entry("fallback", "fallback"),
            Map.entry("lang", "lang"),
            Map.entry("version", "version"),
            Map.entry("strength", "strength"),
            Map.entry("maxVariable", "maxVariable"),
            Map.entry("max-variable", "maxVariable"),
            Map.entry("alternate", "alternate"),
            Map.entry("backwards", "backwards"),
            Map.entry("normalization", "normalization"),
            Map.entry("caseLevel", "caseLevel"),
            Map.entry("case-level", "caseLevel"),
            Map.entry("caseFirst", "caseFirst"),
            Map.entry("case-first", "caseFirst"),
            Map.entry("numeric", "numeric"),
            Map.entry("reorder", "reorder")
    );

    private static final Set<String> VALID_STRENGTH = Set.of(
            "primary", "secondary", "tertiary", "quaternary", "identical",
            "1", "2", "3", "4", "5"
    );
    private static final Set<String> VALID_MAX_VARIABLE = Set.of(
            "space", "punct", "symbol", "currency"
    );
    private static final Set<String> VALID_ALTERNATE = Set.of(
            "non-ignorable", "shifted", "blanked"
    );
    private static final Set<String> VALID_CASE_FIRST = Set.of(
            "upper", "lower"
    );
    private static final Set<String> VALID_BOOLEAN = Set.of(
            "yes", "no", "true", "false"
    );

    public static final FunctionSignature[] FN_COLLATION = {
            new FunctionSignature(
                    new QName("collation", Function.BUILTIN_FUNCTION_NS),
                    "Returns the URI of the default collation.",
                    null,
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE,
                            "The default collation URI")),
            new FunctionSignature(
                    new QName("collation", Function.BUILTIN_FUNCTION_NS),
                    "With a string argument, returns the collation URI if supported. " +
                    "With a map argument, constructs a UCA collation URI from options.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("options", Type.MAP_ITEM,
                                    Cardinality.EXACTLY_ONE,
                                    "A map of UCA collation options")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE,
                            "The collation URI"))
    };

    public static final FunctionSignature FN_COLLATION_AVAILABLE = new FunctionSignature(
            new QName("collation-available", Function.BUILTIN_FUNCTION_NS),
            "Returns true if the specified collation is supported.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("collation", Type.STRING,
                            Cardinality.EXACTLY_ONE, "The collation URI")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                    "true if the collation is supported"));

    public FnCollation(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs("collation")) {
            if (getArgumentCount() == 0) {
                // 0-arg: return default collation
                final String defaultCollation = context.getDefaultCollation();
                return new StringValue(this, defaultCollation != null ? defaultCollation
                        : Collations.UNICODE_CODEPOINT_COLLATION_URI);
            }
            if (getArgumentCount() == 1) {
                final Item firstArg = args[0].itemAt(0);
                if (firstArg instanceof AbstractMapType) {
                    // 1-arg map: construct UCA collation URI from options
                    return collationFromMap((AbstractMapType) firstArg);
                }
                // 1-arg string: check if the named collation is supported
                final String uri = args[0].getStringValue();
                try {
                    context.getCollator(uri);
                    return new StringValue(this, uri);
                } catch (final XPathException e) {
                    return Sequence.EMPTY_SEQUENCE;
                }
            }
        }
        // collation-available
        final String uri = args[0].getStringValue();
        try {
            context.getCollator(uri);
            return BooleanValue.TRUE;
        } catch (final XPathException e) {
            return BooleanValue.FALSE;
        }
    }

    /**
     * Construct a UCA collation URI from a map of options.
     *
     * <p>The map keys correspond to UCA collation parameters as defined in the
     * XQuery 4.0 specification. Boolean values are converted to "yes"/"no".
     * When fallback is false, unknown or invalid options raise FOCH0002.</p>
     */
    private Sequence collationFromMap(final AbstractMapType map) throws XPathException {
        boolean fallback = true;

        // First pass: determine fallback setting
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            final String key = entry.key().getStringValue();
            if ("fallback".equals(key)) {
                final String val = toStringValue(entry.value());
                fallback = !"no".equals(val) && !"false".equals(val);
            }
        }

        final StringJoiner params = new StringJoiner(";");

        // Second pass: validate and build URI parameters
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            final String key = entry.key().getStringValue();
            final String val = toStringValue(entry.value());

            final String uriParam = OPTION_KEY_TO_URI_PARAM.get(key);
            if (uriParam == null) {
                // Unknown option key
                if (!fallback) {
                    throw new XPathException(this, ErrorCodes.FOCH0002,
                            "Unknown collation option: " + key);
                }
                // With fallback, silently ignore unknown keys
                continue;
            }

            // Validate option values when fallback is false
            if (!fallback) {
                validateOptionValue(key, uriParam, val);
            }

            params.add(uriParam + "=" + val);
        }

        final String query = params.toString();
        final String uri = query.isEmpty() ? UCA_BASE : UCA_BASE + "?" + query;
        return new StringValue(this, uri);
    }

    /**
     * Convert a sequence value to a string suitable for a URI parameter.
     * Boolean values are converted to "yes"/"no".
     */
    private static String toStringValue(final Sequence value) throws XPathException {
        if (value.isEmpty()) {
            return "";
        }
        final Item item = value.itemAt(0);
        if (item.getType() == Type.BOOLEAN) {
            return ((BooleanValue) item).getValue() ? "yes" : "no";
        }
        return item.getStringValue();
    }

    /**
     * Validate an option value when fallback is false.
     * Raises FOCH0002 for invalid values.
     */
    private void validateOptionValue(final String mapKey, final String uriParam, final String val)
            throws XPathException {
        switch (uriParam) {
            case "strength" -> {
                if (!VALID_STRENGTH.contains(val)) {
                    throw new XPathException(this, ErrorCodes.FOCH0002,
                            "Invalid collation strength: " + val);
                }
            }
            case "maxVariable" -> {
                if (!VALID_MAX_VARIABLE.contains(val)) {
                    throw new XPathException(this, ErrorCodes.FOCH0002,
                            "Invalid collation maxVariable: " + val);
                }
            }
            case "alternate" -> {
                if (!VALID_ALTERNATE.contains(val)) {
                    throw new XPathException(this, ErrorCodes.FOCH0002,
                            "Invalid collation alternate: " + val);
                }
            }
            case "caseFirst" -> {
                if (!VALID_CASE_FIRST.contains(val)) {
                    throw new XPathException(this, ErrorCodes.FOCH0002,
                            "Invalid collation case-first: " + val);
                }
            }
            case "backwards", "normalization", "caseLevel", "numeric" -> {
                if (!VALID_BOOLEAN.contains(val)) {
                    throw new XPathException(this, ErrorCodes.FOCH0002,
                            "Invalid boolean value for " + mapKey + ": " + val);
                }
            }
            case "fallback" -> {
                // fallback itself is always valid (already parsed)
            }
            default -> {
                // lang, version, reorder: accept any string value
            }
        }
    }
}
