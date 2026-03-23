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

/**
 * Token produced by the hand-written XQuery lexer.
 *
 * <p>Each token carries its type, source text, and position in the input.
 * Position tracking uses 1-based line and column numbers.</p>
 */
public final class Token {

    // ---- Sentinel ----
    public static final int EOF = 0;

    // ---- Punctuation & Operators ----
    public static final int LPAREN = 1;               // (
    public static final int RPAREN = 2;               // )
    public static final int LBRACKET = 3;             // [
    public static final int RBRACKET = 4;             // ]
    public static final int LBRACE = 5;               // {
    public static final int RBRACE = 6;               // }
    public static final int COMMA = 7;                // ,
    public static final int SEMICOLON = 8;            // ;
    public static final int COLON = 9;                // :
    public static final int DOT = 10;                 // .
    public static final int DOT_DOT = 11;             // ..
    public static final int SLASH = 12;               // /
    public static final int DSLASH = 13;              // //
    public static final int AT = 14;                  // @
    public static final int DOLLAR = 15;              // $
    public static final int HASH = 16;                // #
    public static final int QUESTION = 17;            // ?
    public static final int DOUBLE_QUESTION = 18;     // ??
    public static final int STAR = 19;                // *
    public static final int PLUS = 20;                // +
    public static final int MINUS = 21;               // -
    public static final int EQ = 22;                  // =
    public static final int NEQ = 23;                 // !=
    public static final int LT = 24;                  // <
    public static final int LTEQ = 25;                // <=
    public static final int GT = 26;                  // >
    public static final int GTEQ = 27;                // >=
    public static final int BANG = 28;                // !
    public static final int DOUBLE_BANG = 29;         // !!
    public static final int PIPE = 30;                // |
    public static final int CONCAT = 31;              // ||
    public static final int ARROW = 32;               // =>
    public static final int MAPPING_ARROW = 33;       // =!>
    public static final int METHOD_CALL = 34;         // =?>
    public static final int PIPELINE = 35;            // ->
    public static final int COLONCOLON = 36;          // ::
    public static final int PERCENT = 37;             // %
    public static final int COLON_EQ = 38;            // :=

    // ---- Literals ----
    public static final int INTEGER_LITERAL = 40;
    public static final int DECIMAL_LITERAL = 41;
    public static final int DOUBLE_LITERAL = 42;
    public static final int STRING_LITERAL = 43;
    public static final int HEX_INTEGER_LITERAL = 44;
    public static final int BINARY_INTEGER_LITERAL = 45;
    public static final int BRACED_URI_LITERAL = 46;  // Q{...}

    // ---- Names ----
    public static final int NCNAME = 50;
    public static final int QNAME = 51;               // prefix:local

    // ---- String templates ----
    public static final int STRING_TEMPLATE_START = 55;   // ` (opening backtick)
    public static final int STRING_TEMPLATE_END = 56;     // ` (closing backtick)
    public static final int STRING_TEMPLATE_CONTENT = 57; // text between { }
    public static final int STRING_CONSTRUCTOR_START = 58; // ``[
    public static final int STRING_CONSTRUCTOR_END = 59;   // ]``
    public static final int STRING_CONSTRUCTOR_CONTENT = 60;
    public static final int STRING_CONSTRUCTOR_INTERPOLATION_START = 61; // `{
    public static final int STRING_CONSTRUCTOR_INTERPOLATION_END = 62;   // }`

    // ---- Comments ----
    public static final int XQDOC_COMMENT = 65;       // (:~ ... :)
    public static final int PRAGMA_START = 66;         // (# ... #)
    public static final int PRAGMA_END = 67;

    // ---- XML content tokens ----
    public static final int XML_COMMENT = 70;          // <!-- ... -->
    public static final int XML_PI = 71;               // <? ... ?>
    public static final int XML_CDATA = 72;            // <![CDATA[ ... ]]>
    public static final int END_TAG_START = 73;         // </
    public static final int EMPTY_TAG_CLOSE = 74;       // />
    public static final int QUOT = 75;                  // " (in XML context)
    public static final int APOS = 76;                  // ' (in XML context)

    // ---- Keywords (100+) ----
    // Alphabetically ordered for easy lookup.
    // The lexer returns NCNAME for all identifiers; keyword detection
    // happens in the parser via context-sensitive checks.
    // These constants are used by the parser, not the lexer.

