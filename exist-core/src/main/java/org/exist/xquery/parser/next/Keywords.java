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
package org.exist.xquery.parser.next;

import java.util.HashMap;
import java.util.Map;

/**
 * XQuery keyword constants and lookup utilities.
 *
 * <p>In the hand-written parser, keywords are context-sensitive: the lexer always
 * produces {@link Token#NCNAME} for identifiers, and the parser checks whether
 * a name is a keyword in the current grammatical context.</p>
 *
 * <p>This class provides:
 * <ul>
 *   <li>String constants for all XQuery keywords (core, 4.0, XQUF, XQFT)</li>
 *   <li>A typo-suggestion lookup for common misspellings</li>
 * </ul>
 */
public final class Keywords {

    private Keywords() {
        // utility class
    }

    // ========================================================================
    // XQuery 3.1 core keywords
    // ========================================================================

    public static final String ALLOWING = "allowing";
    public static final String ANCESTOR = "ancestor";
    public static final String ANCESTOR_OR_SELF = "ancestor-or-self";
    public static final String AND = "and";
    public static final String ARRAY = "array";
    public static final String AS = "as";
    public static final String ASCENDING = "ascending";
    public static final String AT = "at";
    public static final String ATTRIBUTE = "attribute";
    public static final String BASE_URI = "base-uri";
    public static final String BOUNDARY_SPACE = "boundary-space";
    public static final String BY = "by";
    public static final String CASE = "case";
    public static final String CAST = "cast";
    public static final String CASTABLE = "castable";
    public static final String CATCH = "catch";
    public static final String CHILD = "child";
    public static final String COLLATION = "collation";
    public static final String COLLECTION = "collection";
    public static final String COMMENT = "comment";
    public static final String CONSTRUCTION = "construction";
    public static final String CONTEXT = "context";
    public static final String COPY_NAMESPACES = "copy-namespaces";
    public static final String COUNT = "count";
    public static final String DECLARE = "declare";
    public static final String DEFAULT = "default";
    public static final String DESCENDANT = "descendant";
    public static final String DESCENDANT_OR_SELF = "descendant-or-self";
    public static final String DESCENDING = "descending";
    public static final String DIV = "div";
    public static final String DOCUMENT = "document";
    public static final String DOCUMENT_NODE = "document-node";
    public static final String ELEMENT = "element";
    public static final String ELSE = "else";
    public static final String EMPTY = "empty";
    public static final String EMPTY_SEQUENCE = "empty-sequence";
    public static final String ENCODING = "encoding";
    public static final String END = "end";
    public static final String EQ = "eq";
    public static final String EVERY = "every";
    public static final String EXCEPT = "except";
    public static final String EXTERNAL = "external";
    public static final String FALSE = "false";
    public static final String FN = "fn";
    public static final String FOLLOWING = "following";
    public static final String FOLLOWING_SIBLING = "following-sibling";
    public static final String FOR = "for";
    public static final String FUNCTION = "function";
    public static final String GE = "ge";
    public static final String GREATEST = "greatest";
    public static final String GROUP = "group";
    public static final String GT = "gt";
    public static final String IDIV = "idiv";
    public static final String IF = "if";
    public static final String IMPORT = "import";
    public static final String IN = "in";
    public static final String INHERIT = "inherit";
    public static final String INSTANCE = "instance";
    public static final String INTERSECT = "intersect";
    public static final String IS = "is";
    public static final String ITEM = "item";
    public static final String LE = "le";
    public static final String LEAST = "least";
    public static final String LET = "let";
    public static final String LT = "lt";
    public static final String MAP = "map";
    public static final String MOD = "mod";
    public static final String MODULE = "module";
    public static final String NAMESPACE = "namespace";
    public static final String NAMESPACE_NODE = "namespace-node";
    public static final String NE = "ne";
    public static final String NO_INHERIT = "no-inherit";
    public static final String NO_PRESERVE = "no-preserve";
    public static final String NODE = "node";
    public static final String OF = "of";
    public static final String ONLY = "only";
    public static final String OPTION = "option";
    public static final String OR = "or";
    public static final String ORDER = "order";
    public static final String ORDERED = "ordered";
    public static final String ORDERING = "ordering";
    public static final String PARENT = "parent";
    public static final String PRECEDING = "preceding";
    public static final String PRECEDING_SIBLING = "preceding-sibling";
    public static final String PRESERVE = "preserve";
    public static final String PROCESSING_INSTRUCTION = "processing-instruction";
    public static final String RETURN = "return";
    public static final String SATISFIES = "satisfies";
    public static final String SCHEMA = "schema";
    public static final String SCHEMA_ATTRIBUTE = "schema-attribute";
    public static final String SCHEMA_ELEMENT = "schema-element";
    public static final String SELF = "self";
    public static final String SOME = "some";
    public static final String STABLE = "stable";
    public static final String START = "start";
    public static final String STRIP = "strip";
    public static final String SWITCH = "switch";
    public static final String TEXT = "text";
    public static final String THEN = "then";
    public static final String TO = "to";
    public static final String TREAT = "treat";
    public static final String TRUE = "true";
    public static final String TRY = "try";
    public static final String TUMBLING = "tumbling";
    public static final String TYPESWITCH = "typeswitch";
    public static final String UNION = "union";
    public static final String UNORDERED = "unordered";
    public static final String VALIDATE = "validate";
    public static final String VALUE = "value";
    public static final String VARIABLE = "variable";
    public static final String VERSION = "version";
    public static final String WHEN = "when";
    public static final String WHERE = "where";
    public static final String WINDOW = "window";
    public static final String WITH = "with";
    public static final String XQUERY = "xquery";

