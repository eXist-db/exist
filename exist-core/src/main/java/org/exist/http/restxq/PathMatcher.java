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
package org.exist.http.restxq;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches HTTP request paths against RESTXQ path templates and extracts
 * template variable values.
 *
 * <p>Supports three kinds of path segments:</p>
 * <ul>
 *   <li>Literal segments: {@code /users/list}</li>
 *   <li>Template variables: {@code /users/{$id}}</li>
 *   <li>Regex-constrained variables: {@code /users/{$id=[0-9]+}}</li>
 * </ul>
 *
 * <p>Implements {@link Comparable} for specificity-based route precedence:
 * more segments wins; at equal segment count, literal segments beat
 * template variables.</p>
 *
 * <p>The path matching approach follows the BaseX model for cross-engine
 * RESTXQ compatibility.</p>
 */
public class PathMatcher implements Comparable<PathMatcher> {

    private final String template;
    private final Pattern pattern;
    private final List<String> varNames;
    private final int segmentCount;
    private final BigInteger templatePositions;

    private PathMatcher(final String template, final Pattern pattern,
                        final List<String> varNames, final int segmentCount,
                        final BigInteger templatePositions) {
        this.template = template;
        this.pattern = pattern;
        this.varNames = varNames;
        this.segmentCount = segmentCount;
        this.templatePositions = templatePositions;
    }

    /**
     * Parses a RESTXQ path template into a PathMatcher.
     *
     * @param pathTemplate the path template string, e.g. "/users/{$id=[0-9]+}/posts"
     * @return a compiled PathMatcher
     * @throws IllegalArgumentException if the template is malformed
     */
    public static PathMatcher parse(final String pathTemplate) {
        if (pathTemplate == null) {
            throw new IllegalArgumentException("Path template must not be null");
        }

        // Empty path or "/" both match the root
        if (pathTemplate.isEmpty() || "/".equals(pathTemplate)) {
            final Pattern rootPattern = Pattern.compile("^/?$");
            return new PathMatcher(pathTemplate, rootPattern,
                    Collections.emptyList(), 0, BigInteger.ZERO);
        }

        final List<String> varNames = new ArrayList<>();
        final StringBuilder regex = new StringBuilder();
        int segmentCount = 0;
        BigInteger templatePositions = BigInteger.ZERO;

        // Ensure leading slash
        final String path = pathTemplate.startsWith("/") ? pathTemplate : "/" + pathTemplate;

        int i = 0;
        while (i < path.length()) {
            final char c = path.charAt(i);

            if (c == '{') {
                // Template variable
                final int close = path.indexOf('}', i);
                if (close == -1) {
                    throw new IllegalArgumentException(
                            "Unclosed template variable at position " + i + " in: " + pathTemplate);
                }

                final String rawVarSpec = path.substring(i + 1, close);

                // Must start with $ (RESTXQ variable syntax)
                if (!rawVarSpec.startsWith("$")) {
                    throw new IllegalArgumentException(
                            "Template variable must start with $ in: " + pathTemplate);
                }

                String varSpec = rawVarSpec.substring(1);

                // Validate no spaces in variable spec
                if (varSpec.contains(" ")) {
                    throw new IllegalArgumentException(
                            "Template variable must not contain spaces in: " + pathTemplate);
                }

                String varName;
                String varRegex;

                final int eqIdx = varSpec.indexOf('=');
                if (eqIdx >= 0) {
                    // Regex-constrained variable: {$id=[0-9]+}
                    varName = varSpec.substring(0, eqIdx);
                    varRegex = varSpec.substring(eqIdx + 1);
                } else {
                    varName = varSpec;
                    varRegex = "[^/]+?";
                }

                // Validate variable name (XQuery QName rules: no colons within local part,
                // but allow namespace prefix like m:x)
                if (varName.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Empty variable name in template: " + pathTemplate);
                }
                if (varName.contains("::") || varName.contains(" ")) {
                    throw new IllegalArgumentException(
                            "Invalid variable name '" + varName + "' in template: " + pathTemplate);
                }

                // Check for duplicate variable names
                if (varNames.contains(varName)) {
                    throw new IllegalArgumentException(
                            "Duplicate variable {$" + varName + "} in template: " + pathTemplate);
                }

                varNames.add(varName);
                regex.append('(').append(varRegex).append(')');

                // Mark this segment as a template
                templatePositions = templatePositions.setBit(segmentCount > 0 ? segmentCount - 1 : 0);

                i = close + 1;
            } else if (c == '/') {
                regex.append('/');
                segmentCount++;
                i++;
            } else {
                // Literal segment — accumulate, decode, and escape for regex
                int j = i;
                while (j < path.length() && path.charAt(j) != '/' && path.charAt(j) != '{') {
                    j++;
                }
                final String literal = path.substring(i, j);
                // URL-decode the literal segment (e.g., %7b → {, %20 → space, + → space)
                final String decoded = decodePath(literal);
                regex.append(Pattern.quote(decoded));
                i = j;
            }
        }

        final Pattern compiled = Pattern.compile("^" + regex + "$");
        return new PathMatcher(pathTemplate, compiled, varNames, segmentCount, templatePositions);
    }