    public static final int KW_ALLOWING = 100;
    public static final int KW_ANCESTOR = 101;
    public static final int KW_ANCESTOR_OR_SELF = 102;
    public static final int KW_AND = 103;
    public static final int KW_ARRAY = 104;
    public static final int KW_AS = 105;
    public static final int KW_ASCENDING = 106;
    public static final int KW_AT = 107;
    public static final int KW_ATTRIBUTE = 108;
    public static final int KW_BASE_URI = 109;
    public static final int KW_BOUNDARY_SPACE = 110;
    public static final int KW_BY = 111;
    public static final int KW_CASE = 112;
    public static final int KW_CAST = 113;
    public static final int KW_CASTABLE = 114;
    public static final int KW_CATCH = 115;
    public static final int KW_CHILD = 116;
    public static final int KW_COLLATION = 117;
    public static final int KW_COLLECTION = 118;
    public static final int KW_COMMENT = 119;
    public static final int KW_CONSTRUCTION = 120;
    public static final int KW_CONTEXT = 121;
    public static final int KW_COPY_NAMESPACES = 122;
    public static final int KW_COUNT = 123;
    public static final int KW_DECLARE = 124;
    public static final int KW_DEFAULT = 125;
    public static final int KW_DELETE = 126;
    public static final int KW_DESCENDANT = 127;
    public static final int KW_DESCENDANT_OR_SELF = 128;
    public static final int KW_DESCENDING = 129;
    public static final int KW_DIV = 130;
    public static final int KW_DOCUMENT = 131;
    public static final int KW_DOCUMENT_NODE = 132;
    public static final int KW_ELEMENT = 133;
    public static final int KW_ELSE = 134;
    public static final int KW_EMPTY = 135;
    public static final int KW_EMPTY_SEQUENCE = 136;
    public static final int KW_ENCODING = 137;
    public static final int KW_END = 138;
    public static final int KW_ENUM = 139;
    public static final int KW_EQ = 140;
    public static final int KW_EVERY = 141;
    public static final int KW_EXCEPT = 142;
    public static final int KW_EXTERNAL = 143;
    public static final int KW_FALSE = 144;
    public static final int KW_FINALLY = 145;
    public static final int KW_FN = 146;
    public static final int KW_FOLLOWING = 147;
    public static final int KW_FOLLOWING_SIBLING = 148;
    public static final int KW_FOR = 149;
    public static final int KW_FUNCTION = 150;
    public static final int KW_GE = 151;
    public static final int KW_GREATEST = 152;
    public static final int KW_GROUP = 153;
    public static final int KW_GT = 154;
    public static final int KW_IDIV = 155;
    public static final int KW_IF = 156;
    public static final int KW_IMPORT = 157;
    public static final int KW_IN = 158;
    public static final int KW_INHERIT = 159;
    public static final int KW_INSERT = 160;
    public static final int KW_INSTANCE = 161;
    public static final int KW_INTERSECT = 162;
    public static final int KW_INTO = 163;
    public static final int KW_IS = 164;
    public static final int KW_ITEM = 165;
    public static final int KW_KEY = 166;
    public static final int KW_LE = 167;
    public static final int KW_LEAST = 168;
    public static final int KW_LET = 169;
    public static final int KW_LT = 170;
    public static final int KW_MAP = 171;
    public static final int KW_MEMBER = 172;
    public static final int KW_MOD = 173;
    public static final int KW_MODULE = 174;
    public static final int KW_NAMESPACE = 175;
    public static final int KW_NAMESPACE_NODE = 176;
    public static final int KW_NE = 177;
    public static final int KW_NEXT = 178;
    public static final int KW_NO_INHERIT = 179;
    public static final int KW_NO_PRESERVE = 180;
    public static final int KW_NODE = 181;
    public static final int KW_OF = 182;
    public static final int KW_ONLY = 183;
    public static final int KW_OPTION = 184;
    public static final int KW_OR = 185;
    public static final int KW_ORDER = 186;
    public static final int KW_ORDERED = 187;
    public static final int KW_ORDERING = 188;
    public static final int KW_OTHERWISE = 189;
    public static final int KW_PARENT = 190;
    public static final int KW_PRECEDING = 191;
    public static final int KW_PRECEDING_SIBLING = 192;
    public static final int KW_PRESERVE = 193;
    public static final int KW_PREVIOUS = 194;
    public static final int KW_PROCESSING_INSTRUCTION = 195;
    public static final int KW_RECORD = 196;
    public static final int KW_RENAME = 197;
    public static final int KW_REPLACE = 198;
    public static final int KW_RETURN = 199;
    public static final int KW_SATISFIES = 200;
    public static final int KW_SCHEMA = 201;
    public static final int KW_SCHEMA_ATTRIBUTE = 202;
    public static final int KW_SCHEMA_ELEMENT = 203;
    public static final int KW_SELF = 204;
    public static final int KW_SLIDING = 205;
    public static final int KW_SOME = 206;
    public static final int KW_STABLE = 207;
    public static final int KW_START = 208;
    public static final int KW_STRIP = 209;
    public static final int KW_SWITCH = 210;
    public static final int KW_TEXT = 211;
    public static final int KW_THEN = 212;
    public static final int KW_TO = 213;
    public static final int KW_TREAT = 214;
    public static final int KW_TRUE = 215;
    public static final int KW_TRY = 216;
    public static final int KW_TUMBLING = 217;
    public static final int KW_TYPESWITCH = 218;
    public static final int KW_UNION = 219;
    public static final int KW_UNORDERED = 220;
    public static final int KW_UPDATE = 221;
    public static final int KW_VALIDATE = 222;
    public static final int KW_VALUE = 223;
    public static final int KW_VARIABLE = 224;
    public static final int KW_VERSION = 225;
    public static final int KW_WHEN = 226;
    public static final int KW_WHERE = 227;
    public static final int KW_WHILE = 228;
    public static final int KW_WINDOW = 229;
    public static final int KW_WITH = 230;
    public static final int KW_XQUERY = 231;