    // ========================================================================
    // XQuery 4.0 keywords
    // ========================================================================

    public static final String ENUM = "enum";
    public static final String FINALLY = "finally";
    public static final String GNODE = "gnode";
    public static final String ISNOT = "isnot";
    public static final String KEY = "key";
    public static final String MEMBER = "member";
    public static final String NEXT = "next";
    public static final String OTHERWISE = "otherwise";
    public static final String PREVIOUS = "previous";
    public static final String RECORD = "record";
    public static final String SLIDING = "sliding";
    public static final String WHILE = "while";

    // ========================================================================
    // XQuery Update Facility (XQUF) keywords
    // ========================================================================

    public static final String AFTER = "after";
    public static final String BEFORE = "before";
    public static final String COPY = "copy";
    public static final String DELETE = "delete";
    public static final String FIRST = "first";
    public static final String INSERT = "insert";
    public static final String INTO = "into";
    public static final String LAST = "last";
    public static final String MODIFY = "modify";
    public static final String NODES = "nodes";
    public static final String RENAME = "rename";
    public static final String REPLACE = "replace";
    public static final String TRANSFORM = "transform";
    public static final String UPDATE = "update";

    // ========================================================================
    // Full-Text keywords
    // ========================================================================

    public static final String ALL = "all";
    public static final String ANY = "any";
    public static final String CONTAINS = "contains";
    public static final String CONTENT = "content";
    public static final String DIACRITICS = "diacritics";
    public static final String DIFFERENT = "different";
    public static final String DISTANCE = "distance";
    public static final String ENTIRE = "entire";
    public static final String EXACTLY = "exactly";
    public static final String FROM = "from";
    public static final String FTAND = "ftand";
    public static final String FTNOT = "ftnot";
    public static final String FTOR = "ftor";
    public static final String INSENSITIVE = "insensitive";
    public static final String LANGUAGE = "language";
    public static final String LEVELS = "levels";
    public static final String LOWERCASE = "lowercase";
    public static final String NOT = "not";
    public static final String OCCURS = "occurs";
    public static final String PARAGRAPH = "paragraph";
    public static final String PHRASE = "phrase";
    public static final String RELATIONSHIP = "relationship";
    public static final String SAME = "same";
    public static final String SCORE = "score";
    public static final String SENSITIVE = "sensitive";
    public static final String SENTENCE = "sentence";
    public static final String STEMMING = "stemming";
    public static final String STOP = "stop";
    public static final String THESAURUS = "thesaurus";
    public static final String TIMES = "times";
    public static final String UPPERCASE = "uppercase";
    public static final String USING = "using";
    public static final String WEIGHT = "weight";
    public static final String WILDCARDS = "wildcards";
    public static final String WORD = "word";
    public static final String WORDS = "words";

    // ========================================================================
    // Typo suggestions (Levenshtein-based)
    // ========================================================================

