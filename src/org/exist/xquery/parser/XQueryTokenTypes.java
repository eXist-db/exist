// $ANTLR 2.7.7 (2006-11-01): "XQuery.g" -> "XQueryParser.java"$

	package org.exist.xquery.parser;

	import antlr.debug.misc.*;
	import java.io.StringReader;
	import java.io.BufferedReader;
	import java.io.InputStreamReader;
	import java.util.ArrayList;
	import java.util.List;
	import java.util.Iterator;
	import java.util.Stack;
	import org.exist.storage.BrokerPool;
	import org.exist.storage.DBBroker;
	import org.exist.storage.analysis.Tokenizer;
	import org.exist.EXistException;
	import org.exist.dom.persistent.DocumentSet;
	import org.exist.dom.persistent.DocumentImpl;
	import org.exist.dom.QName;
	import org.exist.security.PermissionDeniedException;
	import org.exist.xquery.*;
	import org.exist.xquery.value.*;
	import org.exist.xquery.functions.fn.*;

public interface XQueryTokenTypes {
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
	int FUNCTION_INLINE = 33;
	int FUNCTION_TEST = 34;
	int MAP_TEST = 35;
	int LOOKUP = 36;
	int ARRAY = 37;
	int ARRAY_TEST = 38;
	int PROLOG = 39;
	int OPTION = 40;
	int ATOMIC_TYPE = 41;
	int MODULE = 42;
	int ORDER_BY = 43;
	int GROUP_BY = 44;
	int POSITIONAL_VAR = 45;
	int CATCH_ERROR_CODE = 46;
	int CATCH_ERROR_DESC = 47;
	int CATCH_ERROR_VAL = 48;
	int MODULE_DECL = 49;
	int MODULE_IMPORT = 50;
	int SCHEMA_IMPORT = 51;
	int ATTRIBUTE_TEST = 52;
	int COMP_ELEM_CONSTRUCTOR = 53;
	int COMP_ATTR_CONSTRUCTOR = 54;
	int COMP_TEXT_CONSTRUCTOR = 55;
	int COMP_COMMENT_CONSTRUCTOR = 56;
	int COMP_PI_CONSTRUCTOR = 57;
	int COMP_NS_CONSTRUCTOR = 58;
	int COMP_DOC_CONSTRUCTOR = 59;
	int PRAGMA = 60;
	int GTEQ = 61;
	int SEQUENCE = 62;
	int LITERAL_xpointer = 63;
	int LPAREN = 64;
	int RPAREN = 65;
	int NCNAME = 66;
	int LITERAL_xquery = 67;
	int LITERAL_version = 68;
	int SEMICOLON = 69;
	int LITERAL_module = 70;
	int LITERAL_namespace = 71;
	int EQ = 72;
	int STRING_LITERAL = 73;
	int LITERAL_declare = 74;
	int LITERAL_default = 75;
	// "boundary-space" = 76
	int LITERAL_ordering = 77;
	int LITERAL_construction = 78;
	// "base-uri" = 79
	// "copy-namespaces" = 80
	int LITERAL_option = 81;
	int LITERAL_function = 82;
	int LITERAL_variable = 83;
	int MOD = 84;
	int LITERAL_import = 85;
	int LITERAL_encoding = 86;
	int LITERAL_collation = 87;
	int LITERAL_element = 88;
	int LITERAL_order = 89;
	int LITERAL_empty = 90;
	int LITERAL_greatest = 91;
	int LITERAL_least = 92;
	int LITERAL_preserve = 93;
	int LITERAL_strip = 94;
	int LITERAL_ordered = 95;
	int LITERAL_unordered = 96;
	int COMMA = 97;
	// "no-preserve" = 98
	int LITERAL_inherit = 99;
	// "no-inherit" = 100
	int DOLLAR = 101;
	int LCURLY = 102;
	int RCURLY = 103;
	int COLON = 104;
	int LITERAL_external = 105;
	int LITERAL_schema = 106;
	int BRACED_URI_LITERAL = 107;
	int LITERAL_as = 108;
	int LITERAL_at = 109;
	// "empty-sequence" = 110
	int QUESTION = 111;
	int STAR = 112;
	int PLUS = 113;
	int LITERAL_item = 114;
	int LITERAL_map = 115;
	int LITERAL_array = 116;
	int LITERAL_for = 117;
	int LITERAL_let = 118;
	int LITERAL_try = 119;
	int LITERAL_some = 120;
	int LITERAL_every = 121;
	int LITERAL_if = 122;
	int LITERAL_switch = 123;
	int LITERAL_typeswitch = 124;
	int LITERAL_update = 125;
	int LITERAL_replace = 126;
	int LITERAL_value = 127;
	int LITERAL_insert = 128;
	int LITERAL_delete = 129;
	int LITERAL_rename = 130;
	int LITERAL_with = 131;
	int LITERAL_into = 132;
	int LITERAL_preceding = 133;
	int LITERAL_following = 134;
	int LITERAL_catch = 135;
	int UNION = 136;
	int LITERAL_where = 137;
	int LITERAL_return = 138;
	int LITERAL_in = 139;
	int LITERAL_by = 140;
	int LITERAL_stable = 141;
	int LITERAL_ascending = 142;
	int LITERAL_descending = 143;
	int LITERAL_group = 144;
	int LITERAL_satisfies = 145;
	int LITERAL_case = 146;
	int LITERAL_then = 147;
	int LITERAL_else = 148;
	int LITERAL_or = 149;
	int LITERAL_and = 150;
	int LITERAL_instance = 151;
	int LITERAL_of = 152;
	int LITERAL_treat = 153;
	int LITERAL_castable = 154;
	int LITERAL_cast = 155;
	int BEFORE = 156;
	int AFTER = 157;
	int LITERAL_eq = 158;
	int LITERAL_ne = 159;
	int LITERAL_lt = 160;
	int LITERAL_le = 161;
	int LITERAL_gt = 162;
	int LITERAL_ge = 163;
	int GT = 164;
	int NEQ = 165;
	int LT = 166;
	int LTEQ = 167;
	int LITERAL_is = 168;
	int LITERAL_isnot = 169;
	int ANDEQ = 170;
	int OREQ = 171;
	int CONCAT = 172;
	int LITERAL_to = 173;
	int MINUS = 174;
	int LITERAL_div = 175;
	int LITERAL_idiv = 176;
	int LITERAL_mod = 177;
	int PRAGMA_START = 178;
	int PRAGMA_END = 179;
	int LITERAL_union = 180;
	int LITERAL_intersect = 181;
	int LITERAL_except = 182;
	int SLASH = 183;
	int DSLASH = 184;
	int BANG = 185;
	int LITERAL_text = 186;
	int LITERAL_node = 187;
	int LITERAL_attribute = 188;
	int LITERAL_comment = 189;
	// "processing-instruction" = 190
	// "document-node" = 191
	int LITERAL_document = 192;
	int HASH = 193;
	int SELF = 194;
	int XML_COMMENT = 195;
	int XML_PI = 196;
	int LPPAREN = 197;
	int RPPAREN = 198;
	int AT = 199;
	int PARENT = 200;
	int LITERAL_child = 201;
	int LITERAL_self = 202;
	int LITERAL_descendant = 203;
	// "descendant-or-self" = 204
	// "following-sibling" = 205
	int LITERAL_parent = 206;
	int LITERAL_ancestor = 207;
	// "ancestor-or-self" = 208
	// "preceding-sibling" = 209
	int INTEGER_LITERAL = 210;
	int DOUBLE_LITERAL = 211;
	int DECIMAL_LITERAL = 212;
	// "schema-element" = 213
	int END_TAG_START = 214;
	int QUOT = 215;
	int APOS = 216;
	int QUOT_ATTRIBUTE_CONTENT = 217;
	int ESCAPE_QUOT = 218;
	int APOS_ATTRIBUTE_CONTENT = 219;
	int ESCAPE_APOS = 220;
	int ELEMENT_CONTENT = 221;
	int XML_COMMENT_END = 222;
	int XML_PI_END = 223;
	int XML_CDATA = 224;
	int LITERAL_collection = 225;
	int LITERAL_validate = 226;
	int XML_PI_START = 227;
	int XML_CDATA_START = 228;
	int XML_CDATA_END = 229;
	int LETTER = 230;
	int DIGITS = 231;
	int HEX_DIGITS = 232;
	int NMSTART = 233;
	int NMCHAR = 234;
	int WS = 235;
	int XQDOC_COMMENT = 236;
	int EXPR_COMMENT = 237;
	int PREDEFINED_ENTITY_REF = 238;
	int CHAR_REF = 239;
	int S = 240;
	int NEXT_TOKEN = 241;
	int CHAR = 242;
	int BASECHAR = 243;
	int IDEOGRAPHIC = 244;
	int COMBINING_CHAR = 245;
	int DIGIT = 246;
	int EXTENDER = 247;
}
