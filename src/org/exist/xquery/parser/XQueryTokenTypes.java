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
	import org.exist.dom.DocumentSet;
	import org.exist.dom.DocumentImpl;
	import org.exist.dom.QName;
	import org.exist.security.PermissionDeniedException;
	import org.exist.xquery.*;
	import org.exist.xquery.value.*;
	import org.exist.xquery.functions.fn.*;

public interface XQueryTokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int QNAME = 4;
	int PREDICATE = 5;
	int FLWOR = 6;
	int PARENTHESIZED = 7;
	int ABSOLUTE_SLASH = 8;
	int ABSOLUTE_DSLASH = 9;
	int WILDCARD = 10;
	int PREFIX_WILDCARD = 11;
	int FUNCTION = 12;
	int UNARY_MINUS = 13;
	int UNARY_PLUS = 14;
	int XPOINTER = 15;
	int XPOINTER_ID = 16;
	int VARIABLE_REF = 17;
	int VARIABLE_BINDING = 18;
	int ELEMENT = 19;
	int ATTRIBUTE = 20;
	int ATTRIBUTE_CONTENT = 21;
	int TEXT = 22;
	int VERSION_DECL = 23;
	int NAMESPACE_DECL = 24;
	int DEF_NAMESPACE_DECL = 25;
	int DEF_COLLATION_DECL = 26;
	int DEF_FUNCTION_NS_DECL = 27;
	int GLOBAL_VAR = 28;
	int FUNCTION_DECL = 29;
	int PROLOG = 30;
	int OPTION = 31;
	int ATOMIC_TYPE = 32;
	int MODULE = 33;
	int ORDER_BY = 34;
	int GROUP_BY = 35;
	int POSITIONAL_VAR = 36;
	int CATCH_ERROR_CODE = 37;
	int CATCH_ERROR_DESC = 38;
	int CATCH_ERROR_VAL = 39;
	int MODULE_DECL = 40;
	int MODULE_IMPORT = 41;
	int SCHEMA_IMPORT = 42;
	int ATTRIBUTE_TEST = 43;
	int COMP_ELEM_CONSTRUCTOR = 44;
	int COMP_ATTR_CONSTRUCTOR = 45;
	int COMP_TEXT_CONSTRUCTOR = 46;
	int COMP_COMMENT_CONSTRUCTOR = 47;
	int COMP_PI_CONSTRUCTOR = 48;
	int COMP_NS_CONSTRUCTOR = 49;
	int COMP_DOC_CONSTRUCTOR = 50;
	int PRAGMA = 51;
	int GTEQ = 52;
	int SEQUENCE = 53;
	int LITERAL_xpointer = 54;
	int LPAREN = 55;
	int RPAREN = 56;
	int NCNAME = 57;
	int LITERAL_xquery = 58;
	int LITERAL_version = 59;
	int SEMICOLON = 60;
	int LITERAL_module = 61;
	int LITERAL_namespace = 62;
	int EQ = 63;
	int STRING_LITERAL = 64;
	int LITERAL_declare = 65;
	int LITERAL_default = 66;
	// "boundary-space" = 67
	int LITERAL_ordering = 68;
	int LITERAL_construction = 69;
	// "base-uri" = 70
	// "copy-namespaces" = 71
	int LITERAL_option = 72;
	int LITERAL_function = 73;
	int LITERAL_variable = 74;
	int LITERAL_import = 75;
	int LITERAL_encoding = 76;
	int LITERAL_collation = 77;
	int LITERAL_element = 78;
	int LITERAL_order = 79;
	int LITERAL_empty = 80;
	int LITERAL_greatest = 81;
	int LITERAL_least = 82;
	int LITERAL_preserve = 83;
	int LITERAL_strip = 84;
	int LITERAL_ordered = 85;
	int LITERAL_unordered = 86;
	int COMMA = 87;
	// "no-preserve" = 88
	int LITERAL_inherit = 89;
	// "no-inherit" = 90
	int DOLLAR = 91;
	int LCURLY = 92;
	int RCURLY = 93;
	int COLON = 94;
	int LITERAL_external = 95;
	int LITERAL_schema = 96;
	int LITERAL_as = 97;
	int LITERAL_at = 98;
	// "empty-sequence" = 99
	int QUESTION = 100;
	int STAR = 101;
	int PLUS = 102;
	int LITERAL_item = 103;
	int LITERAL_for = 104;
	int LITERAL_let = 105;
	int LITERAL_try = 106;
	int LITERAL_some = 107;
	int LITERAL_every = 108;
	int LITERAL_if = 109;
	int LITERAL_switch = 110;
	int LITERAL_typeswitch = 111;
	int LITERAL_update = 112;
	int LITERAL_replace = 113;
	int LITERAL_value = 114;
	int LITERAL_insert = 115;
	int LITERAL_delete = 116;
	int LITERAL_rename = 117;
	int LITERAL_with = 118;
	int LITERAL_into = 119;
	int LITERAL_preceding = 120;
	int LITERAL_following = 121;
	int LITERAL_catch = 122;
	int UNION = 123;
	int LITERAL_where = 124;
	int LITERAL_return = 125;
	int LITERAL_in = 126;
	int LITERAL_by = 127;
	int LITERAL_stable = 128;
	int LITERAL_ascending = 129;
	int LITERAL_descending = 130;
	int LITERAL_group = 131;
	int LITERAL_satisfies = 132;
	int LITERAL_case = 133;
	int LITERAL_then = 134;
	int LITERAL_else = 135;
	int LITERAL_or = 136;
	int LITERAL_and = 137;
	int LITERAL_instance = 138;
	int LITERAL_of = 139;
	int LITERAL_treat = 140;
	int LITERAL_castable = 141;
	int LITERAL_cast = 142;
	int BEFORE = 143;
	int AFTER = 144;
	int LITERAL_eq = 145;
	int LITERAL_ne = 146;
	int LITERAL_lt = 147;
	int LITERAL_le = 148;
	int LITERAL_gt = 149;
	int LITERAL_ge = 150;
	int GT = 151;
	int NEQ = 152;
	int LT = 153;
	int LTEQ = 154;
	int LITERAL_is = 155;
	int LITERAL_isnot = 156;
	int ANDEQ = 157;
	int OREQ = 158;
	int CONCAT = 159;
	int LITERAL_to = 160;
	int MINUS = 161;
	int LITERAL_div = 162;
	int LITERAL_idiv = 163;
	int LITERAL_mod = 164;
	int PRAGMA_START = 165;
	int PRAGMA_END = 166;
	int LITERAL_union = 167;
	int LITERAL_intersect = 168;
	int LITERAL_except = 169;
	int SLASH = 170;
	int DSLASH = 171;
	int LITERAL_text = 172;
	int LITERAL_node = 173;
	int LITERAL_attribute = 174;
	int LITERAL_comment = 175;
	// "processing-instruction" = 176
	// "document-node" = 177
	int LITERAL_document = 178;
	int SELF = 179;
	int XML_COMMENT = 180;
	int XML_PI = 181;
	int LPPAREN = 182;
	int RPPAREN = 183;
	int AT = 184;
	int PARENT = 185;
	int LITERAL_child = 186;
	int LITERAL_self = 187;
	int LITERAL_descendant = 188;
	// "descendant-or-self" = 189
	// "following-sibling" = 190
	int LITERAL_parent = 191;
	int LITERAL_ancestor = 192;
	// "ancestor-or-self" = 193
	// "preceding-sibling" = 194
	int DOUBLE_LITERAL = 195;
	int DECIMAL_LITERAL = 196;
	int INTEGER_LITERAL = 197;
	// "schema-element" = 198
	int END_TAG_START = 199;
	int QUOT = 200;
	int APOS = 201;
	int QUOT_ATTRIBUTE_CONTENT = 202;
	int ESCAPE_QUOT = 203;
	int APOS_ATTRIBUTE_CONTENT = 204;
	int ESCAPE_APOS = 205;
	int ELEMENT_CONTENT = 206;
	int XML_COMMENT_END = 207;
	int XML_PI_END = 208;
	int XML_CDATA = 209;
	int LITERAL_collection = 210;
	int LITERAL_validate = 211;
	int XML_PI_START = 212;
	int XML_CDATA_START = 213;
	int XML_CDATA_END = 214;
	int LETTER = 215;
	int DIGITS = 216;
	int HEX_DIGITS = 217;
	int NMSTART = 218;
	int NMCHAR = 219;
	int WS = 220;
	int EXPR_COMMENT = 221;
	int PREDEFINED_ENTITY_REF = 222;
	int CHAR_REF = 223;
	int S = 224;
	int NEXT_TOKEN = 225;
	int CHAR = 226;
	int BASECHAR = 227;
	int IDEOGRAPHIC = 228;
	int COMBINING_CHAR = 229;
	int DIGIT = 230;
	int EXTENDER = 231;
}