    /**
     * All keywords that might appear in common positions, for typo detection.
     * Only includes keywords likely to be mistyped in practice.
     */
    private static final String[] COMMON_KEYWORDS = {
            // FLWOR
            FOR, LET, WHERE, ORDER, GROUP, RETURN, COUNT, ALLOWING,
            ASCENDING, DESCENDING, STABLE, SATISFIES, COLLATION,
            // Conditionals
            IF, THEN, ELSE, SWITCH, TYPESWITCH, CASE, DEFAULT,
            // Quantified
            SOME, EVERY,
            // Types
            AS, INSTANCE, OF, TREAT, CAST, CASTABLE, ELEMENT, ATTRIBUTE,
            DOCUMENT, DOCUMENT_NODE, TEXT, COMMENT, NODE, ITEM,
            PROCESSING_INSTRUCTION, SCHEMA_ELEMENT, SCHEMA_ATTRIBUTE,
            NAMESPACE_NODE, FUNCTION, MAP, ARRAY, RECORD, ENUM,
            EMPTY_SEQUENCE,
            // Operators
            AND, OR, DIV, IDIV, MOD, UNION, INTERSECT, EXCEPT,
            TO, EQ, NE, LT, LE, GT, GE, IS, ISNOT, OTHERWISE,
            // Path axes
            CHILD, DESCENDANT, DESCENDANT_OR_SELF, PARENT, ANCESTOR,
            ANCESTOR_OR_SELF, FOLLOWING, FOLLOWING_SIBLING, PRECEDING,
            PRECEDING_SIBLING, SELF,
            // Declarations
            DECLARE, IMPORT, MODULE, NAMESPACE, VARIABLE, FUNCTION,
            OPTION, CONSTRUCTION, ORDERING, COPY_NAMESPACES,
            BASE_URI, BOUNDARY_SPACE, DEFAULT, COLLATION,
            PRESERVE, STRIP, INHERIT, NO_INHERIT, NO_PRESERVE,
            ORDERED, UNORDERED, EXTERNAL, ENCODING, VERSION, XQUERY,
            SCHEMA, CONTEXT, VALUE,
            // Window
            TUMBLING, SLIDING, WINDOW, START, END, ONLY, WHEN,
            PREVIOUS, NEXT, MEMBER, KEY,
            // Try/catch
            TRY, CATCH, FINALLY,
            // Update
            INSERT, DELETE, REPLACE, RENAME, COPY, MODIFY, WITH,
            INTO, AFTER, BEFORE, FIRST, LAST, UPDATE, NODES,
            // Full-text
            CONTAINS, USING, LANGUAGE, WILDCARDS, STEMMING, THESAURUS,
            STOP, WORDS, DISTANCE, OCCURS, TIMES, WEIGHT, SENTENCE,
            PARAGRAPH, CONTENT, DIACRITICS, SENSITIVE, INSENSITIVE,
            LOWERCASE, UPPERCASE, ENTIRE, ANY, ALL, PHRASE, EXACTLY,
            FTAND, FTOR, FTNOT, NOT, FROM, RELATIONSHIP, LEVELS,
            DIFFERENT, SAME, SCORE,
            // Boolean (pseudo-keywords)
            TRUE, FALSE, FN, GNODE, WHILE
    };

    /**
     * Map from keyword to itself, for quick membership check.
     */
    private static final Map<String, Boolean> KEYWORD_SET;

    static {
        KEYWORD_SET = new HashMap<>(COMMON_KEYWORDS.length * 2);
        for (final String kw : COMMON_KEYWORDS) {
            KEYWORD_SET.put(kw, Boolean.TRUE);
        }
    }

    /**
     * Suggests a correction for a mistyped keyword.
     *
     * <p>Uses Levenshtein distance to find the closest keyword within
     * an edit distance of 2. Returns null if no close match is found.</p>
     *
     * @param input the mistyped identifier
     * @return the suggested keyword, or null
     */
    public static String suggestKeyword(final String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        String bestMatch = null;
        int bestDistance = 3; // threshold: max 2 edits

        for (final String kw : COMMON_KEYWORDS) {
            // Quick length check to avoid computing full distance
            if (Math.abs(kw.length() - input.length()) >= bestDistance) {
                continue;
            }
            final int dist = levenshteinDistance(input, kw);
            if (dist < bestDistance) {
                bestDistance = dist;
                bestMatch = kw;
                if (dist == 1) {
                    // Can't do better than 1 edit
                    break;
                }
            }
        }
        return bestMatch;
    }

    /**
     * Returns true if the given string is a known XQuery keyword.
     */
    public static boolean isKnownKeyword(final String name) {
        return KEYWORD_SET.containsKey(name);
    }

    /**
     * Computes the Levenshtein edit distance between two strings.
     */
    static int levenshteinDistance(final String a, final String b) {
        final int lenA = a.length();
        final int lenB = b.length();

        // Single-row optimization
        int[] prev = new int[lenB + 1];
        int[] curr = new int[lenB + 1];

        for (int j = 0; j <= lenB; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            for (int j = 1; j <= lenB; j++) {
                final int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            // Swap rows
            final int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[lenB];
    }
}
