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
package org.exist.http;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parsing and proactive content negotiation for the HTTP {@code Accept} header
 * (RFC 7231 §5.3.2). Pure and request-independent so the same logic can be
 * reused by the REST server, the {@code request} XQuery module, and any other
 * caller. Quality values ({@code q=}) and the {@code *}/{@code *} and
 * {@code type/*} wildcards are honored.
 */
public final class AcceptHeader {

    private AcceptHeader() {
    }

    /**
     * A single media range from an {@code Accept} header, e.g. {@code text/html;q=0.8}.
     *
     * @param type the primary type ("text"), or "*" for a wildcard
     * @param subtype the subtype ("html"), or "*" for a wildcard
     * @param quality the quality value (q), from 0.0 to 1.0
     * @param parameters media-range parameters other than q, in declaration order
     */
    public record MediaRange(String type, String subtype, double quality, Map<String, String> parameters) {

        /**
         * @return the "type/subtype" media type as a string.
         */
        public String mediaType() {
            return type + "/" + subtype;
        }

        private int specificity() {
            if ("*".equals(type)) {
                return 0;
            }
            return "*".equals(subtype) ? 1 : 2;
        }

        private boolean matches(final String otherType, final String otherSubtype) {
            final boolean typeMatches = "*".equals(type) || type.equalsIgnoreCase(otherType);
            final boolean subtypeMatches = "*".equals(subtype) || subtype.equalsIgnoreCase(otherSubtype);
            return typeMatches && subtypeMatches;
        }
    }

    /**
     * Parse an {@code Accept} header into its media ranges, ordered by descending
     * quality and then descending specificity. Malformed entries are skipped.
     * Entries with {@code q=0} are retained, as they signify explicit rejection.
     *
     * @param header the raw {@code Accept} header value (may be null or empty)
     * @return the parsed media ranges, highest preference first
     */
    public static List<MediaRange> parse(@Nullable final String header) {
        final List<MediaRange> ranges = new ArrayList<>();
        if (header == null || header.isBlank()) {
            return ranges;
        }
        for (final String element : header.split(",")) {
            final MediaRange range = parseRange(element.trim());
            if (range != null) {
                ranges.add(range);
            }
        }
        ranges.sort((a, b) -> {
            final int byQuality = Double.compare(b.quality(), a.quality());
            return byQuality != 0 ? byQuality : Integer.compare(b.specificity(), a.specificity());
        });
        return ranges;
    }

    @Nullable
    private static MediaRange parseRange(final String element) {
        if (element.isEmpty()) {
            return null;
        }
        final String[] parts = element.split(";");
        final String mediaType = parts[0].trim();
        final int slash = mediaType.indexOf('/');
        if (slash < 1 || slash == mediaType.length() - 1) {
            return null;  // malformed: missing type or subtype
        }
        final String type = mediaType.substring(0, slash).trim();
        final String subtype = mediaType.substring(slash + 1).trim();
        double quality = 1.0;
        final Map<String, String> parameters = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            final String param = parts[i].trim();
            final int eq = param.indexOf('=');
            if (eq < 1) {
                continue;
            }
            final String name = param.substring(0, eq).trim();
            final String value = unquote(param.substring(eq + 1).trim());
            if ("q".equalsIgnoreCase(name)) {
                quality = parseQuality(value);
            } else {
                parameters.put(name, value);
            }
        }
        return new MediaRange(type, subtype, quality, parameters);
    }

    private static String unquote(final String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static double parseQuality(final String value) {
        try {
            final double quality = Double.parseDouble(value);
            if (quality < 0.0) {
                return 0.0;
            }
            return Math.min(quality, 1.0);
        } catch (final NumberFormatException e) {
            return 1.0;
        }
    }

    /**
     * Negotiate the best media type to return for a request.
     *
     * Given the media types the server can produce, return the one most preferred
     * by the client's {@code Accept} header (RFC 7231 §5.3.2), honoring quality
     * values and wildcards. A missing or empty {@code Accept} header (or one that
     * accepts everything) means "no preference", so the first offer is returned.
     * Returns empty if no offer is acceptable (the caller should then respond with
     * 406 Not Acceptable). When several offers tie on quality, the one matching a
     * more specific range wins; remaining ties are broken by the order of $available.
     *
     * @param header the raw {@code Accept} header value (may be null or empty)
     * @param available the media types the server can produce, in preference order
     * @return the best matching media type, or empty if none is acceptable
     */
    public static Optional<String> negotiate(@Nullable final String header, final List<String> available) {
        if (available.isEmpty()) {
            return Optional.empty();
        }
        final List<MediaRange> ranges = parse(header);
        if (ranges.isEmpty()) {
            return Optional.of(available.getFirst());  // no (parseable) preference -> first offer
        }

        String best = null;
        double bestQuality = 0.0;
        int bestSpecificity = -1;
        for (final String offer : available) {
            final MediaRange matched = mostSpecificMatch(ranges, offer);
            if (matched == null || matched.quality() <= 0.0) {
                continue;  // not acceptable
            }
            if (matched.quality() > bestQuality
                    || (matched.quality() == bestQuality && matched.specificity() > bestSpecificity)) {
                best = offer;
                bestQuality = matched.quality();
                bestSpecificity = matched.specificity();
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Find the most specific media range that matches the given offer.
     *
     * @param ranges the parsed Accept media ranges
     * @param offer a "type/subtype" media type the server can produce
     * @return the most specific matching range, or null if none match
     */
    @Nullable
    private static MediaRange mostSpecificMatch(final List<MediaRange> ranges, final String offer) {
        final int slash = offer.indexOf('/');
        final String offerType = slash < 0 ? offer : offer.substring(0, slash);
        final String offerSubtype = slash < 0 ? "*" : offer.substring(slash + 1);

        MediaRange matched = null;
        for (final MediaRange range : ranges) {
            if (range.matches(offerType, offerSubtype)
                    && (matched == null || range.specificity() > matched.specificity())) {
                matched = range;
            }
        }
        return matched;
    }
}
