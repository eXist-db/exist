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

    /** Mutable holder for parsed URI parts; populated step-by-step by eval(). */
    private static final class Parts {
        String str;
        String scheme;
        String fragment;
        String query;
        String filepath;
        String authority;
        String userinfo;
        String host;
        Integer port;
        Boolean absolute;
        Boolean hierarchical;
    }

    private static final Pattern UNC_PATTERN = Pattern.compile("^/*(//[^/].*)$");
    private static final Pattern MULTI_SLASH_DRIVE_PATTERN = Pattern.compile("^//*[A-Za-z]:/.*$");

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final String originalUri = args[0].getStringValue();
        if (originalUri.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final boolean[] opts = parseOptions(args);
        final boolean allowDeprecated = opts[0];
        final boolean omitDefaultPorts = opts[1];
        final boolean uncPath = opts[2];

        final Parts p = new Parts();
        p.str = originalUri.replace('\\', '/');

        stripFragment(p);
        stripQuery(p);
        extractScheme(p);
        p.absolute = (p.scheme != null && p.fragment == null) ? Boolean.TRUE : null;
        handleDriveLetter(p, uncPath);
        determineHierarchical(p);
        if (p.hierarchical != null && !p.hierarchical) {
            p.absolute = null;
        }
        handleFilePath(p, uncPath);
        extractAuthority(p);
        parseAuthority(p, allowDeprecated, omitDefaultPorts);

        final String path = p.str.isEmpty() ? null : p.str;
        if (p.scheme == null && p.filepath == null && path != null) {
            p.filepath = path;
        }
        if (p.filepath != null) {
            p.filepath = uriDecode(p.filepath);
        }

        final List<String> pathSegments = buildPathSegments(p.str, path);
        final MapType queryParams = parseQueryParams(p.query);

        return buildResult(originalUri, p, path, pathSegments, queryParams);
    }

    private boolean[] parseOptions(final Sequence[] args) throws XPathException {
        if (args.length <= 1 || args[1].isEmpty()) {
            return new boolean[] { false, false, false };
        }
        final MapType options = (MapType) args[1].itemAt(0);
        return new boolean[] {
                getBooleanOption(options, "allow-deprecated-features", false),
                getBooleanOption(options, "omit-default-ports", false),
                getBooleanOption(options, "unc-path", false)
        };
    }

    private static void stripFragment(final Parts p) {
        final Matcher m = FRAGMENT_PATTERN.matcher(p.str);
        if (!m.matches()) {
            return;
        }
        p.str = m.group(1);
        final String f = m.group(2);
        p.fragment = f.isEmpty() ? null : uriDecode(f);
    }

    private static void stripQuery(final Parts p) {
        final Matcher m = QUERY_PATTERN.matcher(p.str);
        if (!m.matches()) {
            return;
        }
        p.str = m.group(1);
        final String q = m.group(2);
        p.query = q.isEmpty() ? null : q;
    }

    private static void extractScheme(final Parts p) {
        final Matcher m = SCHEME_PATTERN.matcher(p.str);
        if (m.matches()) {
            p.scheme = m.group(1);
            p.str = m.group(2);
        }
    }

    private static void handleDriveLetter(final Parts p, final boolean uncPath) {
        if (p.scheme != null && !"file".equalsIgnoreCase(p.scheme)) {
            return;
        }
        final Matcher m = DRIVE_LETTER_PATTERN.matcher(p.str);
        if (m.matches()) {
            p.scheme = "file";
            String matched = m.group(1);
            if (matched.length() > 1 && matched.charAt(1) == '|') {
                matched = matched.charAt(0) + ":" + matched.substring(2);
            }
            p.str = "/" + matched;
        } else if (uncPath && p.scheme == null) {
            p.scheme = "file";
        }
    }

    private static void determineHierarchical(final Parts p) {
        if (p.scheme == null) {
            if (!p.str.isEmpty()) {
                p.hierarchical = p.str.startsWith("/");
            }
            return;
        }
        final String schemeLower = p.scheme.toLowerCase();
        if (HIERARCHICAL_SCHEMES.contains(schemeLower)) {
            p.hierarchical = Boolean.TRUE;
        } else if (NON_HIERARCHICAL_SCHEMES.contains(schemeLower)) {
            p.hierarchical = Boolean.FALSE;
        } else if (!p.str.isEmpty()) {
            p.hierarchical = p.str.startsWith("/");
        }
    }

    private static void handleFilePath(final Parts p, final boolean uncPath) {
        if (!"file".equalsIgnoreCase(p.scheme)) {
            return;
        }
        if (uncPath) {
            final Matcher m = UNC_PATTERN.matcher(p.str);
            if (m.matches()) {
                p.filepath = m.group(1);
                p.str = p.filepath;
            }
        }
        if (p.filepath != null) {
            return;
        }
        if (MULTI_SLASH_DRIVE_PATTERN.matcher(p.str).matches()) {
            p.str = p.str.replaceFirst("^/+", "/");
            p.filepath = p.str.replaceFirst("^/", "");
        } else {
            p.str = p.str.replaceFirst("^/+", "/");
            p.filepath = p.str;
        }
    }

    private static void extractAuthority(final Parts p) {
        if (p.hierarchical == null || !p.hierarchical
                || "file".equalsIgnoreCase(p.scheme)) {
            return;
        }
        Matcher m = AUTHORITY_ONLY_PATTERN.matcher(p.str);
        if (m.matches()) {
            p.authority = m.group(1);
            p.str = "";
        } else {
            m = AUTHORITY_PATH_PATTERN.matcher(p.str);
            if (m.matches()) {
                p.authority = m.group(1);
                p.str = m.group(2);
            }
        }
        if (p.authority != null && p.authority.isEmpty()) {
            p.authority = null;
        }
    }

    private void parseAuthority(final Parts p, final boolean allowDeprecated,
            final boolean omitDefaultPorts) throws XPathException {
        if (p.authority == null || p.authority.isEmpty()) {
            return;
        }
        extractUserinfo(p, allowDeprecated);
        extractHostAndPort(p);
        if (omitDefaultPorts && p.port != null && p.scheme != null
                && isDefaultPort(p.scheme.toLowerCase(), p.port)) {
            p.port = null;
        }
    }

    private static void extractUserinfo(final Parts p, final boolean allowDeprecated) {
        final int atIdx = p.authority.indexOf('@');
        if (atIdx < 0) {
            return;
        }
        p.userinfo = p.authority.substring(0, atIdx);
        if (!allowDeprecated && p.userinfo.contains(":")) {
            final String password = p.userinfo.substring(p.userinfo.indexOf(':') + 1);
            if (!password.isEmpty()) {
                p.userinfo = null;
            }
        }
    }

    private void extractHostAndPort(final Parts p) throws XPathException {
        Matcher m = AUTH_IPV6_PATTERN.matcher(p.authority);
        if (m.matches()) {
            p.host = m.group(3);
            p.port = parsePort(m.group(5));
            return;
        }
        if (AUTH_IPV6_OPEN_PATTERN.matcher(p.authority).matches()) {
            throw new XPathException(this, FOUR0001,
                    "Unmatched '[' in URI authority: " + p.authority);
        }
        m = AUTH_NORMAL_PATTERN.matcher(p.authority);
        if (m.matches()) {
            p.host = m.group(3);
            p.port = parsePort(m.group(5));
        }
    }

    private static Integer parsePort(final String portStr) {
        if (portStr == null || portStr.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(portStr);
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private static List<String> buildPathSegments(final String str, final String path) {
        if (path == null) {
            return null;
        }
        final String[] parts = str.split("/", -1);
        final List<String> segs = new ArrayList<>(parts.length);
        for (final String part : parts) {
            segs.add(uriDecode(part));
        }
        return segs;
    }

    private MapType parseQueryParams(final String query) throws XPathException {
        if (query == null || query.isEmpty()) {
            return null;
        }
        final MapType params = new MapType(this, context);
        for (final String param : query.split("&")) {
            addQueryParam(params, param);
        }
        return params;
    }

    private void addQueryParam(final MapType params, final String param) throws XPathException {
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
        final Sequence existing = params.get(keyVal);
        if (existing != null && !existing.isEmpty()) {
            final ValueSequence combined = new ValueSequence();
            combined.addAll(existing);
            combined.add(new StringValue(this, value));
            params.add(keyVal, combined);
        } else {
            params.add(keyVal, new StringValue(this, value));
        }
    }

    private MapType buildResult(final String originalUri, final Parts p, final String path,
            final List<String> pathSegments, final MapType queryParams) throws XPathException {
        final MapType result = new MapType(this, context);
        result.add(new StringValue(this, "uri"), new StringValue(this, originalUri));
        putIfNotNull(result, "scheme", p.scheme);
        if (p.hierarchical != null) {
            result.add(new StringValue(this, "hierarchical"), BooleanValue.valueOf(p.hierarchical));
        }
        if (p.absolute != null) {
            result.add(new StringValue(this, "absolute"), BooleanValue.valueOf(p.absolute));
        }
        putIfNotNull(result, "authority", p.authority);
        putIfNotNull(result, "userinfo", p.userinfo);
        putIfNotNull(result, "host", p.host);
        if (p.port != null) {
            result.add(new StringValue(this, "port"), new IntegerValue(this, p.port));
        }
        putIfNotNull(result, "path", path);
        putIfNotNull(result, "filepath", p.filepath);
        if (pathSegments != null) {
            final ValueSequence segSeq = new ValueSequence(pathSegments.size());
            for (final String seg : pathSegments) {
                segSeq.add(new StringValue(this, seg));
            }
            result.add(new StringValue(this, "path-segments"), segSeq);
        }
        putIfNotNull(result, "query", p.query);
        if (queryParams != null) {
            result.add(new StringValue(this, "query-parameters"), queryParams);
        } else if (p.query != null) {
            result.add(new StringValue(this, "query-parameters"), new MapType(this, context));
        }
        putIfNotNull(result, "fragment", p.fragment);
        return result;
    }

    private void putIfNotNull(final MapType result, final String key, final String value)
            throws XPathException {
        if (value != null) {
            result.add(new StringValue(this, key), new StringValue(this, value));
        }
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
