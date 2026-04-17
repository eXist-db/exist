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
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements fn:parse-uri (XQuery 4.0).
 *
 * Parses a URI string and returns a map of its constituent parts,
 * following the algorithm specified in XPath Functions 4.0.
 */
public class FnParseUri extends BasicFunction {

    private static final ErrorCodes.ErrorCode FOUR0001 = new ErrorCodes.ErrorCode(
            "FOUR0001", "Invalid URI");

    private static final Pattern SCHEME_PATTERN =
            Pattern.compile("^([a-zA-Z][A-Za-z0-9+\\-.]+):(.*)$");
    private static final Pattern FRAGMENT_PATTERN =
            Pattern.compile("^(.*?)#(.*)$");
    private static final Pattern QUERY_PATTERN =
            Pattern.compile("^(.*?)\\?(.*)$");
    private static final Pattern DRIVE_LETTER_PATTERN =
            Pattern.compile("^/*([a-zA-Z][:|].*)$");
    private static final Pattern AUTHORITY_PATH_PATTERN =
            Pattern.compile("^//([^/]*)(/.*)$");
    private static final Pattern AUTHORITY_ONLY_PATTERN =
            Pattern.compile("^//([^/]+)$");

    // Authority parsing patterns from the spec
    private static final Pattern AUTH_IPV6_PATTERN =
            Pattern.compile("^(([^@]*)@)?(\\[[^\\]]*\\])(:([^:]*))?$");
    private static final Pattern AUTH_IPV6_OPEN_PATTERN =
            Pattern.compile("^(([^@]*)@)?\\[.*$");
    private static final Pattern AUTH_NORMAL_PATTERN =
            Pattern.compile("^(([^@]*)@)?([^:]+)(:([^:]*))?$");

    private static final Set<String> NON_HIERARCHICAL_SCHEMES = new HashSet<>(Arrays.asList(
            "mailto", "news", "urn", "tel", "tag", "jar", "data", "javascript", "cid", "mid"
    ));
    private static final Set<String> HIERARCHICAL_SCHEMES = new HashSet<>(Arrays.asList(
            "http", "https", "ftp", "ftps", "sftp", "file", "ssh", "telnet",
            "ldap", "ldaps", "svn", "svn+ssh", "git", "s3", "hdfs"
    ));