    /**
     * Tests whether the given request path matches this template.
     *
     * @param requestPath the request path (must start with "/")
     * @return true if the path matches
     */
    public boolean matches(final String requestPath) {
        return pattern.matcher(requestPath).matches();
    }

    /**
     * Extracts template variable values from a matching request path.
     *
     * @param requestPath the request path
     * @return map of variable name to captured value, or empty map if no match
     */
    public Map<String, String> extractVariables(final String requestPath) {
        final Matcher m = pattern.matcher(requestPath);
        if (!m.matches()) {
            return Collections.emptyMap();
        }

        final Map<String, String> result = new LinkedHashMap<>();
        for (int g = 0; g < varNames.size(); g++) {
            result.put(varNames.get(g), m.group(g + 1));
        }
        return result;
    }

    /**
     * Returns the original path template string.
     */
    public String getTemplate() {
        return template;
    }

    /**
     * Returns the variable names declared in the template, in order.
     */
    public List<String> getVarNames() {
        return Collections.unmodifiableList(varNames);
    }

    /**
     * Returns the number of path segments (separated by '/').
     */
    public int getSegmentCount() {
        return segmentCount;
    }

    /**
     * Specificity comparison for route precedence.
     *
     * <p>More segments = more specific. At equal segment count,
     * literal segments beat template variables (checked left-to-right).</p>
     */
    @Override
    public int compareTo(final PathMatcher other) {
        // More segments = more specific (sort first)
        final int segDiff = other.segmentCount - this.segmentCount;
        if (segDiff != 0) {
            return segDiff;
        }

        // Same segment count: compare segment-by-segment
        // A template position (bit set) is LESS specific than a literal (bit unset)
        for (int s = 0; s < this.segmentCount; s++) {
            final boolean thisIsTemplate = this.templatePositions.testBit(s);
            final boolean otherIsTemplate = other.templatePositions.testBit(s);
            if (thisIsTemplate != otherIsTemplate) {
                // literal (not template) is more specific → sort first
                return thisIsTemplate ? 1 : -1;
            }
        }

        return 0;
    }

    @Override
    public String toString() {
        return template;
    }

    /**
     * URL-decodes a path string, converting percent-encoded characters and
     * {@code +} to space. Throws IllegalArgumentException for invalid encodings.
     */
    static String decodePath(final String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        // Check for invalid percent encoding and decode only percent sequences
        // (NOT +, which is literal in URI paths — only means space in query strings)
        final StringBuilder result = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i);
            if (c == '%') {
                if (i + 2 >= path.length()) {
                    throw new IllegalArgumentException("Invalid percent encoding in path: " + path);
                }
                final char c1 = path.charAt(i + 1);
                final char c2 = path.charAt(i + 2);
                if (!isHexDigit(c1) || !isHexDigit(c2)) {
                    throw new IllegalArgumentException("Invalid percent encoding in path: " + path);
                }
                result.append((char) Integer.parseInt(path.substring(i + 1, i + 3), 16));
                i += 2;
            } else if (c == '+') {
                // In RESTXQ path templates, + means space (following URL convention)
                result.append(' ');
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static boolean isHexDigit(final char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
