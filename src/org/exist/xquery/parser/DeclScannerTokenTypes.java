// $ANTLR 2.7.7 (2006-11-01): "DeclScanner.g" -> "DeclScanner.java"$

	package org.exist.xquery.parser;
	
	import org.exist.xquery.XPathException;

public interface DeclScannerTokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int QNAME = 4;
	int EQNAME = 5;
	int PREDICATE = 6;
	int FLWOR = 7;
	int PARENTHESIZED = 8;
	int ABSOLUTE_SLASH = 9;
	int ABSOLUTE_DSLASH = 10;
	int WILDCARD = 11;
	int PREFIX_WILDCARD = 12;
	int FUNCTION = 13;
	int DYNAMIC_FCALL = 14;
	int UNARY_MINUS = 15;
	int UNARY_PLUS = 16;
	int XPOINTER = 17;
	int XPOINTER_ID = 18;
	int VARIABLE_REF = 19;
	int VARIABLE_BINDING = 20;
	int ELEMENT = 21;
	int ATTRIBUTE = 22;
	int ATTRIBUTE_CONTENT = 23;
	int TEXT = 24;
	int VERSION_DECL = 25;
	int NAMESPACE_DECL = 26;
	int DEF_NAMESPACE_DECL = 27;
	int DEF_COLLATION_DECL = 28;
	int DEF_FUNCTION_NS_DECL = 29;
	int ANNOT_DECL = 30;
	int GLOBAL_VAR = 31;
	int FUNCTION_DECL = 32;
	int INLINE_FUNCTION_DECL = 33;
	int FUNCTION_INLINE = 34;
	int FUNCTION_TEST = 35;
	int MAP_TEST = 36;
	int LOOKUP = 37;
	int ARRAY = 38;
	int ARRAY_TEST = 39;
	int PROLOG = 40;
	int OPTION = 41;
	int ATOMIC_TYPE = 42;
	int MODULE = 43;
	int ORDER_BY = 44;
	int GROUP_BY = 45;
	int POSITIONAL_VAR = 46;
	int CATCH_ERROR_CODE = 47;
	int CATCH_ERROR_DESC = 48;
	int CATCH_ERROR_VAL = 49;
	int MODULE_DECL = 50;
	int MODULE_IMPORT = 51;
	int SCHEMA_IMPORT = 52;
	int ATTRIBUTE_TEST = 53;
	int COMP_ELEM_CONSTRUCTOR = 54;
	int COMP_ATTR_CONSTRUCTOR = 55;
	int COMP_TEXT_CONSTRUCTOR = 56;
	int COMP_COMMENT_CONSTRUCTOR = 57;
	int COMP_PI_CONSTRUCTOR = 58;
	int COMP_NS_CONSTRUCTOR = 59;
	int COMP_DOC_CONSTRUCTOR = 60;
	int PRAGMA = 61;
	int GTEQ = 62;
	int SEQUENCE = 63;
	int LITERAL_xpointer = 64;
	int LPAREN = 65;
	int RPAREN = 66;
	int NCNAME = 67;
	int LITERAL_xquery = 68;
	int LITERAL_version = 69;
	int SEMICOLON = 70;
	int LITERAL_module = 71;
	int LITERAL_namespace = 72;
	int EQ = 73;
	int STRING_LITERAL = 74;
	int LITERAL_declare = 75;
	int LITERAL_default = 76;
	// "boundary-space" = 77
	int LITERAL_ordering = 78;
	int LITERAL_construction = 79;
	// "base-uri" = 80
	// "copy-namespaces" = 81
	int LITERAL_option = 82;
	int LITERAL_function = 83;
	int LITERAL_variable = 84;
	int MOD = 85;
	int LITERAL_import = 86;
	int LITERAL_encoding = 87;
	int LITERAL_collation = 88;
	int LITERAL_element = 89;
	int LITERAL_order = 90;
	int LITERAL_empty = 91;
	int LITERAL_greatest = 92;
	int LITERAL_least = 93;
	int LITERAL_preserve = 94;
	int LITERAL_strip = 95;
	int LITERAL_ordered = 96;
	int LITERAL_unordered = 97;
	int COMMA = 98;
	// "no-preserve" = 99
	int LITERAL_inherit = 100;
	// "no-inherit" = 101
	int DOLLAR = 102;
	int LCURLY = 103;
	int RCURLY = 104;
	int COLON = 105;
	int LITERAL_external = 106;
	int LITERAL_schema = 107;
	int BRACED_URI_LITERAL = 108;
	int LITERAL_as = 109;
	int LITERAL_at = 110;
	// "empty-sequence" = 111
	int QUESTION = 112;
	int STAR = 113;
	int PLUS = 114;
	int LITERAL_item = 115;
	int LITERAL_map = 116;
	int LITERAL_array = 117;
	int LITERAL_for = 118;
	int LITERAL_let = 119;
	int LITERAL_try = 120;
	int LITERAL_some = 121;
	int LITERAL_every = 122;
	int LITERAL_if = 123;
	int LITERAL_switch = 124;
	int LITERAL_typeswitch = 125;
	int LITERAL_update = 126;
	int LITERAL_replace = 127;
	int LITERAL_value = 128;
	int LITERAL_insert = 129;
	int LITERAL_delete = 130;
	int LITERAL_rename = 131;
	int LITERAL_with = 132;
	int LITERAL_into = 133;
	int LITERAL_preceding = 134;
	int LITERAL_following = 135;
	int LITERAL_catch = 136;
	int UNION = 137;
	int LITERAL_where = 138;
	int LITERAL_return = 139;
	int LITERAL_in = 140;
	int LITERAL_by = 141;
	int LITERAL_stable = 142;
	int LITERAL_ascending = 143;
	int LITERAL_descending = 144;
	int LITERAL_group = 145;
	int LITERAL_satisfies = 146;
	int LITERAL_case = 147;
	int LITERAL_then = 148;
	int LITERAL_else = 149;
	int LITERAL_or = 150;
	int LITERAL_and = 151;
	int LITERAL_instance = 152;
	int LITERAL_of = 153;
	int LITERAL_treat = 154;
	int LITERAL_castable = 155;
	int LITERAL_cast = 156;
	int BEFORE = 157;
	int AFTER = 158;
	int LITERAL_eq = 159;
	int LITERAL_ne = 160;
	int LITERAL_lt = 161;
	int LITERAL_le = 162;
	int LITERAL_gt = 163;
	int LITERAL_ge = 164;
	int GT = 165;
	int NEQ = 166;
	int LT = 167;
	int LTEQ = 168;
	int LITERAL_is = 169;
	int LITERAL_isnot = 170;
	int CONCAT = 171;
	int LITERAL_to = 172;
	int MINUS = 173;
	int LITERAL_div = 174;
	int LITERAL_idiv = 175;
	int LITERAL_mod = 176;
	int BANG = 177;
	int PRAGMA_START = 178;
	int PRAGMA_END = 179;
	int LITERAL_union = 180;
	int LITERAL_intersect = 181;
	int LITERAL_except = 182;
	int SLASH = 183;
	int DSLASH = 184;
	int LITERAL_text = 185;
	int LITERAL_node = 186;
	int LITERAL_attribute = 187;
	int LITERAL_comment = 188;
	// "processing-instruction" = 189
	// "document-node" = 190
	int LITERAL_document = 191;
	int HASH = 192;
	int SELF = 193;
	int XML_COMMENT = 194;
	int XML_PI = 195;
	int LPPAREN = 196;
	int RPPAREN = 197;
	int AT = 198;
	int PARENT = 199;
	int LITERAL_child = 200;
	int LITERAL_self = 201;
	int LITERAL_descendant = 202;
	// "descendant-or-self" = 203
	// "following-sibling" = 204
	int LITERAL_parent = 205;
	int LITERAL_ancestor = 206;
	// "ancestor-or-self" = 207
	// "preceding-sibling" = 208
	int INTEGER_LITERAL = 209;
	int DOUBLE_LITERAL = 210;
	int DECIMAL_LITERAL = 211;
	// "schema-element" = 212
	int END_TAG_START = 213;
	int QUOT = 214;
	int APOS = 215;
	int QUOT_ATTRIBUTE_CONTENT = 216;
	int ESCAPE_QUOT = 217;
	int APOS_ATTRIBUTE_CONTENT = 218;
	int ESCAPE_APOS = 219;
	int ELEMENT_CONTENT = 220;
	int XML_COMMENT_END = 221;
	int XML_PI_END = 222;
	int XML_CDATA = 223;
	int LITERAL_collection = 224;
	int LITERAL_validate = 225;
	int XML_PI_START = 226;
	int XML_CDATA_START = 227;
	int XML_CDATA_END = 228;
	int LETTER = 229;
	int DIGITS = 230;
	int HEX_DIGITS = 231;
	int NMSTART = 232;
	int NMCHAR = 233;
	int WS = 234;
	int XQDOC_COMMENT = 235;
	int EXPR_COMMENT = 236;
	int PREDEFINED_ENTITY_REF = 237;
	int CHAR_REF = 238;
	int S = 239;
	int NEXT_TOKEN = 240;
	int CHAR = 241;
	int BASECHAR = 242;
	int IDEOGRAPHIC = 243;
	int COMBINING_CHAR = 244;
	int DIGIT = 245;
	int EXTENDER = 246;
}
