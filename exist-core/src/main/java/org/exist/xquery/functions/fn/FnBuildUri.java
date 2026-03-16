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

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Implements fn:build-uri (XQuery 4.0).
 *
 * Constructs a URI from the parts provided in a map.
 */
public class FnBuildUri extends BasicFunction {

    private static final Set<String> NON_HIERARCHICAL_SCHEMES = new HashSet<>(Arrays.asList(
            "mailto", "news", "urn", "tel", "tag", "jar", "data", "javascript", "cid", "mid"
    ));

    public static final FunctionSignature[] FN_BUILD_URI = {
            new FunctionSignature(
                    new QName("build-uri", Function.BUILTIN_FUNCTION_NS),
                    "Constructs a URI from the parts provided.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("parts", Type.MAP_ITEM,
                                    Cardinality.EXACTLY_ONE, "Map of URI components")
                    },
                    new FunctionReturnSequenceType(Type.STRING,
                            Cardinality.EXACTLY_ONE, "The constructed URI")),
            new FunctionSignature(
                    new QName("build-uri", Function.BUILTIN_FUNCTION_NS),
                    "Constructs a URI from the parts provided.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("parts", Type.MAP_ITEM,
                                    Cardinality.EXACTLY_ONE, "Map of URI components"),
                            new FunctionParameterSequenceType("options", Type.MAP_ITEM,
                                    Cardinality.ZERO_OR_ONE, "Options map")
                    },
                    new FunctionReturnSequenceType(Type.STRING,
                            Cardinality.EXACTLY_ONE, "The constructed URI"))
    };

    public FnBuildUri(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final MapType parts = (MapType) args[0].itemAt(0);

        // Parse options
        boolean allowDeprecated = false;
        boolean omitDefaultPorts = false;
        boolean uncPath = false;
        if (args.length > 1 && !args[1].isEmpty()) {
            final MapType options = (MapType) args[1].itemAt(0);
            allowDeprecated = getBooleanOption(options, "allow-deprecated-features", false);
            omitDefaultPorts = getBooleanOption(options, "omit-default-ports", false);
            uncPath = getBooleanOption(options, "unc-path", false);
        }

        final StringBuilder uri = new StringBuilder();

        // Get scheme
        final String scheme = getStringValue(parts, "scheme");

        // Determine if hierarchical
        boolean hierarchical = true;
        final Sequence hierSeq = parts.get(new StringValue(this, "hierarchical"));
        if (hierSeq != null && !hierSeq.isEmpty()) {
            hierarchical = hierSeq.effectiveBooleanValue();
        } else if (scheme != null) {
            hierarchical = !NON_HIERARCHICAL_SCHEMES.contains(scheme.toLowerCase());
        }

        // Add scheme
        if (scheme != null) {
            uri.append(scheme);
            if (!hierarchical) {
                uri.append(':');
            } else if ("file".equalsIgnoreCase(scheme) && uncPath) {
                uri.append(":////");
            } else {
                uri.append("://");
            }
        }

        // Build authority from components or use authority directly
        final String userinfo = getStringValue(parts, "userinfo");
        final String host = getStringValue(parts, "host");
        final Sequence portSeq = parts.get(new StringValue(this, "port"));
        Integer port = null;
        if (portSeq != null && !portSeq.isEmpty()) {
            port = ((Number) portSeq.itemAt(0).toJavaObject(Long.class)).intValue();
        }

        // Handle deprecated password in userinfo
        String effectiveUserinfo = userinfo;
        if (!allowDeprecated && effectiveUserinfo != null && effectiveUserinfo.contains(":")) {
            final String password = effectiveUserinfo.substring(effectiveUserinfo.indexOf(':') + 1);
            if (!password.isEmpty()) {
                effectiveUserinfo = null;
            }
        }

        // Omit default ports
        if (omitDefaultPorts && port != null && scheme != null) {
            if (isDefaultPort(scheme.toLowerCase(), port)) {
                port = null;
            }
        }

        if (effectiveUserinfo != null || host != null || port != null) {
            if (scheme == null) {
                uri.append("//");
            }
            if (effectiveUserinfo != null) {
                uri.append(effectiveUserinfo).append('@');
            }
            if (host != null) {
                uri.append(host);
            }
            if (port != null) {
                uri.append(':').append(port);
            }
        } else {
            final String authority = getStringValue(parts, "authority");
            if (authority != null) {
                if (scheme == null) {
                    uri.append("//");
                }
                uri.append(authority);
            }
        }

        // Build path from path-segments or use path directly
        final Sequence pathSegments = parts.get(new StringValue(this, "path-segments"));
        if (pathSegments != null && !pathSegments.isEmpty()) {
            final StringBuilder pathBuilder = new StringBuilder();
            boolean first = true;
            for (final SequenceIterator i = pathSegments.iterate(); i.hasNext(); ) {
                if (!first) {
                    pathBuilder.append('/');
                }
                first = false;
                final String segment = i.nextItem().getStringValue();
                if (hierarchical) {
                    pathBuilder.append(encodePathSegment(segment));
                } else {
                    pathBuilder.append(segment);
                }
            }
            uri.append(pathBuilder);
        } else {
            final String path = getStringValue(parts, "path");
            if (path != null) {
                uri.append(path);
            }
        }

        // Build query from query-parameters or use query directly
        final Sequence queryParamsSeq = parts.get(new StringValue(this, "query-parameters"));
        if (queryParamsSeq != null && !queryParamsSeq.isEmpty() && queryParamsSeq.itemAt(0) instanceof MapType) {
            final MapType queryParams = (MapType) queryParamsSeq.itemAt(0);
            final StringBuilder queryBuilder = new StringBuilder();
            boolean first = true;
            for (final SequenceIterator ki = queryParams.keys().iterate(); ki.hasNext(); ) {
                final StringValue key = (StringValue) ki.nextItem();
                final Sequence values = queryParams.get(key);
                for (final SequenceIterator vi = values.iterate(); vi.hasNext(); ) {
                    if (!first) {
                        queryBuilder.append('&');
                    }
                    first = false;
                    final String keyStr = key.getStringValue();
                    final String valStr = vi.nextItem().getStringValue();
                    if (keyStr.isEmpty()) {
                        queryBuilder.append(encodeQueryComponent(valStr));
                    } else {
                        queryBuilder.append(encodeQueryComponent(keyStr))
                                .append('=')
                                .append(encodeQueryComponent(valStr));
                    }
                }
            }
            if (queryBuilder.length() > 0) {
                uri.append('?').append(queryBuilder);
            }
        } else {
            final String query = getStringValue(parts, "query");
            if (query != null) {
                uri.append('?').append(query);
            }
        }

        // Fragment
        final String fragment = getStringValue(parts, "fragment");
        if (fragment != null) {
            uri.append('#').append(encodeFragment(fragment));
        }

        return new StringValue(this, uri.toString());
    }

    private String getStringValue(final MapType map, final String key) throws XPathException {
        final Sequence val = map.get(new StringValue(this, key));
        if (val != null && !val.isEmpty()) {
            return val.getStringValue();
        }
        return null;
    }

    private boolean getBooleanOption(final MapType options, final String key,
            final boolean defaultValue) throws XPathException {
        final Sequence val = options.get(new StringValue(this, key));
        if (val != null && !val.isEmpty()) {
            return val.effectiveBooleanValue();
        }
        return defaultValue;
    }

    private static boolean isDefaultPort(final String scheme, final int port) {
        switch (scheme) {
            case "http": return port == 80;
            case "https": return port == 443;
            case "ftp": return port == 21;
            case "ssh": return port == 22;
            default: return false;
        }
    }

    // Encode path segment: control chars + space % / ? # + [ ]
    private static String encodePathSegment(final String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c < 0x20 || c == ' ' || c == '%' || c == '/' || c == '?'
                    || c == '#' || c == '+' || c == '[' || c == ']') {
                appendPercentEncoded(sb, c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // Encode query component: control chars + space % = & # + [ ]
    private static String encodeQueryComponent(final String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c < 0x20 || c == ' ' || c == '%' || c == '=' || c == '&'
                    || c == '#' || c == '+' || c == '[' || c == ']') {
                appendPercentEncoded(sb, c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // Encode fragment: control chars + space % # + [ ]
    private static String encodeFragment(final String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c < 0x20 || c == ' ' || c == '%' || c == '#' || c == '+' || c == '[' || c == ']') {
                appendPercentEncoded(sb, c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void appendPercentEncoded(final StringBuilder sb, final char c) {
        if (c < 0x80) {
            sb.append('%').append(String.format("%02X", (int) c));
        } else {
            try {
                final byte[] bytes = String.valueOf(c).getBytes("UTF-8");
                for (final byte b : bytes) {
                    sb.append('%').append(String.format("%02X", b & 0xFF));
                }
            } catch (final UnsupportedEncodingException e) {
                sb.append(c);
            }
        }
    }
}