    // ---- XQUF keywords ----
    public static final int KW_COPY = 240;
    public static final int KW_MODIFY = 241;
    public static final int KW_TRANSFORM = 242;
    public static final int KW_FIRST = 243;
    public static final int KW_LAST = 244;
    public static final int KW_BEFORE = 245;
    public static final int KW_AFTER = 246;
    public static final int KW_NODES = 247;
    public static final int KW_NODE_KW = 248;  // "node" as keyword in update context

    // ---- Full-text keywords ----
    public static final int KW_CONTAINS = 260;
    public static final int KW_FTAND = 261;
    public static final int KW_FTOR = 262;
    public static final int KW_FTNOT = 263;
    public static final int KW_NOT = 264;
    public static final int KW_USING = 265;
    public static final int KW_LANGUAGE = 266;
    public static final int KW_WILDCARDS = 267;
    public static final int KW_STEMMING = 268;
    public static final int KW_THESAURUS = 269;
    public static final int KW_STOP = 270;
    public static final int KW_WORDS = 271;
    public static final int KW_DISTANCE = 272;
    public static final int KW_OCCURS = 273;
    public static final int KW_TIMES = 274;
    public static final int KW_WEIGHT = 275;
    public static final int KW_WINDOW_FT = 276;
    public static final int KW_SENTENCE = 277;
    public static final int KW_PARAGRAPH = 278;
    public static final int KW_CONTENT = 279;
    public static final int KW_DIACRITICS = 280;
    public static final int KW_SENSITIVE = 281;
    public static final int KW_INSENSITIVE = 282;
    public static final int KW_LOWERCASE = 283;
    public static final int KW_UPPERCASE = 284;
    public static final int KW_ENTIRE = 285;
    public static final int KW_ANY = 286;
    public static final int KW_ALL = 287;
    public static final int KW_PHRASE = 288;
    public static final int KW_EXACTLY = 289;
    public static final int KW_FROM = 290;
    public static final int KW_RELATIONSHIP = 291;
    public static final int KW_LEVELS = 292;
    public static final int KW_DIFFERENT = 293;
    public static final int KW_SAME = 294;
    public static final int KW_SCORE = 295;

    // ---- XQuery 4.0 additional ----
    public static final int KW_GNODE = 300;
    public static final int KW_ISNOT = 301;

    /** Human-readable names for token types, indexed by type constant. */
    private static final String[] TYPE_NAMES;