    public static final FunctionSignature[] FN_PARSE_URI = {
            new FunctionSignature(
                    new QName("parse-uri", Function.BUILTIN_FUNCTION_NS),
                    "Parses a URI and returns a map of its components.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.STRING,
                                    Cardinality.ZERO_OR_ONE, "The URI to parse")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM,
                            Cardinality.ZERO_OR_ONE, "map of URI components")),
            new FunctionSignature(
                    new QName("parse-uri", Function.BUILTIN_FUNCTION_NS),
                    "Parses a URI and returns a map of its components.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.STRING,
                                    Cardinality.ZERO_OR_ONE, "The URI to parse"),
                            new FunctionParameterSequenceType("options", Type.MAP_ITEM,
                                    Cardinality.ZERO_OR_ONE, "Options map")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM,
                            Cardinality.ZERO_OR_ONE, "map of URI components"))
    };

    public FnParseUri(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String originalUri = args[0].getStringValue();
        if (originalUri.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

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

        // Step 1: Replace backslashes with forward slashes
        String str = originalUri.replace('\\', '/');

        // Step 2: Strip fragment
        String fragment = null;
        Matcher m = FRAGMENT_PATTERN.matcher(str);
        if (m.matches()) {
            str = m.group(1);
            fragment = m.group(2);
            if (fragment.isEmpty()) {
                fragment = null;
            } else {
                fragment = uriDecode(fragment);
            }
        }

        // Step 3: Strip query
        String query = null;
        m = QUERY_PATTERN.matcher(str);
        if (m.matches()) {
            str = m.group(1);
            query = m.group(2);
            if (query.isEmpty()) {
                query = null;
            }
        }

        // Step 4: Identify scheme
        String scheme = null;
        m = SCHEME_PATTERN.matcher(str);
        if (m.matches()) {
            scheme = m.group(1);
            str = m.group(2);
        }

        // Step 5: absolute flag — scheme present and no fragment
        Boolean absolute = (scheme != null && fragment == null) ? Boolean.TRUE : null;

        // Step 6: Handle file: and drive letters
        String filepath = null;
        if (scheme == null || "file".equalsIgnoreCase(scheme)) {
            m = DRIVE_LETTER_PATTERN.matcher(str);
            if (m.matches()) {
                scheme = "file";
                String matched = m.group(1);
                // Replace | with : if necessary
                if (matched.length() > 1 && matched.charAt(1) == '|') {
                    matched = matched.charAt(0) + ":" + matched.substring(2);
                }
                str = "/" + matched;
            } else if (uncPath && scheme == null) {
                scheme = "file";
            }
        }

        // Step 7: Determine hierarchical
        Boolean hierarchical = null;
        if (scheme != null) {
            final String schemeLower = scheme.toLowerCase();
            if (HIERARCHICAL_SCHEMES.contains(schemeLower)) {
                hierarchical = Boolean.TRUE;
            } else if (NON_HIERARCHICAL_SCHEMES.contains(schemeLower)) {
                hierarchical = Boolean.FALSE;
            } else if (str.isEmpty()) {
                hierarchical = null;
            } else {
                hierarchical = str.startsWith("/");
            }
        } else {
            // No scheme — hierarchical if starts with /
            if (!str.isEmpty()) {
                hierarchical = str.startsWith("/");
            }
        }

        // Non-hierarchical → absolute is not applicable
        if (hierarchical != null && !hierarchical) {
            absolute = null;
        }

        // Step 8: Handle file: scheme filepath
        if ("file".equalsIgnoreCase(scheme)) {
            if (uncPath) {
                // UNC path handling
                final Pattern uncPattern = Pattern.compile("^/*(//[^/].*)$");
                m = uncPattern.matcher(str);
                if (m.matches()) {
                    filepath = m.group(1);
                    str = filepath;
                }
            }
            if (filepath == null) {
                // Check for //X:/ pattern (multiple leading slashes before drive)
                if (str.matches("^//*[A-Za-z]:/.*$")) {
                    // Remove all but one leading slash
                    str = str.replaceFirst("^/+", "/");
                    filepath = str.replaceFirst("^/", "");
                } else {
                    // Replace multiple leading slashes with single slash
                    str = str.replaceFirst("^/+", "/");
                    filepath = str;
                }
            }
        }

        // Step 9: Extract authority (hierarchical URIs only, NOT file: scheme)
        String authority = null;
        if (hierarchical != null && hierarchical
                && !"file".equalsIgnoreCase(scheme)) {
            m = AUTHORITY_ONLY_PATTERN.matcher(str);
            if (m.matches()) {
                authority = m.group(1);
                str = "";
            } else {
                m = AUTHORITY_PATH_PATTERN.matcher(str);
                if (m.matches()) {
                    authority = m.group(1);
                    str = m.group(2);
                }
            }
            // Treat empty authority as absent
            if (authority != null && authority.isEmpty()) {
                authority = null;
            }
        }

        // Step 10: Parse authority into userinfo, host, port
        String userinfo = null;
        String host = null;
        Integer port = null;
        if (authority != null && !authority.isEmpty()) {
            // Parse userinfo
            final int atIdx = authority.indexOf('@');
            String authRemainder = authority;
            if (atIdx >= 0) {
                userinfo = authority.substring(0, atIdx);
                authRemainder = authority.substring(atIdx + 1);
                // Check for deprecated password
                if (!allowDeprecated && userinfo.contains(":")) {
                    final String password = userinfo.substring(userinfo.indexOf(':') + 1);
                    if (!password.isEmpty()) {
                        userinfo = null;
                    }
                }
            }

            // Parse host and port from authRemainder
            m = AUTH_IPV6_PATTERN.matcher(authority);
            if (m.matches()) {
                host = m.group(3);
                final String portStr = m.group(5);
                if (portStr != null && !portStr.isEmpty()) {
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (final NumberFormatException ignored) {
                    }
                }
            } else {
                m = AUTH_IPV6_OPEN_PATTERN.matcher(authority);
                if (m.matches()) {
                    throw new XPathException(this, FOUR0001,
                            "Unmatched '[' in URI authority: " + authority);
                }
                m = AUTH_NORMAL_PATTERN.matcher(authority);
                if (m.matches()) {
                    host = m.group(3);
                    final String portStr = m.group(5);
                    if (portStr != null && !portStr.isEmpty()) {
                        try {
                            port = Integer.parseInt(portStr);
                        } catch (final NumberFormatException ignored) {
                        }
                    }
                }
            }

            // Omit default ports
            if (omitDefaultPorts && port != null && scheme != null) {
                if (isDefaultPort(scheme.toLowerCase(), port)) {
                    port = null;
                }
            }
        }

        // Step 11: Determine path and filepath
        final String path = str.isEmpty() ? null : str;
        if (scheme == null && filepath == null && path != null) {
            filepath = path;
        }
        // URI-decode filepath
        if (filepath != null) {
            filepath = uriDecode(filepath);
        }

        // Step 12: Build path-segments
        List<String> pathSegments = null;
        if (path != null) {
            final String[] parts = str.split("/", -1);
            pathSegments = new ArrayList<>(parts.length);
            for (final String part : parts) {
                pathSegments.add(uriDecode(part));
            }
        }

        // Step 13: Parse query parameters
        MapType queryParams = null;
        if (query != null && !query.isEmpty()) {
            queryParams = new MapType(this, context);
            for (final String param : query.split("&")) {
                final int eq = param.indexOf('=');
                final String key;
                final String value;
                if (eq >= 0) {
                    key = uriDecode(param.substring(0, eq));
                    value = uriDecode(param.substring(eq + 1));
                } else {
                    key = "";
                    value = uriDecode(param);
                }
                final AtomicValue keyVal = new StringValue(this, key);
                final Sequence existing = queryParams.get(keyVal);
                if (existing != null && !existing.isEmpty()) {
                    final ValueSequence combined = new ValueSequence();
                    combined.addAll(existing);
                    combined.add(new StringValue(this, value));
                    queryParams.add(keyVal, combined);
                } else {
                    queryParams.add(keyVal, new StringValue(this, value));
                }
            }
        }

        // Build result map — omit keys with empty values per spec
        final MapType result = new MapType(this, context);

        // uri (the original input, always present)
        result.add(new StringValue(this, "uri"), new StringValue(this, originalUri));

        if (scheme != null) {
            result.add(new StringValue(this, "scheme"), new StringValue(this, scheme));
        }
        if (hierarchical != null) {
            result.add(new StringValue(this, "hierarchical"), BooleanValue.valueOf(hierarchical));
        }
        if (absolute != null) {
            result.add(new StringValue(this, "absolute"), BooleanValue.valueOf(absolute));
        }
        if (authority != null) {
            result.add(new StringValue(this, "authority"), new StringValue(this, authority));
        }
        if (userinfo != null) {
            result.add(new StringValue(this, "userinfo"), new StringValue(this, userinfo));
        }
        if (host != null) {
            result.add(new StringValue(this, "host"), new StringValue(this, host));
        }
        if (port != null) {
            result.add(new StringValue(this, "port"), new IntegerValue(this, port));
        }
        if (path != null) {
            result.add(new StringValue(this, "path"), new StringValue(this, path));
        }
        if (filepath != null) {
            result.add(new StringValue(this, "filepath"), new StringValue(this, filepath));
        }
        if (pathSegments != null) {
            final ValueSequence segSeq = new ValueSequence(pathSegments.size());
            for (final String seg : pathSegments) {
                segSeq.add(new StringValue(this, seg));
            }
            result.add(new StringValue(this, "path-segments"), segSeq);
        }
        if (query != null) {
            result.add(new StringValue(this, "query"), new StringValue(this, query));
        }
        if (queryParams != null) {
            result.add(new StringValue(this, "query-parameters"), queryParams);
        } else if (query != null) {
            result.add(new StringValue(this, "query-parameters"), new MapType(this, context));
        }
        if (fragment != null) {
            result.add(new StringValue(this, "fragment"), new StringValue(this, fragment));
        }

        return result;
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

    private static String uriDecode(final String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        if (s.indexOf('%') < 0 && s.indexOf('+') < 0) {
            return s;
        }
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (final UnsupportedEncodingException | IllegalArgumentException e) {
            return s;
        }
    }
}
