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

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Saxon-style query string parameters shared by {@link FunUriCollection}
 * (database collections) and {@link ExtCollection} (file: URI collections).
 *
 * <p>Supports four parameters:</p>
 * <ul>
 *   <li>{@code select} — glob pattern matching resource names (file: only)</li>
 *   <li>{@code match} — regex matching resource names</li>
 *   <li>{@code content-type} — MIME type filter (database: distinguishes XML/binary/collection;
 *       file: distinguishes XML/binary documents)</li>
 *   <li>{@code stable} — {@code yes}/{@code no} for deterministic ordering and caching</li>
 * </ul>
 *
 * <p>Each consumer specifies which subset of parameters it accepts via the {@code allowedKeys}
 * argument to {@link #parse(String, Set, Expression)}. Unknown or invalid keys/values raise
 * {@link ErrorCodes#FODC0004}.</p>
 */
public final class CollectionQueryParameters {

    public static final String KEY_SELECT = "select";
    public static final String KEY_MATCH = "match";
    public static final String KEY_CONTENT_TYPE = "content-type";
    public static final String KEY_STABLE = "stable";

    public static final String VALUE_CONTENT_TYPE_DOCUMENT = "application/vnd.existdb.document";
    public static final String VALUE_CONTENT_TYPE_DOCUMENT_BINARY = "application/vnd.existdb.document+binary";
    public static final String VALUE_CONTENT_TYPE_DOCUMENT_XML = "application/vnd.existdb.document+xml";
    public static final String VALUE_CONTENT_TYPE_SUBCOLLECTION = "application/vnd.existdb.collection";
    public static final Set<String> VALUE_CONTENT_TYPES = Set.of(
            VALUE_CONTENT_TYPE_DOCUMENT,
            VALUE_CONTENT_TYPE_DOCUMENT_BINARY,
            VALUE_CONTENT_TYPE_DOCUMENT_XML,
            VALUE_CONTENT_TYPE_SUBCOLLECTION
    );

    public static final String VALUE_STABLE_NO = "no";
    public static final String VALUE_STABLE_YES = "yes";
    public static final Set<String> VALUE_STABLES = Set.of(
            VALUE_STABLE_NO,
            VALUE_STABLE_YES
    );

    /** Keys accepted by fn:uri-collection (no select). */
    public static final Set<String> URI_COLLECTION_KEYS = Set.of(KEY_MATCH, KEY_CONTENT_TYPE, KEY_STABLE);

    /** Keys accepted by fn:collection() with file: URIs (includes select). */
    public static final Set<String> FILE_COLLECTION_KEYS = Set.of(KEY_SELECT, KEY_MATCH, KEY_CONTENT_TYPE, KEY_STABLE);

    @Nullable private final String select;
    @Nullable private final String match;
    @Nullable private final String contentType;
    private final boolean stable;
    private final Map<String, String> rawMap;

    private CollectionQueryParameters(@Nullable final String select,
                                      @Nullable final String match,
                                      @Nullable final String contentType,
                                      final boolean stable,
                                      final Map<String, String> rawMap) {
        this.select = select;
        this.match = match;
        this.contentType = contentType;
        this.stable = stable;
        this.rawMap = rawMap;
    }

    /**
     * Parse the query string portion of a URI string.
     *
     * @param uriOrQueryString the full URI string (with scheme/path) or just the query portion;
     *                         if {@code null} or has no query, returns parameters with all defaults
     * @param allowedKeys      the set of accepted parameter keys; any other key raises FODC0004
     * @param caller           the calling expression for error reporting
     * @return parsed parameters
     * @throws XPathException FODC0004 if a key is not in {@code allowedKeys} or a value is invalid
     */
    public static CollectionQueryParameters parse(@Nullable final String uriOrQueryString,
                                                  final Set<String> allowedKeys,
                                                  final Expression caller) throws XPathException {
        final Map<String, String> map = parseQueryString(uriOrQueryString);
        validate(map, allowedKeys, caller);

        return new CollectionQueryParameters(
                map.get(KEY_SELECT),
                map.get(KEY_MATCH),
                map.get(KEY_CONTENT_TYPE),
                !map.containsKey(KEY_STABLE) || VALUE_STABLE_YES.equals(map.get(KEY_STABLE)),
                map);
    }

    /** Parse query parameters from the URI string into a key/value map. */
    private static Map<String, String> parseQueryString(@Nullable final String uri) {
        final Map<String, String> map = new HashMap<>();
        if (uri == null) {
            return map;
        }
        final int questionMarkIndex = uri.indexOf('?');
        if (questionMarkIndex < 0 || questionMarkIndex + 1 >= uri.length()) {
            return map;
        }
        final String[] keyValuePairs = uri.substring(questionMarkIndex + 1).split("&");
        for (final String keyValuePair : keyValuePairs) {
            final int equalIndex = keyValuePair.indexOf('=');
            if (equalIndex >= 0) {
                if (equalIndex + 1 < keyValuePair.length()) {
                    map.put(keyValuePair.substring(0, equalIndex).trim(),
                            keyValuePair.substring(equalIndex + 1).trim());
                } else {
                    map.put(keyValuePair.substring(0, equalIndex).trim(), "");
                }
            } else {
                map.put(keyValuePair.trim(), "");
            }
        }
        return map;
    }

    private static void validate(final Map<String, String> map, final Set<String> allowedKeys,
                                 final Expression caller) throws XPathException {
        for (final Map.Entry<String, String> entry : map.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();

            if (!allowedKeys.contains(key)) {
                throw new XPathException(caller, ErrorCodes.FODC0004,
                        String.format("Unexpected query string \"%s\".", entry));
            }

            if (key.equals(KEY_CONTENT_TYPE) && !VALUE_CONTENT_TYPES.contains(value)) {
                throw new XPathException(caller, ErrorCodes.FODC0004,
                        String.format("Invalid query-string value \"%s\".", entry));
            } else if (key.equals(KEY_STABLE) && !VALUE_STABLES.contains(value)) {
                throw new XPathException(caller, ErrorCodes.FODC0004,
                        String.format("Invalid query-string value \"%s\".", entry));
            }
            // KEY_SELECT and KEY_MATCH accept any string value
        }
    }

    /**
     * Strip the {@code stable=...} parameter from a URI string for cache keying.
     * Used by fn:uri-collection to cache results regardless of the {@code stable} setting.
     */
    public static String stripStableParameter(final String uriWithQueryString) {
        String result = uriWithQueryString.replaceAll(
                String.format("%s\\s*=\\s*\\byes|no\\b\\s*&+", KEY_STABLE), "");
        if (result.endsWith("?")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    @Nullable
    public String getSelect() {
        return select;
    }

    @Nullable
    public String getMatch() {
        return match;
    }

    @Nullable
    public String getContentType() {
        return contentType;
    }

    public boolean isStable() {
        return stable;
    }

    public boolean hasContentType() {
        return contentType != null;
    }

    /** True if the content-type filter selects (or includes) XML documents. */
    public boolean includesXmlDocuments() {
        return contentType == null
                || VALUE_CONTENT_TYPE_DOCUMENT.equals(contentType)
                || VALUE_CONTENT_TYPE_DOCUMENT_XML.equals(contentType);
    }

    /** True if the content-type filter selects (or includes) binary documents. */
    public boolean includesBinaryDocuments() {
        return contentType == null
                || VALUE_CONTENT_TYPE_DOCUMENT.equals(contentType)
                || VALUE_CONTENT_TYPE_DOCUMENT_BINARY.equals(contentType);
    }

    /** True if the content-type filter selects (or includes) sub-collections. */
    public boolean includesSubcollections() {
        return contentType == null
                || VALUE_CONTENT_TYPE_SUBCOLLECTION.equals(contentType);
    }

    /** Returns the raw key/value map of all query parameters that were present. */
    public Map<String, String> getRawMap() {
        return rawMap;
    }
}