    static {
        TYPE_NAMES = new String[310];
        TYPE_NAMES[EOF] = "EOF";
        TYPE_NAMES[LPAREN] = "'('";
        TYPE_NAMES[RPAREN] = "')'";
        TYPE_NAMES[LBRACKET] = "'['";
        TYPE_NAMES[RBRACKET] = "']'";
        TYPE_NAMES[LBRACE] = "'{'";
        TYPE_NAMES[RBRACE] = "'}'";
        TYPE_NAMES[COMMA] = "','";
        TYPE_NAMES[SEMICOLON] = "';'";
        TYPE_NAMES[COLON] = "':'";
        TYPE_NAMES[DOT] = "'.'";
        TYPE_NAMES[DOT_DOT] = "'..'";
        TYPE_NAMES[SLASH] = "'/'";
        TYPE_NAMES[DSLASH] = "'//'";
        TYPE_NAMES[AT] = "'@'";
        TYPE_NAMES[DOLLAR] = "'$'";
        TYPE_NAMES[HASH] = "'#'";
        TYPE_NAMES[QUESTION] = "'?'";
        TYPE_NAMES[DOUBLE_QUESTION] = "'??'";
        TYPE_NAMES[STAR] = "'*'";
        TYPE_NAMES[PLUS] = "'+'";
        TYPE_NAMES[MINUS] = "'-'";
        TYPE_NAMES[EQ] = "'='";
        TYPE_NAMES[NEQ] = "'!='";
        TYPE_NAMES[LT] = "'<'";
        TYPE_NAMES[LTEQ] = "'<='";
        TYPE_NAMES[GT] = "'>'";
        TYPE_NAMES[GTEQ] = "'>='";
        TYPE_NAMES[BANG] = "'!'";
        TYPE_NAMES[DOUBLE_BANG] = "'!!'";
        TYPE_NAMES[PIPE] = "'|'";
        TYPE_NAMES[CONCAT] = "'||'";
        TYPE_NAMES[ARROW] = "'=>'";
        TYPE_NAMES[MAPPING_ARROW] = "'=!>'";
        TYPE_NAMES[METHOD_CALL] = "'=?>'";
        TYPE_NAMES[PIPELINE] = "'->'";
        TYPE_NAMES[COLONCOLON] = "'::'";
        TYPE_NAMES[INTEGER_LITERAL] = "integer literal";
        TYPE_NAMES[DECIMAL_LITERAL] = "decimal literal";
        TYPE_NAMES[DOUBLE_LITERAL] = "double literal";
        TYPE_NAMES[STRING_LITERAL] = "string literal";
        TYPE_NAMES[HEX_INTEGER_LITERAL] = "hex integer literal";
        TYPE_NAMES[BINARY_INTEGER_LITERAL] = "binary integer literal";
        TYPE_NAMES[BRACED_URI_LITERAL] = "braced URI literal";
        TYPE_NAMES[NCNAME] = "NCName";
        TYPE_NAMES[QNAME] = "QName";
    }

    /**
     * Returns a human-readable name for the given token type.
     *
     * @param type the token type constant
     * @return a display name, or the numeric value if unknown
     */
    public static String typeName(final int type) {
        if (type >= 0 && type < TYPE_NAMES.length && TYPE_NAMES[type] != null) {
            return TYPE_NAMES[type];
        }
        // For keyword tokens, derive name from constant
        if (type >= 100 && type < 310) {
            return "keyword";
        }
        return "token(" + type + ")";
    }

    // ---- Instance fields ----

    /** Token type (one of the constants defined in this class). */
    public final int type;

    /** Source text of the token. */
    public final String value;

    /** 1-based line number where this token starts. */
    public final int line;

    /** 1-based column number where this token starts. */
    public final int column;

    /** Absolute offset in the input codepoint array where this token ENDS. */
    public final int endOffset;

    /**
     * Creates a new token.
     *
     * @param type   token type constant
     * @param value  source text
     * @param line   1-based line number
     * @param column 1-based column number
     */
    public Token(final int type, final String value, final int line, final int column) {
        this(type, value, line, column, -1);
    }

    public Token(final int type, final String value, final int line, final int column, final int endOffset) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
        this.endOffset = endOffset;
    }

    /**
     * Returns true if this is an end-of-file token.
     */
    public boolean isEOF() {
        return type == EOF;
    }

    @Override
    public String toString() {
        if (type == EOF) {
            return "EOF";
        }
        return typeName(type) + " '" + value + "' at " + line + ":" + column;
    }
}
